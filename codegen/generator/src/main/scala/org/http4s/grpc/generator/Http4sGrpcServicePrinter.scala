/*
 * Copyright (c) 2018 Gary Coady / Fs2 Grpc Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.http4s.grpc.generator

import com.google.protobuf.Descriptors.{MethodDescriptor, ServiceDescriptor}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, StreamType}

class Http4sGrpcServicePrinter(service: ServiceDescriptor, serviceSuffix: String, di: DescriptorImplicits) {
  import di._
  import Http4sGrpcServicePrinter.constants._

  private[this] val serviceName: String = service.name
  private[this] val serviceNameHttp4s: String = s"$serviceName$serviceSuffix"
  private[this] val servicePkgName: String = service.getFile.scalaPackage.fullName

  private[this] def serviceMethodSignature(method: MethodDescriptor) = {

    val scalaInType = method.inputType.scalaType
    val scalaOutType = method.outputType.scalaType
    val ctx = s"ctx: $Ctx"

    s"def ${method.name}" + (method.streamType match {
      case StreamType.Unary => s"(request: $scalaInType, $ctx): F[$scalaOutType]"
      case StreamType.ClientStreaming => s"(request: $Stream[F, $scalaInType], $ctx): F[$scalaOutType]"
      case StreamType.ServerStreaming => s"(request: $scalaInType, $ctx): $Stream[F, $scalaOutType]"
      case StreamType.Bidirectional => s"(request: $Stream[F, $scalaInType], $ctx): $Stream[F, $scalaOutType]"
    })
  }

  private[this] def handleMethod(method: MethodDescriptor) = {
    method.streamType match {
      case StreamType.Unary => "unaryToUnary"
      case StreamType.ClientStreaming => "streamToUnary"
      case StreamType.ServerStreaming => "unaryToStream"
      case StreamType.Bidirectional => "streamToStream"
    }
  }

  private[this] def createClientCall(method: MethodDescriptor) = {
    val encode = s"$Codec.codecForGenerated(${method.inputType.scalaType})"
    val decode = s"$Codec.codecForGenerated(${method.outputType.scalaType})"
    val serviceName = method.getService.getFullName
    val methodName = method.name
    s"""$ClientGrpc.${handleMethod(method)}($encode, $decode, "$serviceName", "$methodName")(client, baseUri)(request, ctx)"""
  }

  private[this] def serviceMethodImplementation(method: MethodDescriptor): PrinterEndo = { p =>
    p.add(serviceMethodSignature(method) + " = {")
      .indent
      .add(s"${createClientCall(method)}")
      .outdent
      .add("}")
  }

  private[this] def serviceBindingImplementation(method: MethodDescriptor): PrinterEndo = { p =>
    val serviceCall = s"serviceImpl.${method.name}"
    val eval = if (method.isServerStreaming) s"$Stream.eval(mkCtx(m))" else "mkCtx(m)"

    val decode = s"$Codec.codecForGenerated(${method.inputType.scalaType})"
    val encode = s"$Codec.codecForGenerated(${method.outputType.scalaType})"
    val serviceName = method.getService.getFullName
    val methodName = method.name

    p.add(s""".combineK($ServerGrpc.${handleMethod(method)}($decode, $encode, "$serviceName", "$methodName")(serviceImpl.$methodName(_, _)))""")
  }

  private[this] def serviceMethods: PrinterEndo = _.seq(service.methods.map(serviceMethodSignature))

  private[this] def serviceMethodImplementations: PrinterEndo =
    _.call(service.methods.map(serviceMethodImplementation): _*)

  private[this] def serviceBindingImplementations: PrinterEndo =
    _.add(s"$HttpRoutes.empty[F]")
      .indent
      .call(service.methods.map(serviceBindingImplementation): _*)
      .outdent

  private[this] def serviceTrait: PrinterEndo =
    _.add(s"trait $serviceNameHttp4s[F[_]] {").indent.call(serviceMethods).outdent.add("}")

  private[this] def serviceObject: PrinterEndo =
    _.add(s"object $serviceNameHttp4s {").indent.newline
      .call(serviceClient)
      .newline
      .call(serviceBinding)
      .outdent
      .newline
      .add("}")

  private[this] def serviceClient: PrinterEndo = {
    _.add(
      s"def fromClient[F[_]: $Concurrent](client: $Client[F], baseUri: $Uri): $serviceNameHttp4s[F] = new $serviceNameHttp4s[F] {"
    ).indent
      .call(serviceMethodImplementations)
      .outdent
      .add("}")
  }

  private[this] def serviceBinding: PrinterEndo = {
    _.add(
      s"def toRoutes[F[_]: $Concurrent](serviceImpl: $serviceNameHttp4s[F]): $HttpRoutes[F] = {"
    ).indent
      .call(serviceBindingImplementations)
      .outdent
      .add("}")
  }

  // /

  def printService(printer: FunctionalPrinter): FunctionalPrinter = {
    printer
      .add(s"package $servicePkgName", "", "import _root_.cats.syntax.all._", "")
      .call(serviceTrait)
      .newline
      .call(serviceObject)
  }
}

object Http4sGrpcServicePrinter {

  private[generator] object constants {

    private val effPkg = "_root_.cats.effect.kernel"
    private val fs2Pkg = "_root_.fs2"
    private val http4sPkg = "_root_.org.http4s"
    private val http4sClientPkg = "_root_.org.http4s.client"
    private val http4sGrpcPkg = s"$http4sPkg.grpc"

    // /

    val Ctx = s"$http4sPkg.Headers"

    val Concurrent = s"$effPkg.Concurrent"
    val Client = s"$http4sClientPkg.Client"
    val Uri = s"$http4sPkg.Uri"
    val Stream = s"$fs2Pkg.Stream"

    val ClientGrpc = s"$http4sGrpcPkg.ClientGrpc"
    val ServerGrpc = s"$http4sGrpcPkg.ServerGrpc"
    val HttpRoutes = s"$http4sPkg.HttpRoutes"

    val Codec = s"$http4sGrpcPkg.codecs.ScalaPb"

  }

}
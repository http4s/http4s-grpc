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

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.MethodDescriptor
import com.google.protobuf.Descriptors.ServiceDescriptor
import scalapb.compiler.DescriptorImplicits
import scalapb.compiler.FunctionalPrinter
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.ProtobufGenerator.asScalaDocBlock
import scalapb.compiler.StreamType

class Http4sGrpcServicePrinter(service: ServiceDescriptor, di: DescriptorImplicits) {
  import di._
  import Http4sGrpcServicePrinter.constants._

  private[this] val serviceName: String = service.name
  private[this] val servicePkgName: String =
    service.getFile.scalaPackage.fullName.stripPrefix("_root_.")

  private[this] def rooted(name: ScalaName): String =
    if (name.emptyPackage) name.fullName else s"_root_.${name.fullName.stripPrefix("_root_.")}"

  private[this] val serviceType: String =
    rooted(service.getFile.scalaPackage / service.getName)

  private[this] def scalaType(
      message: Descriptor,
      t: ExtendedMethodDescriptor#MethodTypeWrapper,
  ): String =
    t.customScalaType.getOrElse(rooted(message.scalaType))

  private[this] def codec(
      message: Descriptor,
      t: ExtendedMethodDescriptor#MethodTypeWrapper,
  ): String = {
    val baseType = rooted(message.scalaType)
    t.customScalaType match {
      case Some(customType) => s"$Codec.codecForTypeMapped[$baseType, $customType]($baseType)"
      case None => s"$Codec.codecForGenerated($baseType)"
    }
  }

  private[this] def generateScalaDoc(method: MethodDescriptor): PrinterEndo = { fp =>
    val lines = asScalaDocBlock(method.comment.map(_.split('\n').toSeq).getOrElse(Seq.empty))
    fp.add(lines: _*)
  }

  private[this] def serviceMethodSignature(method: MethodDescriptor) = {

    val scalaInType = scalaType(method.getInputType, method.inputType)
    val scalaOutType = scalaType(method.getOutputType, method.outputType)
    val ctx = s"ctx: $Ctx"

    s"def ${method.name}" + (method.streamType match {
      case StreamType.Unary => s"(request: $scalaInType, $ctx): F[$scalaOutType]"
      case StreamType.ClientStreaming =>
        s"(request: $Stream[F, $scalaInType], $ctx): F[$scalaOutType]"
      case StreamType.ServerStreaming => s"(request: $scalaInType, $ctx): $Stream[F, $scalaOutType]"
      case StreamType.Bidirectional =>
        s"(request: $Stream[F, $scalaInType], $ctx): $Stream[F, $scalaOutType]"
    })
  }

  private[this] def handleMethod(method: MethodDescriptor) =
    method.streamType match {
      case StreamType.Unary => "unaryToUnary"
      case StreamType.ClientStreaming => "streamToUnary"
      case StreamType.ServerStreaming => "unaryToStream"
      case StreamType.Bidirectional => "streamToStream"
    }

  private[this] def createClientCall(method: MethodDescriptor) = {
    val encode = codec(method.getInputType, method.inputType)
    val decode = codec(method.getOutputType, method.outputType)
    val serviceName = method.getService.getFullName
    val methodName = method.getName
    s"""$ClientGrpc.${handleMethod(
        method
      )}($encode, $decode, "$serviceName", "$methodName", maxMessageSize)(client, baseUri)(request, ctx)"""
  }

  private[this] def serviceMethodImplementation(method: MethodDescriptor): PrinterEndo = { p =>
    p.add(serviceMethodSignature(method) + " = {")
      .indent
      .add(s"${createClientCall(method)}")
      .outdent
      .add("}")
  }

  private[this] def serviceBindingImplementation(method: MethodDescriptor): PrinterEndo = { p =>
    // val serviceCall = s"serviceImpl.${method.name}"
    // val eval = if (method.isServerStreaming) s"$Stream.eval(mkCtx(m))" else "mkCtx(m)"

    val decode = codec(method.getInputType, method.inputType)
    val encode = codec(method.getOutputType, method.outputType)
    val serviceName = method.getService.getFullName
    val methodName = method.getName

    p.add(s""".combineK($ServerGrpc.${handleMethod(
        method
      )}($decode, $encode, "$serviceName", "$methodName", maxMessageSize)(serviceImpl.${method.name}(_, _)))""")
  }

  private[this] def serviceMethods: PrinterEndo = _.call(service.methods.map { method =>
    generateScalaDoc(method).andThen(_.add(serviceMethodSignature(method)).newline)
  }: _*)

  private[this] def serviceMethodImplementations: PrinterEndo =
    _.call(service.methods.map(serviceMethodImplementation): _*)

  private[this] def serviceBindingImplementations: PrinterEndo =
    _.add(s"$ServerGrpc.precondition[F]").indent
      .call(service.methods.map(serviceBindingImplementation): _*)
      .add(s""".combineK($ServerGrpc.methodNotFoundRoute("${service.getFullName()}"))""")
      .outdent

  private[this] def generateScalaDoc(method: ServiceDescriptor): PrinterEndo = { fp =>
    val lines = asScalaDocBlock(method.comment.map(_.split('\n').toSeq).getOrElse(Seq.empty))
    fp.add(lines: _*)
  }

  private[this] def serviceTrait: PrinterEndo =
    _.call(generateScalaDoc(service))
      .add(s"trait $serviceName[F[_]] {")
      .newline
      .indent
      .call(serviceMethods)
      .outdent
      .add("}")

  private[this] def serviceObject: PrinterEndo =
    _.add(s"object $serviceName {").indent.newline
      .call(serviceClient)
      .newline
      .call(serviceBinding)
      .outdent
      .newline
      .add("}")

  private[this] def serviceClient: PrinterEndo =
    _.add(
      s"def fromClient[F[_]: $Concurrent](client: $Client[F], baseUri: $Uri): $serviceName[F] = fromClient(client, baseUri, $DefaultMaxMessageSize)"
    ).newline
      .add(
        s"def fromClient[F[_]: $Concurrent](client: $Client[F], baseUri: $Uri, maxMessageSize: Int): $serviceName[F] = new $serviceType[F] {"
      )
      .indent
      .call(serviceMethodImplementations)
      .outdent
      .add("}")

  private[this] def serviceBinding: PrinterEndo =
    _.add(
      s"def toRoutes[F[_]: $Temporal](serviceImpl: $serviceType[F]): $HttpRoutes[F] = toRoutes(serviceImpl, $DefaultMaxMessageSize)"
    ).newline
      .add(
        s"def toRoutes[F[_]: $Temporal](serviceImpl: $serviceType[F], maxMessageSize: Int): $HttpRoutes[F] = {"
      )
      .indent
      .call(serviceBindingImplementations)
      .outdent
      .add("}")

  // /

  def printService(printer: FunctionalPrinter): FunctionalPrinter =
    printer
      .when(servicePkgName.nonEmpty)(_.add(s"package $servicePkgName", ""))
      .add("import _root_.cats.syntax.all._", "")
      .call(serviceTrait)
      .newline
      .call(serviceObject)
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
    val Temporal = s"$effPkg.Temporal"
    val Client = s"$http4sClientPkg.Client"
    val Uri = s"$http4sPkg.Uri"
    val Stream = s"$fs2Pkg.Stream"

    val ClientGrpc = s"$http4sGrpcPkg.ClientGrpc"
    val ServerGrpc = s"$http4sGrpcPkg.ServerGrpc"
    val HttpRoutes = s"$http4sPkg.HttpRoutes"

    val Codec = s"$http4sGrpcPkg.codecs.ScalaPb"
    val DefaultMaxMessageSize = s"$http4sGrpcPkg.codecs.Messages.DefaultMaxMessageSize"

  }

}

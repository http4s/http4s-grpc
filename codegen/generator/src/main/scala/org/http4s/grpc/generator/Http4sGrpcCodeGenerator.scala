/*
 * Copyright (c) 2018 Gary Coady / Http4sGrpc Grpc Developers
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

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, GeneratorParams}
import scalapb.options.Scalapb
import scala.jdk.CollectionConverters._

final case class Http4sGrpcParams(serviceSuffix: String = "Http4s")

object Http4sGrpcCodeGenerator extends CodeGenApp {

  def generateServiceFiles(
      file: FileDescriptor,
      Http4sGrpcparams: Http4sGrpcParams,
      di: DescriptorImplicits
  ): Seq[PluginProtos.CodeGeneratorResponse.File] = {
    file.getServices.asScala.map { service =>
      val p = new Http4sGrpcServicePrinter(service, Http4sGrpcparams.serviceSuffix, di)

      import di.{ExtendedServiceDescriptor, ExtendedFileDescriptor}
      val code = p.printService(FunctionalPrinter()).result()
      val b = PluginProtos.CodeGeneratorResponse.File.newBuilder()
      b.setName(file.scalaDirectory + "/" + service.name + s"${Http4sGrpcparams.serviceSuffix}.scala")
      b.setContent(code)
      b.build
    }.toSeq
  }

  private def parseParameters(params: String): Either[String, (GeneratorParams, Http4sGrpcParams)] =
    for {
      paramsAndUnparsed <- GeneratorParams.fromStringCollectUnrecognized(params)
      params = paramsAndUnparsed._1
      unparsed = paramsAndUnparsed._2
      suffix <- unparsed.map(_.split("=", 2).toList).foldLeft[Either[String, Http4sGrpcParams]](Right(Http4sGrpcParams())) {
        case (Right(params), ServiceSuffix :: suffix :: Nil) => Right(params.copy(serviceSuffix = suffix))
        case (Right(_), xs) => Left(s"Unrecognized parameter: $xs")
        case (Left(e), _) => Left(e)
      }
    } yield (params, suffix)

  def process(request: CodeGenRequest): CodeGenResponse = {
    parseParameters(request.parameter) match {
      case Right((params, http4sGrpcParams)) =>
        val implicits = DescriptorImplicits.fromCodeGenRequest(params, request)
        val srvFiles = request.filesToGenerate.flatMap(generateServiceFiles(_, http4sGrpcParams, implicits))
        CodeGenResponse.succeed(
          srvFiles,
          Set(PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL)
        )
      case Left(error) =>
        CodeGenResponse.fail(error)
    }
  }

  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    Scalapb.registerAllExtensions(registry)
  }

  private[generator] val ServiceSuffix: String = "serviceSuffix"
}

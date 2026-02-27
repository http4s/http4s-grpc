/*
 * Copyright (c) 2023 Christopher Davenport
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

package org.http4s.grpc

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import scalapb.GeneratedMessage
import scodec.bits.ByteVector

import scala.util.control.NonFatal

sealed abstract class GrpcStatusDetails extends Product with Serializable {
  def code: GrpcStatusCode

  def withCode(code: GrpcStatusCode): GrpcStatusDetails

  def message: String

  def withMessage(message: String): GrpcStatusDetails

  def withoutMessage: GrpcStatusDetails

  def details: List[PbAny]

  def addDetails[A <: GeneratedMessage](details: A): GrpcStatusDetails

  def addAllDetails(details: PbAny*): GrpcStatusDetails

  def withDetails(details: List[PbAny]): GrpcStatusDetails

  def withoutDetails: GrpcStatusDetails

  private[grpc] lazy val serializedSize: Int = {
    var size: Int = 0

    if (code != GrpcStatusCode.Ok)
      size += CodedOutputStream.computeInt32Size(1, code.value)

    if (message.nonEmpty)
      size += CodedOutputStream.computeStringSize(2, message)

    details.foreach { details =>
      size += 1
      size += CodedOutputStream.computeUInt32SizeNoTag(details.serializedSize)
      size += details.serializedSize
    }

    size
  }

  private[grpc] def toByteVector: ByteVector = {
    val array = new Array[Byte](serializedSize)
    val output = CodedOutputStream.newInstance(array)

    if (code != GrpcStatusCode.Ok)
      output.writeInt32(1, code.value)

    if (message.nonEmpty)
      output.writeString(2, message)

    details.foreach { details =>
      output.writeTag(3, 2)
      output.writeUInt32NoTag(details.serializedSize)
      details.writeTo(output)
    }

    output.checkNoSpaceLeft()
    ByteVector.view(array)
  }
}

object GrpcStatusDetails {
  def apply(
      code: GrpcStatusCode
  ): GrpcStatusDetails =
    apply(code, message = "")

  def apply(
      code: GrpcStatusCode,
      message: String,
  ): GrpcStatusDetails =
    apply(code, message, details = List.empty)

  def apply(
      code: GrpcStatusCode,
      message: String,
      details: List[PbAny],
  ): GrpcStatusDetails =
    GrpcStatusDetailsImpl(code, message, details)

  private[grpc] def fromByteVector(bytes: ByteVector): Option[GrpcStatusDetails] =
    try {
      val input = CodedInputStream.newInstance(bytes.toArrayUnsafe)
      var code: Option[GrpcStatusCode] = Some(GrpcStatusCode.Ok)
      var message = ""
      val details = List.newBuilder[PbAny]
      var done = false

      while (!done)
        input.readTag() match {
          case 0 => done = true
          case 8 => code = GrpcStatusCode.fromValue(input.readInt32())
          case 18 => message = input.readStringRequireUtf8()
          case 26 => details += PbAny.readFrom(input)
          case _ => () // ignore unknown fields
        }

      code.map(GrpcStatusDetails(_, message, details.result()))
    } catch { case e if NonFatal(e) => None }

  private final case class GrpcStatusDetailsImpl(
      override val code: GrpcStatusCode,
      override val message: String,
      override val details: List[PbAny],
  ) extends GrpcStatusDetails {
    override def withCode(code: GrpcStatusCode): GrpcStatusDetails =
      copy(code = code)

    override def withMessage(message: String): GrpcStatusDetails =
      copy(message = message)

    override def withoutMessage: GrpcStatusDetails =
      withMessage("")

    override def addDetails[A <: GeneratedMessage](details: A): GrpcStatusDetails =
      addAllDetails(PbAny.pack(details))

    override def addAllDetails(details: PbAny*): GrpcStatusDetails =
      if (details.isEmpty) this
      else withDetails(this.details ++ details)

    override def withDetails(details: List[PbAny]): GrpcStatusDetails =
      copy(details = details)

    override def withoutDetails: GrpcStatusDetails =
      withDetails(List.empty)

    override def toString: String =
      s"GrpcStatusDetails($code, $message, $details)"
  }
}

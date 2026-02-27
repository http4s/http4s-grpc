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

import cats.syntax.all._
import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

sealed abstract class PbAny extends Product with Serializable {
  def typeUrl: String

  def withTypeUrl(typeUrl: String): PbAny

  def value: ByteString

  def withValue(value: ByteString): PbAny

  def typeName: String =
    typeUrl.split('/').lastOption.getOrElse(typeUrl)

  def is[A <: GeneratedMessage](implicit companion: GeneratedMessageCompanion[A]): Boolean =
    typeName == companion.scalaDescriptor.fullName

  def unpack[A <: GeneratedMessage](implicit
      companion: GeneratedMessageCompanion[A]
  ): Either[Throwable, A] = {
    val typeName = this.typeName
    val fullName = companion.scalaDescriptor.fullName
    if (typeName == fullName)
      Either.catchNonFatal(companion.parseFrom(value.newCodedInput()))
    else
      Left(
        new IllegalArgumentException(
          s"Type ${typeName} of PbAny does not match expected type $fullName"
        )
      )
  }

  private[grpc] lazy val serializedSize: Int = {
    var size: Int = 0

    if (typeUrl.nonEmpty)
      size += CodedOutputStream.computeStringSize(1, typeUrl)

    if (!value.isEmpty)
      size += CodedOutputStream.computeBytesSize(2, value)

    size
  }

  private[grpc] def writeTo(output: CodedOutputStream): Unit = {
    if (typeUrl.nonEmpty)
      output.writeString(1, typeUrl)

    if (!value.isEmpty)
      output.writeBytes(2, value)
  }
}

object PbAny {
  def apply(typeUrl: String, value: ByteString): PbAny =
    PbAnyImpl(typeUrl, value)

  def pack[A <: GeneratedMessage](generatedMessage: A): PbAny =
    pack(generatedMessage, "type.googleapis.com/")

  def pack[A <: GeneratedMessage](generatedMessage: A, urlPrefix: String): PbAny = {
    val fullName = generatedMessage.companion.scalaDescriptor.fullName

    PbAny(
      typeUrl =
        if (urlPrefix.endsWith("/")) urlPrefix + fullName
        else urlPrefix + "/" + fullName,
      value = generatedMessage.toByteString,
    )
  }

  private def parseFrom(input: CodedInputStream): PbAny = {
    var typeUrl = ""
    var value = ByteString.EMPTY
    var done = false

    while (!done)
      input.readTag() match {
        case 0 => done = true
        case 10 => typeUrl = input.readStringRequireUtf8()
        case 18 => value = input.readBytes()
        case _ => () // ignore unknown fields
      }

    PbAny(typeUrl, value)
  }

  private[grpc] def readFrom(input: CodedInputStream): PbAny = {
    val length = input.readRawVarint32()
    val oldLimit = input.pushLimit(length)
    val result = parseFrom(input)
    input.checkLastTagWas(0)
    input.popLimit(oldLimit)
    result
  }

  private final case class PbAnyImpl(
      override val typeUrl: String,
      override val value: ByteString,
  ) extends PbAny {
    override def withTypeUrl(typeUrl: String): PbAny =
      copy(typeUrl = typeUrl)

    override def withValue(value: ByteString): PbAny =
      copy(value = value)

    override def toString: String =
      s"PbAny($typeUrl, $value)"
  }
}

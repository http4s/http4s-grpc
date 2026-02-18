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

package org.http4s.grpc.codecs

import com.google.protobuf.ByteString
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion
import scalapb.TypeMapper
import scodec.Attempt
import scodec.Codec
import scodec.DecodeResult
import scodec.Decoder
import scodec.Encoder
import scodec.bits.BitVector
import scodec.bits.ByteVector

// Should this be its own subproject?
object ScalaPb {

  private def encoderForGenerated[A <: GeneratedMessage](
      companion: GeneratedMessageCompanion[A]
  ): Encoder[A] =
    Encoder[A]((a: A) => Attempt.successful(ByteVector.view(companion.toByteArray(a)).bits))

  private def decoderForGenerated[A <: GeneratedMessage](
      companion: GeneratedMessageCompanion[A]
  ): Decoder[A] =
    Decoder[A]((b: BitVector) =>
      Attempt
        .fromTry(companion.validate(b.bytes.toArrayUnsafe))
        .map(a => DecodeResult(a, BitVector.empty))
    )

  def codecForGenerated[A <: GeneratedMessage](companion: GeneratedMessageCompanion[A]): Codec[A] =
    Codec[A](encoderForGenerated(companion), decoderForGenerated(companion))

  implicit def byteVectorTypeMapper: TypeMapper[ByteString, ByteVector] =
    new TypeMapper[ByteString, ByteVector] {
      def toCustom(bs: ByteString) = ByteVector.view(bs.toByteArray())
      def toBase(bv: ByteVector) = ByteString.copyFrom(bv.toArrayUnsafe)
    }
}

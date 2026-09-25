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

import cats._
import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s.grpc.GrpcStatus
import org.http4s.grpc.GrpcStatusException
import scodec.Attempt

object Messages {

  /** Default max size of a received message, 4 MiB, same as other gRPC implementations. */
  val DefaultMaxMessageSize: Int = 4 * 1024 * 1024

  def decode[F[_]: MonadThrow, A](d: scodec.Decoder[A])(s: Stream[F, Byte]): Stream[F, A] =
    decode(d, DefaultMaxMessageSize)(s)

  /** Fails with ResourceExhausted, after discarding the rest of the stream, if a message is
    * larger than maxMessageSize bytes.
    */
  def decode[F[_]: MonadThrow, A](d: scodec.Decoder[A], maxMessageSize: Int)(
      s: Stream[F, Byte]
  ): Stream[F, A] = {
    require(maxMessageSize >= 0, s"maxMessageSize must be non-negative: $maxMessageSize")
    decodeLPMStream(maxMessageSize)(s)
      .through(decodeLPMThroughDecoder(d))
  }

  def decodeSingle[F[_]: Concurrent, A](d: scodec.Decoder[A])(s: Stream[F, Byte]): F[A] =
    decodeSingle(d, DefaultMaxMessageSize)(s)

  def decodeSingle[F[_]: Concurrent, A](d: scodec.Decoder[A], maxMessageSize: Int)(
      s: Stream[F, Byte]
  ): F[A] =
    decode(d, maxMessageSize)(s)
      .take(1)
      .compile
      .lastOrError

  private def decodeLPMThroughDecoder[F[_]: MonadThrow, A](d: scodec.Decoder[A])(
      s: Stream[F, LengthPrefixedMessage]
  ): Stream[F, A] =
    s.evalMap(lpm => liftAttempt(d.decodeValue(lpm.message.bits)))

  private def decodeLPMStream[F[_]: RaiseThrowable](maxMessageSize: Int)(
      s: Stream[F, Byte]
  ): Stream[F, LengthPrefixedMessage] = {
    def go(s: Stream[F, Byte]): Pull[F, LengthPrefixedMessage, Unit] =
      s.pull.unconsN(5, allowFewer = true).flatMap {
        case Some((prefix, rest)) if prefix.size == 5 =>
          val bytes = prefix.toByteVector
          val size = bytes.drop(1).toLong(signed = false)
          if (size > maxMessageSize)
            rest.drain.pull.echo >> Pull.raiseError[F](
              GrpcStatusException(
                GrpcStatus.ResourceExhausted.withMessage(
                  s"gRPC message exceeds maximum size $maxMessageSize: $size"
                )
              )
            )
          else
            rest.pull.unconsN(size.toInt, allowFewer = true).flatMap {
              case Some((message, rest)) if message.size.toLong == size =>
                Pull.output1(LengthPrefixedMessage(bytes.head == 1, message.toByteVector)) >>
                  go(rest)
              case _ => Pull.done
            }
        case _ => Pull.done
      }

    go(s).stream
  }

  def encode[F[_]: MonadThrow, A](e: scodec.Encoder[A])(s: Stream[F, A]): Stream[F, Byte] =
    s.through(encodeLPMThroughEncoder[F, A](e))
      .through(encodeLPMStream[F])

  def encodeSingle[F[_]: MonadThrow, A](e: scodec.Encoder[A])(a: A): Stream[F, Byte] =
    encode(e)(Stream(a).covary[F])

  private def encodeLPMThroughEncoder[F[_]: MonadThrow, A](
      e: scodec.Encoder[A]
  )(s: Stream[F, A]): Stream[F, LengthPrefixedMessage] =
    s
      .evalMap(a => liftAttempt(e.encode(a)))
      .map(b => LengthPrefixedMessage(compressed = false, b.bytes))

  private def encodeLPMStream[F[_]: RaiseThrowable](
      s: Stream[F, LengthPrefixedMessage]
  ): Stream[F, Byte] =
    s.through(fs2.interop.scodec.StreamEncoder.many(LengthPrefixedMessage.codec).toPipeByte)

  private def liftAttempt[F[_]: MonadThrow, A](att: Attempt[A]): F[A] =
    att.toEither.leftMap(err => new RuntimeException(err.messageWithContext)).liftTo[F]
}

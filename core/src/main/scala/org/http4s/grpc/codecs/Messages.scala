package org.http4s.grpc.codecs

import cats._
import cats.effect._
import cats.syntax.all._
import fs2._
import scodec.Attempt

object Messages {

  def decode[F[_]: MonadThrow, A](d: scodec.Decoder[A])(s: Stream[F, Byte]): Stream[F, A] =
    decodeLPMStream(s)
      .through(decodeLPMThroughDecoder(d))

  def decodeSingle[F[_]: Concurrent, A](d: scodec.Decoder[A])(s: Stream[F, Byte]): F[A] =
    decode(d)(s)
      .take(1)
      .compile
      .lastOrError

  private def decodeLPMThroughDecoder[F[_]: MonadThrow, A](d: scodec.Decoder[A])(
      s: Stream[F, LengthPrefixedMessage]
  ): Stream[F, A] =
    s.evalMap(lpm => liftAttempt(d.decodeValue(lpm.message.bits)))

  private def decodeLPMStream[F[_]: RaiseThrowable](
      s: Stream[F, Byte]
  ): Stream[F, LengthPrefixedMessage] =
    s.through(fs2.interop.scodec.StreamDecoder.many(LengthPrefixedMessage.codec).toPipeByte)

  def encode[F[_]: MonadThrow, A](e: scodec.Encoder[A])(s: Stream[F, A]): Stream[F, Byte] =
    s.through(encodeLPMThroughEncoder[F, A](e))
      .through(encodeLPMStream[F])

  def encodeToChunk[F[_]: MonadThrow, A](e: scodec.Encoder[A])(a: A): F[Chunk[Byte]] =
    liftAttempt(
      e.encode(a)
        .map(b => LengthPrefixedMessage(compressed = false, b.bytes))
        .flatMap(msg => LengthPrefixedMessage.codec.encode(msg))
        .map(bv => Chunk.byteVector(bv.bytes))
    )

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

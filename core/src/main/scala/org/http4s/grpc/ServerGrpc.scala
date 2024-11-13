package org.http4s.grpc

import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.dsl.request._
import org.http4s.grpc.codecs.NamedHeaders
import org.http4s.headers.Trailer
import org.typelevel.ci._
import scodec.Decoder
import scodec.Encoder

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

object ServerGrpc {

  def unaryToUnary[F[_]: Temporal, A, B]( // Stuff We can provide via codegen\
      decode: Decoder[A],
      encode: Encoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff we apply at invocation
      f: (A, Headers) => F[B]
  ): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Deferred[F, (Int, Option[String])]
        trailers = status.get.map { case (i, message) =>
          Headers(
            NamedHeaders.GrpcStatus(i)
          ).put(message.map(NamedHeaders.GrpcMessage(_)))
        }
        timeout = req.headers.get[NamedHeaders.GrpcTimeout]
      } yield {
        val body = Stream
          .eval(codecs.Messages.decodeSingle(decode)(req.body))
          .evalMap(f(_, req.headers))
          .flatMap(codecs.Messages.encodeSingle(encode)(_))
          .through(timeoutStream(_)(timeout.map(_.duration)))
          .onFinalizeCaseWeak {
            case Resource.ExitCase.Errored(_: TimeoutException) => status.complete((4, None)).void
            case Resource.ExitCase.Errored(e) => status.complete((2, e.toString().some)).void
            case Resource.ExitCase.Canceled => status.complete((1, None)).void
            case Resource.ExitCase.Succeeded => status.complete((0, None)).void
          }
          .mask // ensures body closure without rst-stream

        Response[F](Status.Ok, HttpVersion.`HTTP/2`)
          .putHeaders(
            Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
            SharedGrpc.ContentType,
            SharedGrpc.GrpcEncoding,
            SharedGrpc.TE,
          )
          .withBodyStream(body)
          .withTrailerHeaders(trailers)
      }
  }

  def unaryToStream[F[_]: Temporal, A, B]( // Stuff We can provide via codegen\
      decode: Decoder[A],
      encode: Encoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff we apply at invocation
      f: (A, Headers) => Stream[F, B]
  ): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Deferred[F, (Int, Option[String])]
        trailers = status.get.map { case (i, message) =>
          Headers(
            NamedHeaders.GrpcStatus(i)
          ).put(message.map(NamedHeaders.GrpcMessage(_)))
        }
        timeout = req.headers.get[NamedHeaders.GrpcTimeout]
      } yield {
        val body = Stream
          .eval(codecs.Messages.decodeSingle(decode)(req.body))
          .flatMap(f(_, req.headers))
          .through(codecs.Messages.encode(encode))
          .through(timeoutStream(_)(timeout.map(_.duration)))
          .onFinalizeCaseWeak {
            case Resource.ExitCase.Errored(_: TimeoutException) => status.complete((4, None)).void
            case Resource.ExitCase.Errored(e) => status.complete((2, e.toString().some)).void
            case Resource.ExitCase.Canceled => status.complete((1, None)).void
            case Resource.ExitCase.Succeeded => status.complete((0, None)).void
          }
          .mask // ensures body closure without rst-stream
        Response[F](Status.Ok, HttpVersion.`HTTP/2`)
          .putHeaders(
            Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
            SharedGrpc.ContentType,
            SharedGrpc.GrpcEncoding,
            SharedGrpc.TE,
          )
          .withBodyStream(body)
          .withTrailerHeaders(trailers)
      }
  }

  def streamToUnary[F[_]: Temporal, A, B]( // Stuff We can provide via codegen\
      decode: Decoder[A],
      encode: Encoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff we apply at invocation
      f: (Stream[F, A], Headers) => F[B]
  ): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Deferred[F, (Int, Option[String])]
        trailers = status.get.map { case (i, message) =>
          Headers(
            NamedHeaders.GrpcStatus(i)
          ).put(message.map(NamedHeaders.GrpcMessage(_)))
        }
        timeout = req.headers.get[NamedHeaders.GrpcTimeout]

      } yield {
        val body = Stream
          .eval(f(codecs.Messages.decode(decode)(req.body), req.headers))
          .flatMap(codecs.Messages.encodeSingle(encode)(_))
          .through(timeoutStream(_)(timeout.map(_.duration)))
          .onFinalizeCaseWeak {
            case Resource.ExitCase.Errored(_: TimeoutException) => status.complete((4, None)).void
            case Resource.ExitCase.Errored(e) => status.complete((2, e.toString().some)).void
            case Resource.ExitCase.Canceled => status.complete((1, None)).void
            case Resource.ExitCase.Succeeded => status.complete((0, None)).void
          }
          .mask // ensures body closure without rst-stream

        Response[F](Status.Ok, HttpVersion.`HTTP/2`)
          .putHeaders(
            Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
            SharedGrpc.ContentType,
            SharedGrpc.GrpcEncoding,
            SharedGrpc.TE,
          )
          .withBodyStream(body)
          .withTrailerHeaders(trailers)
      }
  }

  def streamToStream[F[_]: Temporal, A, B]( // Stuff We can provide via codegen\
      decode: Decoder[A],
      encode: Encoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff we apply at invocation
      f: (Stream[F, A], Headers) => Stream[F, B]
  ): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Deferred[F, (Int, Option[String])]
        trailers = status.get.map { case (i, message) =>
          Headers(
            NamedHeaders.GrpcStatus(i)
          ).put(message.map(NamedHeaders.GrpcMessage(_)))
        }
        timeout = req.headers.get[NamedHeaders.GrpcTimeout]
      } yield {

        val body = f(codecs.Messages.decode(decode)(req.body), req.headers)
          .through(codecs.Messages.encode(encode))
          .through(timeoutStream(_)(timeout.map(_.duration)))
          .onFinalizeCaseWeak {
            case Resource.ExitCase.Errored(_: TimeoutException) => status.complete((4, None)).void
            case Resource.ExitCase.Errored(e) => status.complete((2, e.toString().some)).void
            case Resource.ExitCase.Canceled => status.complete((1, None)).void
            case Resource.ExitCase.Succeeded => status.complete((0, None)).void
          }
          .mask // ensures body closure without rst-stream

        Response[F](Status.Ok, HttpVersion.`HTTP/2`)
          .putHeaders(
            Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
            SharedGrpc.ContentType,
            SharedGrpc.GrpcEncoding,
            SharedGrpc.TE,
          )
          .withBodyStream(body)
          .withTrailerHeaders(trailers)
      }
  }

  def methodNotFoundRoute[F[_]: Concurrent](serviceName: String) = HttpRoutes.of[F] {
    case POST -> Root / sN / mN if sN === serviceName =>
      Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          SharedGrpc.ContentType,
          SharedGrpc.TE,
          NamedHeaders.GrpcStatus(12),
          "grpc-message" -> s"unknown method $mN for service $sN",
        )
        .pure[F]
  }

  def closeGrpcRoutes[F[_]: Concurrent](req: Request[F]): F[Response[F]] = req match {
    case POST -> Root / sN =>
      Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          SharedGrpc.ContentType,
          SharedGrpc.TE,
          NamedHeaders.GrpcStatus(12),
          "grpc-message" -> s"unknown service $sN",
        )
        .pure[F]
    case other -> Root / _ =>
      Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          SharedGrpc.ContentType,
          SharedGrpc.TE,
          NamedHeaders.GrpcStatus(12),
          "grpc-message" -> s"unknown method $other",
        )
        .pure[F]
    case _ =>
      Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          SharedGrpc.ContentType,
          SharedGrpc.TE,
          NamedHeaders.GrpcStatus(12),
          "grpc-message" -> s"unknown request",
        )
        .pure[F]
  }

  private def timeoutStream[F[_]: Temporal, A](
      s: Stream[F, A]
  )(timeout: Option[FiniteDuration]): Stream[F, A] =
    timeout match {
      case None => s
      case Some(value) => s.timeout(value)
    }

}

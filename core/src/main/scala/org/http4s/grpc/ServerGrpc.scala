package org.http4s.grpc

import cats.syntax.all._
import cats.effect._
import org.http4s._
import scodec.{Encoder, Decoder}
import fs2._
import org.http4s.dsl.request._
import org.http4s.headers.Trailer
import org.typelevel.ci._

object ServerGrpc {

  def unaryToUnary[F[_]: Concurrent, A, B](// Stuff We can provide via codegen\
    decode: Decoder[A],
    encode: Encoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff we apply at invocation
    f: (A,Headers) => F[B]
  ): HttpRoutes[F] = HttpRoutes.of[F]{
    case req@POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Ref.of[F, Int](0)
        trailers = status.get.map(i => 
          Headers(
            "grpc-status" -> i.toString()
          )
        )
        decoded <- codecs.Messages.decodeSingle(decode)(req.body)
        out <- f(decoded, req.headers)
          .onError{ case _ => status.set(2)}

      } yield Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
          SharedGrpc.ContentType,
          SharedGrpc.GrpcEncoding,
          SharedGrpc.TE
        )
        .withBodyStream(codecs.Messages.encodeSingle(encode)(out))
        .withTrailerHeaders(trailers)
  }

  def unaryToStream[F[_]: Concurrent, A, B](// Stuff We can provide via codegen\
    decode: Decoder[A],
    encode: Encoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff we apply at invocation
    f: (A,Headers) => Stream[F,B]
  ): HttpRoutes[F] = HttpRoutes.of[F]{
    case req@POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Ref.of[F, Int](0)
        trailers = status.get.map(i => 
          Headers(
            "grpc-status" -> i.toString()
          )
        )
        decoded <- codecs.Messages.decodeSingle(decode)(req.body)
        body = f(decoded, req.headers)
          .through(codecs.Messages.encode(encode))
          .onError{ case _ => Stream.eval(status.set(2))}
      } yield Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
          SharedGrpc.ContentType,
          SharedGrpc.GrpcEncoding,
          SharedGrpc.TE
        )
        .withBodyStream(body)
        .withTrailerHeaders(trailers)
  }

  def streamToUnary[F[_]: Concurrent, A, B](// Stuff We can provide via codegen\
    decode: Decoder[A],
    encode: Encoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff we apply at invocation
    f: (Stream[F,A],Headers) => F[B]
  ): HttpRoutes[F] = HttpRoutes.of[F]{
    case req@POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Ref.of[F, Int](0)
        trailers = status.get.map(i => 
          Headers(
            "grpc-status" -> i.toString()
          )
        )
        out <- f(codecs.Messages.decode(decode)(req.body), req.headers)
          .onError{ case _ => status.set(2)}

      } yield Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
          SharedGrpc.ContentType,
          SharedGrpc.GrpcEncoding,
          SharedGrpc.TE
        )
        .withBodyStream(codecs.Messages.encodeSingle(encode)(out))
        .withTrailerHeaders(trailers)
  }

  def streamToStream[F[_]: Concurrent, A, B](// Stuff We can provide via codegen\
    decode: Decoder[A],
    encode: Encoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff we apply at invocation
    f: (Stream[F, A],Headers) => Stream[F,B]
  ): HttpRoutes[F] = HttpRoutes.of[F]{
    case req@POST -> Root / sN / mN if sN === serviceName && mN === methodName =>
      for {
        status <- Ref.of[F, Int](0)
        trailers = status.get.map(i => 
          Headers(
            "grpc-status" -> i.toString()
          )
        )
        body = f(codecs.Messages.decode(decode)(req.body), req.headers)
          .through(codecs.Messages.encode(encode))
          .onError{ case _ => Stream.eval(status.set(2))}

      } yield Response[F](Status.Ok, HttpVersion.`HTTP/2`)
        .putHeaders(
          Trailer(cats.data.NonEmptyList.of(CIString("grpc-status"))),
          SharedGrpc.ContentType,
          SharedGrpc.GrpcEncoding,
          SharedGrpc.TE
        )
        .withBodyStream(body)
        .withTrailerHeaders(trailers)
  }

}
package org.http4s.grpc

import cats._
import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.client.Client
import org.http4s.grpc.codecs.NamedHeaders
import org.http4s.h2.H2Keys
import scodec.Decoder
import scodec.Encoder

object ClientGrpc {
  def unaryToUnary[F[_]: Concurrent, A, B]( // Stuff We can provide via codegen
      encode: Encoder[A],
      decode: Decoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff We can apply at application scope
      client: Client[F],
      baseUri: Uri,
  )( // Stuff we apply at invocation
      message: A,
      ctx: Headers,
  ): F[B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(
        SharedGrpc.TE,
        SharedGrpc.GrpcEncoding,
        SharedGrpc.GrpcAcceptEncoding,
        SharedGrpc.ContentType,
      )
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw): _*)
      .withBodyStream(codecs.Messages.encodeSingle(encode)(message))
      .withAttribute(H2Keys.Http2PriorKnowledge, ())

    client
      .run(req)
      .use(resp =>
        handleFailure(resp.headers) >>
          codecs.Messages
            .decodeSingle(decode)(resp.body)
            .handleErrorWith(e =>
              resp.trailerHeaders
                .flatMap(handleFailure[F])
                .attempt
                .flatMap(t => t.as(e).merge.raiseError[F, B])
            ) <*
          resp.trailerHeaders.flatMap(handleFailure[F])
      )
  }

  def unaryToStream[F[_]: Concurrent, A, B]( // Stuff We can provide via codegen
      encode: Encoder[A],
      decode: Decoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff We can apply at application scope
      client: Client[F],
      baseUri: Uri,
  )( // Stuff we apply at invocation
      message: A,
      ctx: Headers,
  ): Stream[F, B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(
        SharedGrpc.TE,
        SharedGrpc.GrpcEncoding,
        SharedGrpc.GrpcAcceptEncoding,
        SharedGrpc.ContentType,
      )
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw): _*)
      .withBodyStream(codecs.Messages.encodeSingle(encode)(message))
      .withAttribute(H2Keys.Http2PriorKnowledge, ())

    Stream
      .resource(client.run(req))
      .flatMap(resp =>
        Stream.eval(handleFailure(resp.headers)).drain ++
          codecs.Messages
            .decode[F, B](decode)(resp.body)
            .handleErrorWith(e =>
              Stream.eval(
                resp.trailerHeaders
                  .flatMap(handleFailure[F])
                  .attempt
                  .flatMap(t => t.as(e).merge.raiseError[F, B])
              )
            ) ++
          Stream.eval(resp.trailerHeaders).evalMap(handleFailure[F]).drain
      )
  }

  def streamToUnary[F[_]: Concurrent, A, B]( // Stuff We can provide via codegen
      encode: Encoder[A],
      decode: Decoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff We can apply at application scope
      client: Client[F],
      baseUri: Uri,
  )( // Stuff we apply at invocation
      message: Stream[F, A],
      ctx: Headers,
  ): F[B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(
        SharedGrpc.TE,
        SharedGrpc.GrpcEncoding,
        SharedGrpc.GrpcAcceptEncoding,
        SharedGrpc.ContentType,
      )
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw): _*)
      .withBodyStream(codecs.Messages.encode(encode)(message).mask)
      .withAttribute(H2Keys.Http2PriorKnowledge, ())

    client
      .run(req)
      .use(resp =>
        handleFailure(resp.headers) >>
          codecs.Messages
            .decodeSingle(decode)(resp.body)
            .handleErrorWith(e =>
              resp.trailerHeaders
                .flatMap(handleFailure[F])
                .attempt
                .flatMap(t => t.as(e).merge.raiseError[F, B])
            ) <*
          resp.trailerHeaders.flatMap(handleFailure[F])
      )
  }

  def streamToStream[F[_]: Concurrent, A, B]( // Stuff We can provide via codegen
      encode: Encoder[A],
      decode: Decoder[B],
      serviceName: String,
      methodName: String,
  )( // Stuff We can apply at application scope
      client: Client[F],
      baseUri: Uri,
  )( // Stuff we apply at invocation
      message: Stream[F, A],
      ctx: Headers,
  ): Stream[F, B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(
        SharedGrpc.TE,
        SharedGrpc.GrpcEncoding,
        SharedGrpc.GrpcAcceptEncoding,
        SharedGrpc.ContentType,
      )
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw): _*)
      .withBodyStream(codecs.Messages.encode(encode)(message).mask)
      .withAttribute(H2Keys.Http2PriorKnowledge, ())

    Stream
      .resource(client.run(req))
      .flatMap(resp =>
        Stream.eval(handleFailure(resp.headers)).drain ++
          codecs.Messages
            .decode[F, B](decode)(resp.body)
            .handleErrorWith(e =>
              Stream.eval(
                resp.trailerHeaders
                  .flatMap(handleFailure[F])
                  .attempt
                  .flatMap(t => t.as(e).merge.raiseError[F, B])
              )
            ) ++
          Stream.eval(resp.trailerHeaders).evalMap(handleFailure[F]).drain
      )
  }

  private def handleFailure[F[_]: MonadThrow](headers: Headers): F[Unit] = {
    val status = headers.get[NamedHeaders.GrpcStatus]
    val reason = headers.get[NamedHeaders.GrpcMessage]

    status match {
      case Some(NamedHeaders.GrpcStatus(0)) => ().pure[F]
      case Some(NamedHeaders.GrpcStatus(status)) =>
        GrpcExceptions.StatusRuntimeException(status, reason.map(_.message)).raiseError[F, Unit]
      case None => ().pure[F]
    }
  }

}

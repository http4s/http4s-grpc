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

import cats._
import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.client.Client
import org.http4s.grpc.GrpcStatus._
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
      case Some(NamedHeaders.GrpcStatus(Ok)) => ().pure[F]
      case Some(NamedHeaders.GrpcStatus(status)) =>
        GrpcExceptions.StatusRuntimeException(status, reason.map(_.message)).raiseError[F, Unit]
      case None => ().pure[F]
    }
  }

}

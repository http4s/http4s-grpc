package org.http4s.grpc

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import scodec.{Encoder, Decoder}
import fs2._

object ClientGrpc {
  def unaryToUnary[F[_]: Concurrent, A, B](// Stuff We can provide via codegen
    encode: Encoder[A],
    decode: Decoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff We can apply at application scope
    client: Client[F], baseUri: Uri
  )( // Stuff we apply at invocation
    message: A, ctx: Headers
  ): F[B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(SharedGrpc.TE, SharedGrpc.GrpcEncoding, SharedGrpc.GrpcAcceptEncoding, SharedGrpc.ContentType)
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw):_*)
      .withBodyStream(codecs.Messages.encodeSingle(encode)(message))

    client.run(req).use( resp => 
      codecs.Messages.decodeSingle(decode)(resp.body)
    )
  }


  def unaryToStream[F[_]: Concurrent, A, B](// Stuff We can provide via codegen
    encode: Encoder[A],
    decode: Decoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff We can apply at application scope
    client: Client[F], baseUri: Uri
  )( // Stuff we apply at invocation
    message: A, ctx: Headers
  ): Stream[F, B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(SharedGrpc.TE, SharedGrpc.GrpcEncoding, SharedGrpc.GrpcAcceptEncoding, SharedGrpc.ContentType)
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw):_*)
      .withBodyStream(codecs.Messages.encodeSingle(encode)(message))

    Stream.resource(client.run(req)).flatMap( resp => 
      codecs.Messages.decode[F, B](decode)(resp.body)
    )
  }

  def streamToUnary[F[_]: Concurrent, A, B](// Stuff We can provide via codegen
    encode: Encoder[A],
    decode: Decoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff We can apply at application scope
    client: Client[F], baseUri: Uri
  )( // Stuff we apply at invocation
    message: Stream[F, A], ctx: Headers
  ): F[B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(SharedGrpc.TE, SharedGrpc.GrpcEncoding, SharedGrpc.GrpcAcceptEncoding, SharedGrpc.ContentType)
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw):_*)
      .withBodyStream(codecs.Messages.encode(encode)(message))

    client.run(req).use( resp => 
      codecs.Messages.decodeSingle(decode)(resp.body)
    )
  }

  def streamToStream[F[_]: Concurrent, A, B](// Stuff We can provide via codegen
    encode: Encoder[A],
    decode: Decoder[B],
    serviceName: String,
    methodName: String,
  )( // Stuff We can apply at application scope
    client: Client[F], baseUri: Uri
  )( // Stuff we apply at invocation
    message: Stream[F, A], ctx: Headers
  ): Stream[F, B] = {
    val req = Request(Method.POST, baseUri / serviceName / methodName, HttpVersion.`HTTP/2`)
      .putHeaders(SharedGrpc.TE, SharedGrpc.GrpcEncoding, SharedGrpc.GrpcAcceptEncoding, SharedGrpc.ContentType)
      .putHeaders(ctx.headers.map(Header.ToRaw.rawToRaw):_*)
      .withBodyStream(codecs.Messages.encode(encode)(message))

    Stream.resource(client.run(req)).flatMap( resp => 
      codecs.Messages.decode[F, B](decode)(resp.body)
    )
  }


}


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

package hello.world

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import munit._
import org.http4s._
import org.http4s.client.Client
import org.http4s.grpc.GrpcStatus._
import org.http4s.grpc.GrpcStatusException
import org.typelevel.ci._
import org.typelevel.vault.Key

class WithContextSuite extends CatsEffectSuite {
  val impl: TestService.WithContext[IO, String] = new TestService.WithContext[IO, String] {
    def noStreaming(request: TestMessage, ctx: String): IO[TestMessage] =
      IO(request.copy(a = ctx))

    def clientStreaming(request: Stream[IO, TestMessage], ctx: String): IO[TestMessage] =
      request.compile.lastOrError.map(_.copy(a = ctx))

    def serverStreaming(request: TestMessage, ctx: String): Stream[IO, TestMessage] =
      Stream.emit(request.copy(a = ctx))

    def bothStreaming(request: Stream[IO, TestMessage], ctx: String): Stream[IO, TestMessage] =
      request.map(_.copy(a = ctx))

    def `export`(request: TestMessage, ctx: String): IO[TestMessage] =
      IO(request.copy(a = ctx))
  }

  val msg: TestMessage = TestMessage("", 1, None)

  private def callAll(client: TestService.WithContext[IO, Headers]): IO[List[String]] =
    List(
      client.noStreaming(msg, Headers.empty),
      client.clientStreaming(Stream.emit(msg), Headers.empty),
      client.serverStreaming(msg, Headers.empty).compile.lastOrError,
      client.bothStreaming(Stream.emit(msg), Headers.empty).compile.lastOrError,
    ).traverse(_.map(_.a))

  test("Server context is derived from request attributes") {
    Key.newKey[IO, String].flatMap { userId =>
      val routes = TestService.toRoutes[IO, String](
        impl,
        req => IO.fromOption(req.attributes.lookup(userId))(new NoSuchElementException("userId")),
      )
      val withAuth = HttpRoutes[IO](req => routes.run(req.withAttribute(userId, "user-1")))
      val client = TestService.fromClient[IO](Client.fromHttpApp(withAuth.orNotFound), Uri())

      callAll(client).assertEquals(List.fill(4)("user-1"))
    }
  }

  test("Server context failure is returned as the grpc status") {
    val routes = TestService.toRoutes[IO, String](
      impl,
      _ => IO.raiseError(Unauthenticated.withMessage("no user").toException),
    )
    val client = TestService.fromClient[IO](Client.fromHttpApp(routes.orNotFound), Uri())

    List(
      client.noStreaming(msg, Headers.empty),
      client.clientStreaming(Stream.emit(msg), Headers.empty),
      client.serverStreaming(msg, Headers.empty).compile.lastOrError,
      client.bothStreaming(Stream.emit(msg), Headers.empty).compile.lastOrError,
    ).traverse(_.attemptNarrow[GrpcStatusException].map(_.leftMap(_.status)))
      .assertEquals(List.fill(4)(Either.left(Unauthenticated.withMessage("no user"))))
  }

  test("Client context is converted to request headers") {
    val routes = TestService.toRoutes[IO, String](
      impl,
      req =>
        IO.fromOption(req.headers.get(ci"x-user-id").map(_.head.value))(
          new NoSuchElementException("x-user-id")
        ),
    )
    val client = TestService.fromClient[IO, String](
      Client.fromHttpApp(routes.orNotFound),
      Uri(),
      (userId: String) => IO.pure(Headers("x-user-id" -> userId)),
    )

    List(
      client.noStreaming(msg, "user-2"),
      client.clientStreaming(Stream.emit(msg), "user-2"),
      client.serverStreaming(msg, "user-2").compile.lastOrError,
      client.bothStreaming(Stream.emit(msg), "user-2").compile.lastOrError,
    ).traverse(_.map(_.a))
      .assertEquals(List.fill(4)("user-2"))
  }

  test("Headers-based impl can be served with a derived context") {
    val headersImpl: TestService[IO] = new TestService[IO] {
      def noStreaming(request: TestMessage, ctx: Headers): IO[TestMessage] =
        IO(request.copy(a = ctx.get(ci"x-user-id").fold("")(_.head.value)))

      def clientStreaming(request: Stream[IO, TestMessage], ctx: Headers): IO[TestMessage] =
        request.compile.lastOrError

      def serverStreaming(request: TestMessage, ctx: Headers): Stream[IO, TestMessage] =
        Stream.emit(request)

      def bothStreaming(request: Stream[IO, TestMessage], ctx: Headers): Stream[IO, TestMessage] =
        request

      def `export`(request: TestMessage, ctx: Headers): IO[TestMessage] = IO(request)
    }
    val routes = TestService.toRoutes[IO, Headers](
      headersImpl,
      req => IO.pure(req.headers.put("x-user-id" -> "user-3")),
    )
    val client = TestService.fromClient[IO](Client.fromHttpApp(routes.orNotFound), Uri())

    client.noStreaming(msg, Headers.empty).map(_.a).assertEquals("user-3")
  }
}

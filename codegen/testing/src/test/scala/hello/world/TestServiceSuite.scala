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

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.concurrent.Channel
import munit._
import org.http4s._
import org.http4s.client.Client
import org.http4s.grpc.GrpcStatus
import org.http4s.grpc.GrpcStatus._
import org.http4s.grpc.GrpcStatusCode
import org.http4s.grpc.GrpcStatusException
import org.http4s.syntax.all._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._
import org.scalacheck.effect.PropF.forAllF

import scala.concurrent.duration._

class TestServiceSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  val impl: TestService[IO] = new TestService[IO] {
    def noStreaming(request: TestMessage, ctx: Headers): IO[TestMessage] =
      IO(request)

    def clientStreaming(request: Stream[IO, TestMessage], ctx: Headers): IO[TestMessage] =
      request.compile.lastOrError

    def serverStreaming(request: TestMessage, ctx: Headers): Stream[IO, TestMessage] =
      Stream.emit(request)

    def bothStreaming(request: Stream[IO, TestMessage], ctx: Headers): Stream[IO, TestMessage] =
      request

    def `export`(request: TestMessage, ctx: Headers): IO[TestMessage] = IO(request)
  }

  implicit val arbitraryTestMessage: Arbitrary[TestMessage] = Arbitrary(
    for {
      a <- arbitrary[String]
      b <- arbitrary[Int]
      c <- Gen.option(
        Gen.oneOf(Color.RED, Color.GREEN, Color.BLUE).map(TestMessage.NestedMessage(_))
      )
    } yield TestMessage(a, b, c)
  )

  val client: TestService[IO] = TestService.fromClient[IO](
    Client.fromHttpApp(TestService.toRoutes(impl).orNotFound),
    Uri(),
  )

  test("no streaming") {
    forAllF { (msg: TestMessage) =>
      client.noStreaming(msg, Headers.empty).assertEquals(msg)
    }
  }

  test("client streaming") {
    forAllF { (msg: TestMessage, tail: List[TestMessage]) =>
      client
        .clientStreaming(Stream.emits(msg :: tail), Headers.empty)
        .assertEquals(tail.lastOption.getOrElse(msg))
    }
  }

  test("server streaming") {
    forAllF { (msg: TestMessage) =>
      client.serverStreaming(msg, Headers.empty).compile.lastOrError.assertEquals(msg)
    }
  }

  test("both streaming") {
    forAllF { (msgs: List[TestMessage]) =>
      client.bothStreaming(Stream.emits(msgs), Headers.empty).compile.to(List).assertEquals(msgs)
    }
  }

  test("Routes returns 405 Method Not Allowed with Allow header on GET requests") {
    val client = Client.fromHttpApp(TestService.toRoutes(impl).orNotFound)
    implicit val methodExceptPost: Arbitrary[Method] =
      Arbitrary(Gen.oneOf(Method.all.filterNot(_ === Method.POST)))
    implicit val wellKnownGRPCHeaders: Arbitrary[Header.ToRaw] = Arbitrary(
      Gen.oneOf(
        "Content-Type" -> "application/grpc",
        "Content-Type" -> "application/grpc+proto",
        "Content-Type" -> "application/grpc+json",
      )
    )
    forAllF { (meth: Method, grpcHeader: Header.ToRaw) =>
      client
        .run(
          Request[IO](meth, uri"/hello.world.TestService/any/url")
            .withHeaders(grpcHeader)
        )
        .use { resp =>
          val headers = resp.headers
          val methods = headers.get[org.http4s.headers.Allow].map(_.methods)
          (resp.status, methods).pure[IO]
        }
        .assertEquals(
          (Status.MethodNotAllowed, Some(Set(Method.POST)))
        )
    }
  }

  test("Routes returns 415 Unsupported Media Type on requests without grpc content type") {
    val client = Client.fromHttpApp(TestService.toRoutes(impl).orNotFound)
    client
      .run(
        Request[IO](Method.POST, uri"/hello.world.TestService/noStreaming")
      )
      .use { resp =>
        val status = resp.status
        status.pure[IO]
      }
      .assertEquals(
        Status.UnsupportedMediaType
      )
  }

  test("Routes returns UNIMPLEMENTED") {
    val client = Client.fromHttpApp(TestService.toRoutes(impl).orNotFound)
    client
      .run(
        Request[IO](org.http4s.Method.POST, uri"/hello.world.TestService/missingMethod")
          .withHeaders("Content-Type" -> "application/grpc")
      )
      .use { resp =>
        val headers = resp.headers
        val status = headers.get[org.http4s.grpc.codecs.NamedHeaders.GrpcStatus]
        status.pure[IO]
      }
      .assertEquals(
        Some(org.http4s.grpc.codecs.NamedHeaders.GrpcStatus(Unimplemented.code))
      )
  }

  test("Client fails with initial failure") {
    forAllF { (msg: TestMessage) =>
      val route = org.http4s.HttpRoutes.of[IO] { case _ =>
        Response(Status.Ok)
          .putHeaders(
            org.http4s.grpc.codecs.NamedHeaders.GrpcStatus(Unimplemented.code)
          )
          .pure[IO]
      }
      val client = TestService.fromClient[IO](
        Client.fromHttpApp(route.orNotFound),
        Uri(),
      )
      client
        .`export`(msg, Headers.empty)
        .attemptNarrow[GrpcStatusException]
        .map(_.leftMap(_.status))
        .assertEquals(Either.left(Unimplemented))
    }
  }

  test("Server Fails in Trailers") {

    forAllF { (msg: TestMessage) =>
      val exception = new RuntimeException("Boo!")

      val ts = new TestService[IO] {
        def noStreaming(request: TestMessage, ctx: Headers): IO[TestMessage] =
          IO(request) <* IO.raiseError(exception)

        def clientStreaming(request: Stream[IO, TestMessage], ctx: Headers): IO[TestMessage] =
          request.compile.lastOrError

        def serverStreaming(request: TestMessage, ctx: Headers): Stream[IO, TestMessage] =
          Stream.emit(request)

        def bothStreaming(request: Stream[IO, TestMessage], ctx: Headers): Stream[IO, TestMessage] =
          request

        def `export`(request: TestMessage, ctx: Headers): IO[TestMessage] = IO(request)
      }
      val client = TestService.fromClient[IO](
        Client.fromHttpApp(TestService.toRoutes[IO](ts).orNotFound),
        Uri(),
      )

      client
        .noStreaming(msg, Headers.empty)
        .attemptNarrow[GrpcStatusException]
        .map(_.leftMap(_.status))
        .assertEquals(Either.left(Unknown.withMessage(exception.toString)))
    }

  }

  test("Server Fails with a Status") {
    implicit val arbitraryStatus: Arbitrary[GrpcStatus] =
      Arbitrary(
        for {
          code <- Gen.oneOf(GrpcStatusCode.values)
          if code != GrpcStatusCode.Ok
          message <- Gen.option(arbitrary[String])
          details <- arbitrary[TestMessage]
          status = GrpcStatus(code, message).addDetails(details)
        } yield status
      )

    forAllF { (msg: TestMessage, status: GrpcStatus) =>
      val ts = new TestService[IO] {
        def noStreaming(request: TestMessage, ctx: Headers): IO[TestMessage] =
          IO(request) <* IO.raiseError(status.toException)

        def clientStreaming(request: Stream[IO, TestMessage], ctx: Headers): IO[TestMessage] =
          request.compile.lastOrError

        def serverStreaming(request: TestMessage, ctx: Headers): Stream[IO, TestMessage] =
          Stream.emit(request)

        def bothStreaming(request: Stream[IO, TestMessage], ctx: Headers): Stream[IO, TestMessage] =
          request

        def `export`(request: TestMessage, ctx: Headers): IO[TestMessage] = IO(request)
      }
      val client = TestService.fromClient[IO](
        Client.fromHttpApp(TestService.toRoutes[IO](ts).orNotFound),
        Uri(),
      )

      client
        .noStreaming(msg, Headers.empty)
        .attemptNarrow[GrpcStatusException]
        .map(_.leftMap(_.status))
        .assertEquals(Either.left(status))
    }
  }

  test("Server rejects messages larger than maxMessageSize") {
    val client = TestService.fromClient[IO](
      Client.fromHttpApp(TestService.toRoutes(impl, 16).orNotFound),
      Uri(),
    )

    client
      .noStreaming(TestMessage("a" * 32, 0, None), Headers.empty)
      .attemptNarrow[GrpcStatusException]
      .map(_.leftMap(_.status.code))
      .assertEquals(Either.left(GrpcStatusCode.ResourceExhausted))
  }

  test("Server rejects oversized messages without buffering them") {
    val client = Client.fromHttpApp(TestService.toRoutes(impl).orNotFound)
    val body = Stream[IO, Byte](0, -1, -1, -1, -1) ++ Stream.constant[IO, Byte](0)

    client
      .run(
        Request[IO](Method.POST, uri"/hello.world.TestService/noStreaming")
          .withHeaders("Content-Type" -> "application/grpc")
          .withBodyStream(body)
      )
      .use(resp => resp.body.compile.drain >> resp.trailerHeaders)
      .map(_.get[org.http4s.grpc.codecs.NamedHeaders.GrpcStatus].map(_.statusCode))
      .assertEquals(Some(GrpcStatusCode.ResourceExhausted))
  }

  test("Client rejects messages larger than maxMessageSize") {
    val client = TestService.fromClient[IO](
      Client.fromHttpApp(TestService.toRoutes(impl).orNotFound),
      Uri(),
      16,
    )

    client
      .noStreaming(TestMessage("a" * 32, 0, None), Headers.empty)
      .attemptNarrow[GrpcStatusException]
      .map(_.leftMap(_.status.code))
      .assertEquals(Either.left(GrpcStatusCode.ResourceExhausted))
  }

  /*
   * Like Client.fromHttpApp, but delivers the response like HTTP/2:
   * the body in 16 KiB frames through a 64 KiB receive window, and
   * the trailers after the last frame.
   */
  private def flowControlledClient(app: HttpApp[IO]): Client[IO] =
    Client { req =>
      for {
        resp <- Resource.eval(app(req))
        window <- Resource.eval(Channel.bounded[IO, Chunk[Byte]](4))
        trailers <- Resource.eval(Deferred[IO, Headers])
        _ <- (resp.body.chunkLimit(16 * 1024).through(window.sendAll).compile.drain >>
          resp.trailerHeaders.flatMap(trailers.complete)).background
      } yield resp.withBodyStream(window.stream.unchunks).withTrailerHeaders(trailers.get)
    }

  test("Client rejects oversized messages when the transport applies flow control") {
    val client =
      TestService.fromClient[IO](
        flowControlledClient(TestService.toRoutes(impl).orNotFound),
        Uri(),
        16 * 1024,
      )

    client
      .noStreaming(TestMessage("a" * (256 * 1024), 0, None), Headers.empty)
      .attemptNarrow[GrpcStatusException]
      .map(_.leftMap(_.status.code))
      .timeout(10.seconds)
      .assertEquals(Either.left(GrpcStatusCode.ResourceExhausted))
  }
}

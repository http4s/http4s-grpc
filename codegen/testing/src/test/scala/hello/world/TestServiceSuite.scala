package hello.world

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import munit._
import org.http4s.Headers
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.grpc.GrpcStatus._
import org.http4s.syntax.all._
import org.scalacheck._
import org.scalacheck.effect.PropF.forAllF

import Arbitrary.arbitrary

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

  implicit val arbitraryStatusCode: Arbitrary[Code] = Arbitrary(
    Gen.oneOf(codeValues.filter(_ != Ok))
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

  test("Routes returns missing method") {
    val client = Client.fromHttpApp(TestService.toRoutes(impl).orNotFound)
    client
      .run(
        org.http4s.Request[IO](org.http4s.Method.POST, uri"/hello.world.TestService/missingMethod")
      )
      .use { resp =>
        val headers = resp.headers
        val status = headers.get[org.http4s.grpc.codecs.NamedHeaders.GrpcStatus]
        status.pure[IO]
      }
      .assertEquals(
        Some(org.http4s.grpc.codecs.NamedHeaders.GrpcStatus(Unimplemented))
      )
  }

  test("Client fails with initial failure") {
    forAllF { (msg: TestMessage) =>
      val route = org.http4s.HttpRoutes.of[IO] { case _ =>
        org.http4s
          .Response(org.http4s.Status.Ok)
          .putHeaders(
            org.http4s.grpc.codecs.NamedHeaders.GrpcStatus(Unimplemented)
          )
          .pure[IO]
      }
      val client = TestService.fromClient[IO](
        Client.fromHttpApp(route.orNotFound),
        Uri(),
      )
      client
        .`export`(msg, Headers.empty)
        .attemptNarrow[org.http4s.grpc.GrpcExceptions.StatusRuntimeException]
        .map(_.leftMap(grpcFailed => grpcFailed.status))
        .assertEquals(Either.left(Unimplemented))
    }
  }

  test("Server Fails in Trailers") {

    forAllF { (msg: TestMessage) =>
      val ts = new TestService[IO] {
        def noStreaming(request: TestMessage, ctx: Headers): IO[TestMessage] =
          IO(request) <* IO.raiseError(new RuntimeException("Boo!"))

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
        .attemptNarrow[org.http4s.grpc.GrpcExceptions.StatusRuntimeException]
        .map(_.leftMap(grpcFailed => grpcFailed.status))
        .assertEquals(Either.left(Unknown))
    }

  }

  test("Server Fails with an Status Code") {

    forAllF { (msg: TestMessage, statusCode: Code) =>
      val ts = new TestService[IO] {
        def noStreaming(request: TestMessage, ctx: Headers): IO[TestMessage] =
          IO(request) <* IO.raiseError(statusCode.asStatusRuntimeException())

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
        .attemptNarrow[org.http4s.grpc.GrpcExceptions.StatusRuntimeException]
        .map(_.leftMap(grpcFailed => grpcFailed.status))
        .assertEquals(Either.left(statusCode))
    }

  }

}

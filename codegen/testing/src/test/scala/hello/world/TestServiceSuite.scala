package hello.world

import cats.effect.IO
import fs2.Stream
import munit._
import org.http4s.client.Client
import org.http4s.Headers
import org.http4s.Uri
import org.http4s.syntax.all._
import org.scalacheck._, Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF

class TestServiceSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  
  val impl = new TestService[IO] {
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
      c <- Gen.option(Gen.oneOf(Color.RED, Color.GREEN, Color.BLUE).map(TestMessage.NestedMessage(_)))
    } yield TestMessage(a, b, c)
  )

  val client = TestService.fromClient[IO](
    Client.fromHttpApp(TestService.toRoutes(impl).orNotFound),
    Uri()
  )

  test("no streaming") {
    forAllF { (msg: TestMessage) =>
      client.noStreaming(msg, Headers.empty).assertEquals(msg)
    }
  }

  test("client streaming") {
    forAllF { (msg: TestMessage, tail: List[TestMessage]) =>
      client.clientStreaming(Stream.emits(msg :: tail), Headers.empty).assertEquals(tail.lastOption.getOrElse(msg))
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

}

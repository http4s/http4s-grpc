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
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import munit._
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.grpc.GrpcStatusCode
import org.http4s.grpc.GrpcStatusException
import org.http4s.syntax.all._

import scala.concurrent.duration._

class EmberSuite extends CatsEffectSuite {
  val MiB: Int = 1024 * 1024

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

  val ember: Resource[IO, (Client[IO], Uri)] =
    for {
      server <- EmberServerBuilder
        .default[IO]
        .withHttp2
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withShutdownTimeout(1.second)
        .withHttpApp(TestService.toRoutes(impl).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].withHttp2.build
    } yield (client, server.baseUri)

  test("Connection keeps working after the server rejects an oversized message") {
    ember.use { case (client, uri) =>
      val service = TestService.fromClient[IO](client, uri, Int.MaxValue)

      service
        .noStreaming(TestMessage("a" * (8 * MiB), 0, None), Headers.empty)
        .attemptNarrow[GrpcStatusException]
        .map(_.leftMap(_.status.code))
        .timeout(10.seconds)
        .assertEquals(Either.left(GrpcStatusCode.ResourceExhausted)) >>
        service
          .noStreaming(TestMessage("a" * MiB, 0, None), Headers.empty)
          .map(_.a.length)
          .timeout(10.seconds)
          .assertEquals(MiB)
    }
  }
}

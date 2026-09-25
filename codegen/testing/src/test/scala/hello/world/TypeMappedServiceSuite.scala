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
import com.google.protobuf.empty.Empty
import fs2.Stream
import munit._
import org.http4s._
import org.http4s.client.Client
import org.scalacheck.effect.PropF.forAllF

class TypeMappedServiceSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  val impl: TypeMappedService[IO] = new TypeMappedService[IO] {
    def count(request: Empty, ctx: Headers): Stream[IO, Long] =
      Stream(1L, 2L, 3L)

    def echo(request: String, ctx: Headers): IO[String] =
      IO(request)

    def echoShape(request: Shape, ctx: Headers): IO[Shape] =
      IO(request)

    def echoTemperature(request: Celsius, ctx: Headers): IO[Celsius] =
      IO(request)
  }

  val client: TypeMappedService[IO] = TypeMappedService.fromClient[IO](
    Client.fromHttpApp(TypeMappedService.toRoutes(impl).orNotFound),
    Uri(),
  )

  test("Int64Value as Long") {
    client.count(Empty(), Headers.empty).compile.toList.assertEquals(List(1L, 2L, 3L))
  }

  test("StringValue as String") {
    forAllF { (s: String) =>
      client.echo(s, Headers.empty).assertEquals(s)
    }
  }

  test("sealed oneof as sealed trait") {
    List[Shape](Circle(1.5), Square(2.5), Shape.Empty).traverse_ { shape =>
      client.echoShape(shape, Headers.empty).assertEquals(shape)
    }
  }

  test("relative custom type with imported TypeMapper") {
    client.echoTemperature(Celsius(21.5), Headers.empty).assertEquals(Celsius(21.5))
  }
}

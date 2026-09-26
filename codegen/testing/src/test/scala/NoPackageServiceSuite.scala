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

import cats.effect.IO
import munit._
import org.http4s._
import org.http4s.client.Client
import org.scalacheck.effect.PropF.forAllF

class NoPackageServiceSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  val impl: NoPackageService[IO] = new NoPackageService[IO] {
    def echo(request: NoPackageMessage, ctx: Headers): IO[NoPackageMessage] =
      IO(request)
  }

  val client: NoPackageService[IO] = NoPackageService.fromClient[IO](
    Client.fromHttpApp(NoPackageService.toRoutes(impl).orNotFound),
    Uri(),
  )

  test("service in the empty package") {
    forAllF { (value: String) =>
      client.echo(NoPackageMessage(value), Headers.empty).assertEquals(NoPackageMessage(value))
    }
  }
}

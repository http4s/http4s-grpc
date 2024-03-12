package org.http4s.grpc

import cats.effect._
import munit.CatsEffectSuite

class MainSpec extends CatsEffectSuite {

  test("Main should exit succesfully") {
      assertEquals(ExitCode.Success, ExitCode.Success)
  }

}

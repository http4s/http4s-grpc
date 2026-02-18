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

package org.http4s.grpc.codecs

import cats.parse.Parser
import cats.syntax.all._
import org.http4s.Header
import org.http4s.ParseResult
import org.http4s.grpc.GrpcStatus.Code
import org.http4s.grpc.GrpcStatus.fromCodeValue
import org.http4s.internal.parsing.CommonRules.ows
import org.http4s.parser.AdditionalRules
import org.typelevel.ci.CIString

import scala.concurrent.duration._

object NamedHeaders {

  final case class GrpcTimeout(duration: FiniteDuration)

  object GrpcTimeout {
    private val parser = (
      (AdditionalRules.NonNegativeLong <* ows) ~
        Parser.charIn('H', 'M', 'S', 'm', 'u', 'n').mapFilter(c => decodeTimeUnit(c.toString()))
    ).map { case (value, unit) =>
      GrpcTimeout(FiniteDuration.apply(value, unit))
    }

    implicit val header: Header[GrpcTimeout, Header.Single] = Header.create(
      CIString("grpc-timeout"),
      (t: GrpcTimeout) => {
        val (x, unit) = encodeTimeUnit(t.duration.unit)
        val value = t.duration.length
        val out = value * x
        s"$out $unit"
      },
      (s: String) => ParseResult.fromParser(parser, "Invalid GrpcTimeout")(s),
    )

    private def encodeTimeUnit(t: TimeUnit): (Int, String) = t match {
      case NANOSECONDS => (1, "n")
      case MICROSECONDS => (1, "u")
      case MILLISECONDS => (1, "m")
      case SECONDS => (1, "S")
      case MINUTES => (1, "M")
      case HOURS => (1, "H")
      case DAYS => (24, "H")
    }

    private def decodeTimeUnit(s: String): Option[TimeUnit] = s match {
      case "H" => HOURS.some
      case "M" => MINUTES.some
      case "S" => SECONDS.some
      case "m" => MILLISECONDS.some
      case "u" => MICROSECONDS.some
      case "n" => NANOSECONDS.some
      case _ => None
    }
  }

  // https://grpc.github.io/grpc/core/md_doc_statuscodes.html
  final case class GrpcStatus(statusCode: Code)

  object GrpcStatus {
    private val parser = cats.parse.Numbers.nonNegativeIntString
      .mapFilter(s => fromCodeValue(s.toInt))
      .map(GrpcStatus(_))

    implicit val header: Header[GrpcStatus, Header.Single] = Header.create(
      CIString("grpc-status"),
      (t: GrpcStatus) => t.statusCode.value.toString(),
      (s: String) => ParseResult.fromParser(parser, "Invalid GrpcStatus")(s),
    )
  }

  final case class GrpcMessage(message: String)

  object GrpcMessage {

    implicit val header: Header[GrpcMessage, Header.Single] = Header.create(
      CIString("grpc-message"),
      (t: GrpcMessage) => t.message,
      (s: String) => ParseResult.success(GrpcMessage(s)),
    )
  }

}

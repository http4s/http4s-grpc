package org.http4s.grpc.codecs

import scala.concurrent.duration._
import cats.syntax.all._
import org.http4s.Header
import org.typelevel.ci.CIString

import cats.parse.Parser
import org.http4s.parser.AdditionalRules
import org.http4s.internal.parsing.CommonRules.ows
import org.http4s.ParseResult

object NamedHeaders {

  case class GrpcTimeout(duration: FiniteDuration)
  object GrpcTimeout {
    private val parser = (
      (AdditionalRules.NonNegativeLong <* ows) ~
      Parser.charIn('H', 'M', 'S', 'm', 'u', 'n').mapFilter(c => decodeTimeUnit(c.toString()))
    ).map{ case (value, unit) => 
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
      (s: String) => ParseResult.fromParser(parser, "Invalid GrpcTimeout")(s)
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

    private def decodeTimeUnit(s: String): Option[TimeUnit] =  s match {
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
  case class GrpcStatus(statusCode: Int)
  object GrpcStatus {
    private val parser = cats.parse.Numbers.nonNegativeIntString.map(s => GrpcStatus(s.toInt))

    implicit val header: Header[GrpcStatus, Header.Single] = Header.create(
      CIString("grpc-status"),
      (t: GrpcStatus) => {
        t.statusCode.toString()
      },
      (s: String) => ParseResult.fromParser(parser, "Invalid GrpcStatus")(s)
    )
  }

  case class GrpcMessage(message: String)
  object GrpcMessage {

    implicit val header: Header[GrpcMessage, Header.Single] = Header.create(
      CIString("grpc-message"),
      (t: GrpcMessage) => {
        t.message
      },
      (s: String) => ParseResult.success(GrpcMessage(s))
    )
  }

}
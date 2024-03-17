package org.http4s.grpc.codecs

import cats.syntax.all._
import scodec._
import scodec.bits._
import scodec.codecs._

final case class LengthPrefixedMessage(compressed: Boolean, message: ByteVector)

object LengthPrefixedMessage {

  val codec: scodec.Codec[LengthPrefixedMessage] =
    (
      uint8.xmap[Boolean](_ === 1, { case true => 1; case false => 0 }) ::
        variableSizeBytesLong(uint32, bytes)
    ).as[LengthPrefixedMessage]

}

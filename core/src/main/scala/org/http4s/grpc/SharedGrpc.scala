package org.http4s.grpc

import org.http4s.Header
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString

private object SharedGrpc {
  val ContentType: `Content-Type` = org.http4s.headers.`Content-Type`
    .parse("application/grpc+proto")
    .getOrElse(throw new Throwable("Impossible: This protocol is valid"))

  // TODO  Content-Coding → "identity" / "gzip" / "deflate" / "snappy" / {custom}
  val GrpcEncoding: Header.Raw = Header.Raw(CIString("grpc-encoding"), "identity")
  val GrpcAcceptEncoding: Header.Raw =
    org.http4s.Header.Raw(CIString("grpc-accept-encoding"), "identity")
  val TE: Header.Raw = Header.Raw(CIString("te"), "trailers")

}

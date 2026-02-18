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

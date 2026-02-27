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

sealed abstract class GrpcStatusCode(val value: Int) extends Product with Serializable

object GrpcStatusCode {
  case object Ok extends GrpcStatusCode(0)

  case object Cancelled extends GrpcStatusCode(1)

  case object Unknown extends GrpcStatusCode(2)

  case object InvalidArgument extends GrpcStatusCode(3)

  case object DeadlineExceeded extends GrpcStatusCode(4)

  case object NotFound extends GrpcStatusCode(5)

  case object AlreadyExists extends GrpcStatusCode(6)

  case object PermissionDenied extends GrpcStatusCode(7)

  case object ResourceExhausted extends GrpcStatusCode(8)

  case object FailedPrecondition extends GrpcStatusCode(9)

  case object Aborted extends GrpcStatusCode(10)

  case object OutOfRange extends GrpcStatusCode(11)

  case object Unimplemented extends GrpcStatusCode(12)

  case object Internal extends GrpcStatusCode(13)

  case object Unavailable extends GrpcStatusCode(14)

  case object DataLoss extends GrpcStatusCode(15)

  case object Unauthenticated extends GrpcStatusCode(16)

  def fromValue(value: Int): Option[GrpcStatusCode] =
    valuesMap.get(value)

  val values: Vector[GrpcStatusCode] =
    Vector(
      Ok,
      Cancelled,
      Unknown,
      InvalidArgument,
      DeadlineExceeded,
      NotFound,
      AlreadyExists,
      PermissionDenied,
      ResourceExhausted,
      FailedPrecondition,
      Aborted,
      OutOfRange,
      Unimplemented,
      Internal,
      Unavailable,
      DataLoss,
      Unauthenticated,
    )

  private val valuesMap: Map[Int, GrpcStatusCode] =
    values.map(code => (code.value, code)).toMap
}

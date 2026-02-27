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

import com.google.rpc.status.Status
import org.http4s.grpc.GrpcExceptions.StatusRuntimeException

sealed abstract class GrpcStatus extends Product with Serializable {
  def code: GrpcStatusCode

  def withCode(code: GrpcStatusCode): GrpcStatus

  def message: Option[String]

  def withMessage(message: String): GrpcStatus

  def withMessageOption(message: Option[String]): GrpcStatus

  def withoutMessage: GrpcStatus

  def details: Option[Status]

  def withDetails(details: Status): GrpcStatus

  def withDetailsOption(details: Option[Status]): GrpcStatus

  def withoutDetails: GrpcStatus

  def asStatusRuntimeException: StatusRuntimeException
}

object GrpcStatus {
  def apply(code: GrpcStatusCode): GrpcStatus =
    withCode(code)

  def withCode(code: GrpcStatusCode): GrpcStatus =
    GrpcStatusImpl(code, message = None, details = None)

  private final case class GrpcStatusImpl(
      override val code: GrpcStatusCode,
      override val message: Option[String],
      override val details: Option[Status],
  ) extends GrpcStatus {
    override def withCode(code: GrpcStatusCode): GrpcStatus =
      copy(code = code)

    override def withMessage(message: String): GrpcStatus =
      withMessageOption(Some(message))

    override def withMessageOption(message: Option[String]): GrpcStatus =
      copy(message = message)

    override def withoutMessage: GrpcStatus =
      withMessageOption(None)

    override def withDetails(details: Status): GrpcStatus =
      withDetailsOption(Some(details))

    override def withDetailsOption(details: Option[Status]): GrpcStatus =
      copy(details = details)

    override def withoutDetails: GrpcStatus =
      withDetailsOption(None)

    override def asStatusRuntimeException: StatusRuntimeException =
      StatusRuntimeException(this)
  }

  val Ok: GrpcStatus =
    withCode(GrpcStatusCode.Ok)

  val Cancelled: GrpcStatus =
    withCode(GrpcStatusCode.Cancelled)

  val Unknown: GrpcStatus =
    withCode(GrpcStatusCode.Unknown)

  val InvalidArgument: GrpcStatus =
    withCode(GrpcStatusCode.InvalidArgument)

  val DeadlineExceeded: GrpcStatus =
    withCode(GrpcStatusCode.DeadlineExceeded)

  val NotFound: GrpcStatus =
    withCode(GrpcStatusCode.NotFound)

  val AlreadyExists: GrpcStatus =
    withCode(GrpcStatusCode.AlreadyExists)

  val PermissionDenied: GrpcStatus =
    withCode(GrpcStatusCode.PermissionDenied)

  val ResourceExhausted: GrpcStatus =
    withCode(GrpcStatusCode.ResourceExhausted)

  val FailedPrecondition: GrpcStatus =
    withCode(GrpcStatusCode.FailedPrecondition)

  val Aborted: GrpcStatus =
    withCode(GrpcStatusCode.Aborted)

  val OutOfRange: GrpcStatus =
    withCode(GrpcStatusCode.OutOfRange)

  val Unimplemented: GrpcStatus =
    withCode(GrpcStatusCode.Unimplemented)

  val Internal: GrpcStatus =
    withCode(GrpcStatusCode.Internal)

  val Unavailable: GrpcStatus =
    withCode(GrpcStatusCode.Unavailable)

  val DataLoss: GrpcStatus =
    withCode(GrpcStatusCode.DataLoss)

  val Unauthenticated: GrpcStatus =
    withCode(GrpcStatusCode.Unauthenticated)
}

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

import scalapb.GeneratedMessage

sealed abstract class GrpcStatus extends Product with Serializable {
  def code: GrpcStatusCode

  def withCode(code: GrpcStatusCode): GrpcStatus

  def message: Option[String]

  def withMessage(message: String): GrpcStatus

  def withMessageOption(message: Option[String]): GrpcStatus

  def withoutMessage: GrpcStatus

  def details: Option[GrpcStatusDetails]

  def addDetails[A <: GeneratedMessage](details: A): GrpcStatus

  def addAllDetails(details: PbAny*): GrpcStatus

  def withDetails(details: GrpcStatusDetails): GrpcStatus

  def withDetailsOption(details: Option[GrpcStatusDetails]): GrpcStatus

  def withoutDetails: GrpcStatus

  def toException: GrpcStatusException
}

object GrpcStatus {
  def apply(
      code: GrpcStatusCode
  ): GrpcStatus =
    apply(code, message = None)

  def apply(
      code: GrpcStatusCode,
      message: Option[String],
  ): GrpcStatus =
    apply(code, message, details = None)

  def apply(
      code: GrpcStatusCode,
      message: Option[String],
      details: Option[GrpcStatusDetails],
  ): GrpcStatus =
    GrpcStatusImpl(code, message, details)

  private final case class GrpcStatusImpl(
      override val code: GrpcStatusCode,
      override val message: Option[String],
      override val details: Option[GrpcStatusDetails],
  ) extends GrpcStatus {
    override def withCode(code: GrpcStatusCode): GrpcStatus =
      copy(code = code, details = details.map(_.withCode(code)))

    override def withMessage(message: String): GrpcStatus =
      withMessageOption(Some(message))

    override def withMessageOption(message: Option[String]): GrpcStatus =
      copy(message = message, details = details.map(_.withMessage(message.getOrElse(""))))

    override def withoutMessage: GrpcStatus =
      withMessageOption(None)

    override def addDetails[A <: GeneratedMessage](details: A): GrpcStatus =
      addAllDetails(PbAny.pack(details))

    override def addAllDetails(details: PbAny*): GrpcStatus =
      if (details.isEmpty) this
      else
        withDetails(this.details match {
          case Some(existing) => existing.addAllDetails(details: _*)
          case None => GrpcStatusDetails(code, message.getOrElse(""), details.toList)
        })

    override def withDetails(details: GrpcStatusDetails): GrpcStatus =
      withDetailsOption(Some(details))

    override def withDetailsOption(details: Option[GrpcStatusDetails]): GrpcStatus =
      copy(details = details)

    override def withoutDetails: GrpcStatus =
      withDetailsOption(None)

    override def toException: GrpcStatusException =
      GrpcStatusException(this)

    override def toString: String =
      s"GrpcStatus($code, $message, $details)"
  }

  val Ok: GrpcStatus =
    apply(GrpcStatusCode.Ok)

  val Cancelled: GrpcStatus =
    apply(GrpcStatusCode.Cancelled)

  val Unknown: GrpcStatus =
    apply(GrpcStatusCode.Unknown)

  val InvalidArgument: GrpcStatus =
    apply(GrpcStatusCode.InvalidArgument)

  val DeadlineExceeded: GrpcStatus =
    apply(GrpcStatusCode.DeadlineExceeded)

  val NotFound: GrpcStatus =
    apply(GrpcStatusCode.NotFound)

  val AlreadyExists: GrpcStatus =
    apply(GrpcStatusCode.AlreadyExists)

  val PermissionDenied: GrpcStatus =
    apply(GrpcStatusCode.PermissionDenied)

  val ResourceExhausted: GrpcStatus =
    apply(GrpcStatusCode.ResourceExhausted)

  val FailedPrecondition: GrpcStatus =
    apply(GrpcStatusCode.FailedPrecondition)

  val Aborted: GrpcStatus =
    apply(GrpcStatusCode.Aborted)

  val OutOfRange: GrpcStatus =
    apply(GrpcStatusCode.OutOfRange)

  val Unimplemented: GrpcStatus =
    apply(GrpcStatusCode.Unimplemented)

  val Internal: GrpcStatus =
    apply(GrpcStatusCode.Internal)

  val Unavailable: GrpcStatus =
    apply(GrpcStatusCode.Unavailable)

  val DataLoss: GrpcStatus =
    apply(GrpcStatusCode.DataLoss)

  val Unauthenticated: GrpcStatus =
    apply(GrpcStatusCode.Unauthenticated)
}

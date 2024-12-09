package org.http4s.grpc

import GrpcExceptions.StatusRuntimeException

object GrpcStatus {

  sealed abstract class Code(val value: Int) extends Product with Serializable {
    def asStatusRuntimeException(message: Option[String] = None): StatusRuntimeException =
      StatusRuntimeException(this, message)
  }

  case object Ok extends Code(0)

  case object Cancelled extends Code(1)

  case object Unknown extends Code(2)

  case object InvalidArgument extends Code(3)

  case object DeadlineExceeded extends Code(4)

  case object NotFound extends Code(5)

  case object AlreadyExists extends Code(6)

  case object PermissionDenied extends Code(7)

  case object ResourceExhausted extends Code(8)

  case object FailedPrecondition extends Code(9)

  case object Aborted extends Code(10)

  case object OutOfRange extends Code(11)

  case object Unimplemented extends Code(12)

  case object Internal extends Code(13)

  case object Unavailable extends Code(14)

  case object DataLoss extends Code(15)

  case object Unauthenticated extends Code(16)

  def fromCodeValue(value: Int): Option[Code] = codeValues.find(_.value == value)

  val codeValues: List[Code] = List(
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

}

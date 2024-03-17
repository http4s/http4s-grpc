package org.http4s.grpc

object GrpcExceptions {
  final case class StatusRuntimeException(status: Int, message: Option[String])
      extends RuntimeException({
        val me = message.fold("")((m: String) => s", Message-${m}")
        s"Grpc Failed: Status-$status${me}"
      })
}

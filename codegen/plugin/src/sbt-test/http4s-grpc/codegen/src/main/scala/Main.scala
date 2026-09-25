import hello.world.test_service.TestMessage
import org.http4s.grpc.codecs.ScalaPb

object Main {
  def main(args: Array[String]): Unit = {
    val codec = ScalaPb.codecForGenerated(TestMessage)
    val message = TestMessage("hello")
    val decoded = codec.decodeValue(codec.encode(message).require).require
    assert(decoded == message, s"round trip turned $message into $decoded")
  }
}

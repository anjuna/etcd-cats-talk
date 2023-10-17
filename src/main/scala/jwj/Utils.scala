package jwj

import cats.effect.IO
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.kv.TxnResponse

import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import scala.language.implicitConversions

object Utils {

  def extract(res: TxnResponse): String = {
    val kv = res.getGetResponses.get(0).getKvs.get(0)
    kv.getValue.toString(Charset.forName("UTF-8"))
  }

  implicit def stringToByteSequence(in: String): ByteSequence =
    ByteSequence.from(in.getBytes())

  //Copying behaviour of `scala.concurrent.java8.FuturesConvertersImpl.P`
  def toIO[T](in: => CompletableFuture[T]): IO[T] =
    IO.async_[T] { cb =>
      in.whenComplete((v: T, e: Throwable) =>
        if (e == null) {
          cb(Right(v))
        } else {
          cb(Left(e))
        }
      )
    }

  implicit class IOLogOps(log: => String) {
    def logIO: IO[Unit] = IO(println(log))
  }
}

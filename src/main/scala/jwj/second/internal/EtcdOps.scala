package jwj.second.internal

import cats.effect.{FiberIO, IO}
import io.etcd.jetcd.Client
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.op.{Cmp, CmpTarget, Op}
import io.etcd.jetcd.options.{GetOption, PutOption}
import io.grpc.stub.StreamObserver
import jwj.second.internal.EtcdOps._

private[second] trait EtcdOps {
  def compareAndSwap(
                      key: String,
                      update: String,
                    ): IO[CASResponse]
}

private[second] object EtcdOps {

  sealed trait CASResponse
  case class WonRace(cancelFiber: FiberIO[Unit]) extends CASResponse
  case class LostRace(str: String) extends CASResponse

}

private[second] class EtcdOpsImpl(etcdv3Client: Client) extends EtcdOps {
  import jwj.Utils._

  //Either we lost the lock, or we won it and need to keep a lease alive. The returned fiber can
  //be cancelled to relinquish this ownership
  def compareAndSwap(key: String, update: String): IO[CASResponse] =
    for {
      leaseId <- toIO(etcdv3Client.getLeaseClient.grant(3)).map(_.getID)
      keepLeaseAliveFiber <- IO.async[Unit] { cb =>
        IO(etcdv3Client.getLeaseClient.keepAlive(leaseId, new MyObserver(cb)))
          .map(closeable => Some(IO(closeable.close())))
      }.start
      resp <- doCompareAndSwap(key = key, update = update, leaseId = leaseId).flatMap {
        case actual if actual == update =>
          IO.pure[CASResponse](WonRace(keepLeaseAliveFiber))
        case actualWinner =>
          keepLeaseAliveFiber.cancel.map[CASResponse](_ => LostRace(actualWinner))
      }
    } yield resp

  private def doCompareAndSwap(
                                  key: String,
                                  update: String,
                                  leaseId: Long
                                ): IO[String] = {
    val valueDoesntYetExist = new Cmp(key, Cmp.Op.EQUAL, CmpTarget.createRevision(0L))
    val setItToThis = Op.put(key, update, PutOption.builder().withLeaseId(leaseId).build())
    val getValue = Op.get(key, GetOption.builder().build())

    toIO(
      etcdv3Client.getKVClient
        .txn()
        .If(valueDoesntYetExist)
        .Then(setItToThis, getValue)
        .Else(getValue)
        .commit()
    ).map(extract)
  }

  private class MyObserver(cb: Either[Throwable, Unit] => Unit) extends StreamObserver[LeaseKeepAliveResponse] {
    override def onNext(value: LeaseKeepAliveResponse): Unit = {}
//      println(s"Received lease keepalive response" + value.getID)

    override def onError(t: Throwable): Unit = {
      println(s"Received error in keep alive: ${t.getMessage}")
      cb(Left(t))
    }

    override def onCompleted(): Unit =
      cb(Left(new RuntimeException(s"Early completion of lease")))
  }
}
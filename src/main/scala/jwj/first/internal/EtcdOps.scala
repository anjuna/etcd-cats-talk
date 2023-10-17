package jwj.first.internal

import cats.effect.IO
import io.etcd.jetcd.Client
import io.etcd.jetcd.op.{Cmp, CmpTarget, Op}
import io.etcd.jetcd.options.{GetOption, PutOption}
import jwj.first.internal.EtcdOps._

private[first] trait EtcdOps {
  def compareAndSwap(
                      key: String,
                      update: String,
                    ): IO[CASResponse]

  def deleteKey(key: String): IO[Unit]
}

private[first] object EtcdOps {

  sealed trait CASResponse
  case class WonRace() extends CASResponse
  case class LostRace(actualWinner: String) extends CASResponse

}

private[first] class EtcdOpsImpl(etcdv3Client: Client) extends EtcdOps {
  import jwj.Utils._

  override def compareAndSwap(
                               key: String,
                               update: String,
                             ): IO[CASResponse] =
    doCompareAndSwap(key = key, update = update).map {
      case actual if actual == update =>
        WonRace()
      case actual =>
        LostRace(actual)
    }

  protected def doCompareAndSwap(
                                  key: String,
                                  update: String
                                ): IO[String] = {
    val valueDoesntYetExist = new Cmp(key, Cmp.Op.EQUAL, CmpTarget.createRevision(0L))

    val setItToThis = Op.put(key, update, PutOption.builder().build())

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

  override def deleteKey(key: String): IO[Unit] = {
    toIO(etcdv3Client.getKVClient.delete(key)).void
  }
}
package jwj.first.internal

import cats.effect.IO
import io.etcd.jetcd.Client

private[first] object Setup {
  case class AppDependencies(etcdOps: EtcdOps, taskName: String, myLocation: String)

  def apply(args: List[String]): IO[AppDependencies] = {
    for {
      myLocation <- IO.fromOption(args.headOption)(new IllegalArgumentException("no location supplied"))
      client <- IO(Client.builder().endpoints("http://localhost:2379").build())
      ops = new EtcdOpsImpl(client)
    } yield AppDependencies(ops, "first-impl-task", myLocation)
  }
}

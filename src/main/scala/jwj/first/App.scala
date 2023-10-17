package jwj.first

import cats.effect.{ExitCode, IO, IOApp}
import jwj.WorkerTask
import jwj.Utils._
import jwj.first.internal.{EtcdOps, Setup}

import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.Random

object App extends IOApp {

  /**
   *
   * FIRST: Scalable
   *
   */
  override def run(args: List[String]): IO[ExitCode] =
    for {
      dependencies <- Setup(args)
      _ <- s"\n\n** Starting Node ${dependencies.myLocation} **\n\n".logIO
      _ <- IO.sleep((Random.nextInt(500) + 1000).millis)

      _ <- runNode(dependencies.etcdOps, dependencies.taskName, dependencies.myLocation)
    } yield ExitCode.Success

  private def runNode(ops: EtcdOps, taskName: String, myLocation: String): IO[Unit] =
    ops.compareAndSwap(taskName, myLocation).flatMap {
      case EtcdOps.WonRace() =>
        "Won race!".logIO >>
        WorkerTask.run() >>
        "Cleaning up".logIO >>
        ops.deleteKey(taskName)
      case EtcdOps.LostRace(actualWinner) =>
        s"Lost to: $actualWinner! Giving up.".logIO
    }
}
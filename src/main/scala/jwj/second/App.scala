package jwj.second

import cats.effect.{ExitCode, IO, IOApp}
import jwj.WorkerTask
import jwj.second.internal.EtcdOps._
import jwj.Utils._
import jwj.second.internal.{EtcdOps, Setup}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object App extends IOApp {

  /**
   *
   * SECOND: Scalable & Resilient
   * but can't complete successfully without duplication
   *
   */
  override def run(args: List[String]): IO[ExitCode] =
    for {
      dependencies <- Setup(args)
      _ <- s"\n\n** Starting Node ${dependencies.myLocation} **\n\n".logIO
      _ <- IO.sleep((Random.nextInt(500) + 1000).millis)

      _ <- runNode(dependencies.etcdOps, dependencies.taskName, dependencies.myLocation)
    } yield ExitCode.Success

  private def runNode(ops: EtcdOps, taskName: String, myLocation: String): IO[Unit] = {
    def go(): IO[Unit] =
      ops.compareAndSwap(taskName, myLocation).flatMap {
        case WonRace(cancelFiber) =>
            "Won race!".logIO >>
            WorkerTask.run() >>
            "Finished task, cancelling keep alive".logIO >>
            cancelFiber.cancel
        case LostRace(actual) =>
          s"Lost to: $actual retrying".logIO >>
            IO.sleep(1.second) >>
            go()
      }

    go()
  }
}
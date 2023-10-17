package jwj.third

import cats.effect.{ExitCode, IO, IOApp}
import jwj.WorkerTask
import jwj.Utils._
import jwj.third.internal.EtcdOps._
import jwj.third.internal.{EtcdOps, Setup}

import scala.concurrent.duration.DurationInt
import scala.util.Random

object App extends IOApp {

  /**
   *
   * THIRD: Resilient & Scalable
   *
   */
  override def run(args: List[String]): IO[ExitCode] =
    for {
      dependencies <- Setup(args)
      _ <- s"\n\n** Starting Node ${dependencies.myLocation} **\n\n".logIO


      _ <- runNode(dependencies.etcdOps, dependencies.taskName, dependencies.myLocation)
    } yield ExitCode.Success

  private val sentinelSuccessValue = "something-really-really-weird"

  private def runNode(ops: EtcdOps, taskName: String, myLocation: String): IO[Unit] = {
    def go(): IO[Unit] =
      ops.compareAndSwap(taskName, myLocation).flatMap {
        case WonRace(cancelFiber, leaseID) =>
            "Won race!".logIO >>
            WorkerTask.run() >>
            "Finished task, cancelling keep alive and signalling successful termination".logIO >>
            ops.put(taskName, sentinelSuccessValue, leaseID) >> cancelFiber.cancel
        case LostRace(actual) if actual != sentinelSuccessValue =>
          s"Lost to: $actual retrying in case they fail.".logIO >>
            IO.sleep(1.second) >>
            go()

        case LostRace(_) =>
          s"Task has completed elsewhere, giving up!".logIO
      }

    go()
  }
}
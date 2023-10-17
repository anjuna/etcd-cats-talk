package jwj

import cats.effect.IO
import scala.concurrent.duration.DurationInt

object WorkerTask {

  def run(): IO[Unit] =
    IO(println("\n****************************************\n\n************ PERFORMING TASK ***********\n\n****************************************\n")) >>
    IO.sleep(10.seconds) >>
    IO(println("\n****************************************\n\n****** TASK COMPLETED SUCCESSFULLY *****\n\n****************************************\n"))
}

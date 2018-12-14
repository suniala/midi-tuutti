package midituutti

import midituutti.engine.createEngine

import scala.io.StdIn

object MidiTuuttiCommand extends App {
  val filePath = if (args.length >= 1) args(0) else throw new IllegalArgumentException("must give path to midi file")
  val startMeasure = if (args.length >= 2) Some(args(1).toInt) else None
  val endMeasure = if (args.length >= 3) Some(args(2).toInt) else None

  val engine = createEngine(filePath, startMeasure, endMeasure)
  engine.start()
  StdIn.readLine("Press enter to quit.")
  engine.stop()
}

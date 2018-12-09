package midituutti

import java.util.concurrent.SynchronousQueue

import midituutti.midi.{MidiEvent, OutputTimestamp, Tempo, Tick}

import scala.io.StdIn


object MidiTuutti extends App {
  val filePath = if (args.length >= 1) args(0) else throw new IllegalArgumentException("must give path to midi file")
  val startMeasure = if (args.length >= 2) Some(args(1).toInt) else None
  val endMeasure = if (args.length >= 3) Some(args(2).toInt) else None

  val synthesizerPort = midi.createDefaultSynthesizerPort
  val midiFile = midi.openFile(filePath)
  // TODO: read tempo from file
  val tempo = Tempo(120.0)

  val track = TrackStructure.of(midiFile)

  @volatile var playing = true
  val queue = new SynchronousQueue[MidiEvent]

  val reader: Thread = new Thread {
    override def run(): Unit = {
      // TODO: fix the playing loop
      while (playing) {
        try {
          for (measure <-
                 track.measures.slice(
                   startMeasure.getOrElse(1) - 1,
                   endMeasure.getOrElse(track.measures.length));
               event <- measure.events) {
            queue.put(event)
          }
        } catch {
          case _: InterruptedException =>
        }
      }

      println("reader done")
    }
  }

  val player: Thread = new Thread {
    override def run(): Unit = {
      var prevTicks: Option[Tick] = None

      try {
        while (playing) {
          val event = queue.take()

          val ticksDelta = event.ticks - prevTicks.getOrElse(event.ticks)
          val timestampDelta = OutputTimestamp.ofTickAndTempo(ticksDelta, midiFile.ticksPerBeat, tempo)

          if (timestampDelta.nonNil) {
            Thread.sleep(timestampDelta.millisPart, timestampDelta.nanosPart)
          }

          synthesizerPort.send(event.message)

          prevTicks = Some(event.ticks)
        }
      } catch {
        case _: InterruptedException =>
      }

      println("player done")
    }
  }

  player.start()
  reader.start()

  StdIn.readLine("Press enter to quit.")

  playing = false
  player.interrupt()
  reader.interrupt()
}

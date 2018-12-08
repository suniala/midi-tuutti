package midituutti

import java.util.concurrent.SynchronousQueue

import midituutti.midi.{MidiEvent, OutputTimestamp, Tempo, Tick}

import scala.io.StdIn


object MidiTuutti extends App {
  val filePath = if (args.length == 1) args(0) else throw new IllegalArgumentException("must give path to midi file")

  val synthesizerPort = midi.createDefaultSynthesizerPort
  val midiFile = midi.openFile(filePath)
  val tempo = Tempo(120.0)

  @volatile var playing = true
  val queue = new SynchronousQueue[MidiEvent]

  val reader: Thread = new Thread {
    override def run(): Unit = {
      while (playing) {
        try {
          for (event <- midiFile.track) {
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
          val timestampDelta = OutputTimestamp.ofTickAndTempo(ticksDelta, midiFile.resolution, tempo)

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

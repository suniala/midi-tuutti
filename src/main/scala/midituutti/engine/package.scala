package midituutti

import java.util.concurrent.SynchronousQueue

import midituutti.midi._

package object engine {

  trait Engine {
    def start(): Unit

    def stop(): Unit
  }

  def createEngine(filePath: String, startMeasure: Option[Int], endMeasure: Option[Int]): Engine = {
    val synthesizerPort = midi.createDefaultSynthesizerPort
    val midiFile = midi.openFile(filePath)
    // TODO: read tempo from file
    val tempo = Tempo(120.0)

    val track = TrackStructure.of(midiFile)

    @volatile var playing = false
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

    new Engine {
      override def start(): Unit = {
        playing = true
        player.start()
        reader.start()
      }

      override def stop(): Unit = {
        playing = false
        player.interrupt()
        reader.interrupt()
      }
    }
  }
}

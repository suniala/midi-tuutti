package midituutti

import java.util.concurrent.SynchronousQueue

import midituutti.midi._

package object engine {

  trait Engine {
    def play(): Unit

    def stop(): Unit

    def quit(): Unit
  }

  private abstract class Player extends Thread {
    def play(): Unit
  }

  def createEngine(filePath: String, startMeasure: Option[Int], endMeasure: Option[Int]): Engine = {
    val synthesizerPort = midi.createDefaultSynthesizerPort
    val midiFile = midi.openFile(filePath)
    // TODO: read tempo from file
    val tempo = Tempo(120.0)

    val track = TrackStructure.of(midiFile)

    @volatile var running = true
    @volatile var playing = false
    val queue = new SynchronousQueue[MidiEvent]

    val reader: Thread = new Thread {
      override def run(): Unit = {
        while (running) {
          try {
            for (measure <-
                   track.measures.slice(
                     startMeasure.getOrElse(1) - 1,
                     endMeasure.getOrElse(track.measures.length));
                 event <- measure.events) {
              queue.put(event)
            }
          } catch {
            case _: InterruptedException => // stop playing
          }
        }

        println("reader done")
      }
    }

    val player: Player = new Player {
      private val playMutex = new Object()

      override def play(): Unit = playMutex.synchronized {
        playMutex.notify()
      }

      private def waitForPlay(): Unit = playMutex.synchronized {
        playMutex.wait()
      }

      override def run(): Unit = {
        var prevTicks: Option[Tick] = None

        try {
          while (running) {
            waitForPlay()

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
              case _: InterruptedException => // stop playing
            }
          }
        } catch {
          case _: InterruptedException => // stop running
        }

        println("player done")
      }
    }

    player.start()
    reader.start()

    new Engine {
      override def play(): Unit = {
        playing = true
        player.play()
      }

      override def stop(): Unit = {
        playing = false
        player.interrupt()
        reader.interrupt()
      }

      override def quit(): Unit = {
        running = false
        stop()
      }
    }
  }
}

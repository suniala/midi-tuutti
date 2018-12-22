package midituutti

import java.util.concurrent.SynchronousQueue

import midituutti.midi.MessageDecoder.Accessors
import midituutti.midi._

import scala.collection.mutable

package object engine {
  private def muteOrPass(mutedChannels: collection.Set[Int], message: MidiMessage): MidiMessage = {
    if (message.isNote) {
      val note = Accessors.noteAccessor.get(message)
      if (mutedChannels.contains(note.channel)) {
        NoteMessage(note.copy(velocity = 0))
      } else {
        message
      }
    } else {
      message
    }
  }

  trait Engine {
    def isPlaying: Boolean

    def play(): Unit

    def stop(): Unit

    def quit(): Unit

    def mute(channel: Int): Engine

    def unMute(channel: Int): Engine

    def isMuted(channel: Int): Boolean
  }

  def createEngine(filePath: String, startMeasure: Option[Int], endMeasure: Option[Int]): Engine = {
    val synthesizerPort = midi.createDefaultSynthesizerPort
    val midiFile = midi.openFile(filePath)
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

    class MyPlayer extends Thread {
      private val playMutex = new Object()

      private val mutedChannels = new mutable.HashSet[Int]()

      def play(): Unit = playMutex.synchronized {
        playMutex.notify()
      }

      def mute(channel: Int): Unit = mutedChannels.add(channel)

      def unMute(channel: Int): Unit = mutedChannels.remove(channel)

      def isMuted(channel: Int): Boolean = mutedChannels.contains(channel)

      private def waitForPlay(): Unit = playMutex.synchronized {
        playMutex.wait()
      }

      override def run(): Unit = {
        var prevTicks: Option[Tick] = None
        var tempo = Tempo(120)

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

                if (event.message.metaType.contains(MetaType.Tempo)) {
                  tempo = Accessors.tempoAccessor.get(event.message)
                } else {
                  synthesizerPort.send(muteOrPass(mutedChannels, event.message))
                }

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

    val player = new MyPlayer
    player.start()
    reader.start()

    new Engine {
      override def isPlaying: Boolean = playing

      override def play(): Unit = {
        playing = true
        player.play()
      }

      override def stop(): Unit = {
        if (playing) {
          playing = false
          player.interrupt()
          reader.interrupt()
          synthesizerPort.panic()
        }
      }

      override def quit(): Unit = {
        playing = false
        running = false
        reader.interrupt()
        player.interrupt()
      }

      override def mute(channel: Int): Engine = {
        player.mute(channel)
        this
      }

      override def unMute(channel: Int): Engine = {
        player.unMute(channel)
        this
      }

      override def isMuted(channel: Int): Boolean = player.isMuted(channel)
    }
  }
}

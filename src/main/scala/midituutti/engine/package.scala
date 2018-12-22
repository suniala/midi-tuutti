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
    type TempoListener = (Option[Tempo], Tempo) => Unit

    def isPlaying: Boolean

    def play(): Unit

    def stop(): Unit

    def quit(): Unit

    def mute(channel: Int): Engine

    def unMute(channel: Int): Engine

    def isMuted(channel: Int): Boolean

    def addTempoListener(listener: TempoListener)

    def updateTempoMultiplier(f: Double => Double): Unit
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

    trait PlayerListener {
      def tempoChanged(oldTempo: Option[Tempo], newTempo: Tempo)
    }

    class MyPlayer(private val listener: PlayerListener, var tempoMultiplier: Double) extends Thread {
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
        var tempo: Option[Tempo] = None

        try {
          while (running) {
            waitForPlay()

            try {
              while (playing) {
                val event = queue.take()

                val ticksDelta = event.ticks - prevTicks.getOrElse(event.ticks)
                val timestampDelta = tempo.map(
                  t => OutputTimestamp.ofTickAndTempo(ticksDelta, midiFile.ticksPerBeat, t * tempoMultiplier))

                if (timestampDelta.isDefined) {
                  if (timestampDelta.get.nonNil) {
                    Thread.sleep(timestampDelta.get.millisPart, timestampDelta.get.nanosPart)
                  }
                }

                if (event.message.metaType.contains(MetaType.Tempo)) {
                  val oldTempo = tempo
                  tempo = Some(Accessors.tempoAccessor.get(event.message))
                  listener.tempoChanged(oldTempo, tempo.get)
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

    new Engine with PlayerListener {
      val player = new MyPlayer(this, 1.0)
      player.start()
      reader.start()

      private val tempoListeners = new mutable.HashSet[TempoListener]()

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

      override def addTempoListener(listener: TempoListener): Unit = tempoListeners.add(listener)

      override def tempoChanged(oldTempo: Option[Tempo], newTempo: Tempo): Unit =
        tempoListeners.foreach(_.apply(oldTempo, newTempo))

      override def updateTempoMultiplier(f: Double => Double): Unit = {
        player.tempoMultiplier = f(player.tempoMultiplier)
      }
    }
  }
}

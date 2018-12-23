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

    def jumpToBar(f: Int => Int): Unit
  }

  private class PlaySemaphore {
    private val waitLock = new Object()

    @volatile private var playing = false

    def play(): Unit = {
      val before = playing
      playing = true
      if (!before) waitLock.synchronized {
        waitLock.notify()
      }
    }

    def stop(): Unit = {
      playing = false
    }

    def isPlaying: Boolean = playing

    def waitForPlay(): Unit = {
      while (!playing) {
        try {
          waitLock.synchronized {
            waitLock.wait()
          }
        } catch {
          case _: InterruptedException => // play
        }
      }
    }
  }

  def createEngine(filePath: String, initialFrom: Option[Int], initialTo: Option[Int]): Engine = {
    val synthesizerPort = midi.createDefaultSynthesizerPort
    val midiFile = midi.openFile(filePath)
    val track = TrackStructure.of(midiFile)

    val queue = new SynchronousQueue[MidiEvent]

    class Reader(val playControl: PlaySemaphore,
                 @volatile var measureCursor: Int,
                 @volatile var from: Int,
                 @volatile var to: Int) extends Thread {
      setDaemon(true)

      override def run(): Unit = {
        while (true) {
          playControl.waitForPlay()
          println("reader: playing")

          try {
            while (playControl.isPlaying) {
              val startFrom = measureCursor
              println(s"reader: reading $startFrom - $to")
              for (readerCursor <- startFrom to to) {
                println(s"reader: at $readerCursor")
                measureCursor = readerCursor
                val measure = track.measures(readerCursor - 1)
                for (event <- measure.events) {
                  queue.put(event)
                }
              }
              measureCursor = from
            }
          } catch {
            case _: InterruptedException => println("reader: stop playing")
          }
        }

        println("reader: done")
      }
    }

    trait PlayerListener {
      def tempoChanged(oldTempo: Option[Tempo], newTempo: Tempo)
    }

    class Player(val playControl: PlaySemaphore,
                 private val listener: PlayerListener,
                 var tempoMultiplier: Double) extends Thread {
      setDaemon(true)

      private val mutedChannels = new mutable.HashSet[Int]()

      def mute(channel: Int): Unit = mutedChannels.add(channel)

      def unMute(channel: Int): Unit = mutedChannels.remove(channel)

      def isMuted(channel: Int): Boolean = mutedChannels.contains(channel)

      override def run(): Unit = {
        var tempo: Option[Tempo] = None

        while (true) {
          playControl.waitForPlay()
          var prevTicks: Option[Tick] = None
          println("player: playing")

          try {
            while (playControl.isPlaying) {
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
            case _: InterruptedException => println("player: stop playing")
          }
        }

        println("player: done")
      }
    }

    new Engine with PlayerListener {
      val readerControl = new PlaySemaphore
      val playerControl = new PlaySemaphore

      val player = new Player(playerControl, this, 1.0)
      player.start()
      val reader = new Reader(readerControl, initialFrom.getOrElse(1), initialFrom.getOrElse(1),
        initialTo.getOrElse(track.measures.length))
      reader.start()

      private val tempoListeners = new mutable.HashSet[TempoListener]()

      override def isPlaying: Boolean = playerControl.isPlaying

      override def play(): Unit = {
        playerControl.play()
        readerControl.play()
      }

      override def stop(): Unit = {
        if (playerControl.isPlaying) {
          signalStop()
        }
      }

      private def signalStop(): Unit = {
        readerControl.stop()
        playerControl.stop()
        reader.interrupt()
        player.interrupt()
        synthesizerPort.panic()
      }

      override def quit(): Unit = {
        signalStop()
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

      override def jumpToBar(f: Int => Int): Unit = {
        stop()
        reader.measureCursor = Math.min(Math.max(f(reader.measureCursor), 1), track.measures.length)
        play()
      }
    }
  }
}

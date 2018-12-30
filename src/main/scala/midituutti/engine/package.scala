package midituutti

import java.util.concurrent.SynchronousQueue

import midituutti.engine.ClickType.ClickType
import midituutti.midi.MessageDecoder.{Accessors, Note, OnOff}
import midituutti.midi._

import scala.collection.mutable

package object engine {
  private def muteOrPass(mutedChannels: collection.Set[EngineChannel], message: MidiMessage): MidiMessage = {
    if (message.isNote) {
      val note = Accessors.noteAccessor.get(message)
      if (mutedChannels.exists({ case MidiChannel(channel) => channel == note.channel; case _ => false })) {
        NoteMessage(message.ticks, note.copy(velocity = 0))
      } else {
        message
      }
    } else {
      message
    }
  }

  trait EngineEvent {
    def ticks: Tick
  }

  case class MessageEvent(message: MidiMessage) extends EngineEvent {
    override def ticks: Tick = message.ticks
  }

  object ClickType extends Enumeration {
    type ClickType = Value

    val One: ClickType = Value
    val Quarter: ClickType = Value
    val Eight: ClickType = Value
  }

  case class ClickEvent(ticks: Tick, click: ClickType) extends EngineEvent

  trait EngineChannel

  case class MidiChannel(channel: Int) extends EngineChannel

  object ClickChannel extends EngineChannel

  trait Engine {

    case class TempoEvent(tempo: Option[Tempo],
                          multiplier: Double,
                          adjustedTempo: Option[Tempo])

    type TempoListener = TempoEvent => Unit

    def isPlaying: Boolean

    def play(): Unit

    def stop(): Unit

    def quit(): Unit

    def mute(channel: EngineChannel): Engine

    def unMute(channel: EngineChannel): Engine

    def isMuted(channel: EngineChannel): Boolean

    def addTempoListener(listener: TempoListener)

    def updateTempoMultiplier(f: Double => Double): Unit

    def jumpToBar(f: Int => Int): Unit
  }

  private class PlayControl {
    private val waitLock = new Object()

    @volatile private var playing = false

    def play(): Unit = waitLock.synchronized {
      val before = playing
      playing = true
      if (!before) waitLock.notifyAll()
    }

    def stop(): Unit = {
      playing = false
    }

    def isPlaying: Boolean = playing

    def waitForPlay(): Unit = waitLock.synchronized {
      while (!playing) {
        try {
          waitLock.wait()
        } catch {
          case _: InterruptedException => // ignore, probably we are quitting the app
        }
      }
    }
  }

  def createEngine(filePath: String, initialFrom: Option[Int], initialTo: Option[Int]): Engine = {
    val synthesizerPort = midi.createDefaultSynthesizerPort
    val midiFile = midi.openFile(filePath)
    val track = TrackStructure.withClick(midiFile)

    val queue = new SynchronousQueue[EngineEvent]

    class Reader(val playControl: PlayControl,
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
      def tempoChanged(): Unit
    }

    class Player(val playControl: PlayControl,
                 private val listener: PlayerListener,
                 var tempoMultiplier: Double) extends Thread {
      setDaemon(true)

      private val mutedChannels = new mutable.HashSet[EngineChannel]()

      private var tempo: Option[Tempo] = None

      def mute(channel: EngineChannel): Unit = mutedChannels.add(channel)

      def unMute(channel: EngineChannel): Unit = mutedChannels.remove(channel)

      def isMuted(channel: EngineChannel): Boolean = mutedChannels.contains(channel)

      def currentTempo: Option[Tempo] = tempo

      def currentAdjustedTempo: Option[Tempo] = tempo.map(_ * tempoMultiplier)

      override def run(): Unit = {
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

              event match {
                case MessageEvent(message) =>
                  if (message.metaType.contains(MetaType.Tempo)) {
                    tempo = Some(Accessors.tempoAccessor.get(message))
                    listener.tempoChanged()
                  } else {
                    synthesizerPort.send(muteOrPass(mutedChannels, message))
                  }
                case ClickEvent(t, click) =>
                  if (!mutedChannels.contains(ClickChannel))
                    click match {
                      case ClickType.One => synthesizerPort.send(NoteMessage(t, Note(OnOff.On, 10, 40, 100)))
                      case ClickType.Quarter => synthesizerPort.send(NoteMessage(t, Note(OnOff.On, 10, 41, 100)))
                      case ClickType.Eight => synthesizerPort.send(NoteMessage(t, Note(OnOff.On, 10, 42, 100)))
                    }
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
      val playControl = new PlayControl

      val player = new Player(playControl, this, 1.0)
      player.start()
      val reader = new Reader(playControl, initialFrom.getOrElse(1), initialFrom.getOrElse(1),
        initialTo.getOrElse(track.measures.length))
      reader.start()

      private val tempoListeners = new mutable.HashSet[TempoListener]()

      override def isPlaying: Boolean = playControl.isPlaying

      override def play(): Unit = {
        playControl.play()
      }

      override def stop(): Unit = {
        if (playControl.isPlaying) {
          signalStop()
        }
      }

      private def signalStop(): Unit = {
        playControl.stop()
        reader.interrupt()
        player.interrupt()
        synthesizerPort.panic()
      }

      override def quit(): Unit = {
        signalStop()
      }

      override def mute(channel: EngineChannel): Engine = {
        player.mute(channel)
        this
      }

      override def unMute(channel: EngineChannel): Engine = {
        player.unMute(channel)
        this
      }

      override def isMuted(channel: EngineChannel): Boolean = player.isMuted(channel)

      override def addTempoListener(listener: TempoListener): Unit = tempoListeners.add(listener)

      override def tempoChanged(): Unit =
        tempoListeners.foreach(_.apply(TempoEvent(player.currentTempo, player.tempoMultiplier, player.currentAdjustedTempo)))

      override def updateTempoMultiplier(f: Double => Double): Unit = {
        player.tempoMultiplier = math.min(math.max(f(player.tempoMultiplier), 0.1), 3.0)
        tempoChanged()
      }

      override def jumpToBar(f: Int => Int): Unit = {
        stop()
        reader.measureCursor = math.min(math.max(f(reader.measureCursor), 1), track.measures.length)
        play()
      }
    }
  }
}

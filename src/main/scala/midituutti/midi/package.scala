package midituutti

import java.io.File

import javax.sound.midi.{MidiSystem, Receiver, Sequence, MidiMessage => JavaMidiMessage}

/**
  * Thin Scala wrappers for the Java MidiSystem.
  */
package object midi {

  class Tempo(val bpm: Double) extends AnyVal

  object Tempo {
    def apply(bpm: Double): Tempo = new Tempo(bpm)
  }

  class Tick(val tick: Long) extends AnyVal {
    def -(other: Tick): Tick = {
      new Tick(tick - other.tick)
    }

    override def toString: String = s"Tick($tick)"
  }

  class OutputTimestamp private(val asMicros: Long) extends AnyVal {
    def nonNil: Boolean = asMicros > 0
    def asMillis: Long = asMicros / 1000
    def millisPart: Long = (asMicros / 1000.0).toLong
    def nanosPart: Int = ((asMicros - millisPart * 1000) * 1000).intValue()
  }

  object OutputTimestamp {
    def ofTickAndTempo(tick: Tick, resolution: Int, tempo: Tempo): OutputTimestamp = {
      val ticksPerSecond = resolution * (tempo.bpm / 60.0)
      val tickSize = 1.0 / ticksPerSecond
      ofMicros(Math.round(tick.tick * tickSize * 1000 * 1000))
    }

    def ofMicros(micros: Long): OutputTimestamp = {
      new OutputTimestamp(micros)
    }
  }

  abstract class MidiMessage() {
    def toJava: JavaMidiMessage
  }

  private class JavaWrapperMessage(val message: JavaMidiMessage) extends MidiMessage {
    override def toJava: JavaMidiMessage = message
  }

  class MidiEvent(val ticks: Tick, val message: MidiMessage)

  class MidiPort(private val receiver: Receiver) {
    def send(message: MidiMessage): Unit = {
      receiver.send(message.toJava, -1)
    }
  }

  class MidiFile(private val seq: Sequence) {
    def resolution: Int = seq.getResolution

    /**
      * Note: only single track sequences are supported
      */
    def track: Seq[MidiEvent] = {
      val track = seq.getTracks.apply(0)
      for (eventI <- 0 until track.size)
        yield new MidiEvent(new Tick(track.get(eventI).getTick), new JavaWrapperMessage(track.get(eventI).getMessage))
    }
  }

  def openFile(path: String): MidiFile = {
    new MidiFile(MidiSystem.getSequence(new File(path)))
  }

  def createDefaultSynthesizerPort: MidiPort = {
    val synthesizer = MidiSystem.getSynthesizer
    synthesizer.open()

    val receiver = synthesizer.getReceiver
    new MidiPort(receiver)
  }
}

package midituutti

import java.io.{File, InputStream}

import javax.sound.midi.{MidiSystem, Receiver, Sequence, MetaMessage => JavaMetaMessage, MidiMessage => JavaMidiMessage}
import midituutti.midi.MessageDecoder.{Accessors, MetaAccessor}
import midituutti.midi.MetaType.MetaType

import scala.language.implicitConversions

/**
  * Thin Scala wrappers for the Java MidiSystem.
  */
package object midi {

  class Tempo(val bpm: Double) extends AnyVal

  object Tempo {
    def apply(bpm: Double): Tempo = new Tempo(bpm)
  }

  class Tick(val tick: Long) extends AnyVal {
    def <(other: Tick): Boolean = tick < other.tick

    def -(other: Tick): Tick = {
      new Tick(tick - other.tick)
    }

    def +(other: Tick): Tick = {
      new Tick(tick + other.tick)
    }

    override def toString: String = s"Tick($tick)"
  }

  object Tick {
    def apply(tick: Long): Tick = new Tick(tick)
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

  object MetaType extends Enumeration {

    protected case class Val(label: String, accessor: MetaAccessor[_]) extends super.Val

    implicit def valueToMetaTypeVal(x: Value): Val = x.asInstanceOf[Val]

    type MetaType = Val

    val TimeSignature: MetaType = Val("time-signature", Accessors.timeSignatureAccessor)
    val NotSupported: MetaType = Val("not-supported", Accessors.noneAccessor)
  }

  abstract class MidiMessage() {
    def toJava: JavaMidiMessage

    def isMeta: Boolean

    def metaType: Option[MetaType]

    def get[T](accessor: MetaAccessor[T]): T = {
      accessor.get(this)
    }

    override def toString: String =
      s"MidiMessage(meta=$isMeta, metaType=$metaType, value=${metaType.map(t => get(t.accessor))}"
  }

  private class JavaWrapperMessage(val message: JavaMidiMessage) extends MidiMessage {
    override def toJava: JavaMidiMessage = message

    override def isMeta: Boolean = message.isInstanceOf[JavaMetaMessage]

    override def metaType: Option[MetaType] =
      message match {
        case metaMessage: JavaMetaMessage => Some(MessageDecoder.metaTypeOf(metaMessage))
        case _ => None
      }
  }

  class MidiEvent(val ticks: Tick, val message: MidiMessage) {
    override def toString: String = s"MidiEvent($ticks, $message)"
  }

  class MidiPort(private val receiver: Receiver) {
    def send(message: MidiMessage): Unit = {
      receiver.send(message.toJava, -1)
    }
  }

  class MidiFile(private val seq: Sequence) {
    def ticksPerBeat: Int = seq.getResolution

    /**
      * Note: only single track sequences are supported
      */
    def track: Seq[MidiEvent] = {
      val track = seq.getTracks.apply(0)
      for (eventI <- 0 until track.size)
        yield new MidiEvent(new Tick(track.get(eventI).getTick), new JavaWrapperMessage(track.get(eventI).getMessage))
    }
  }

  def openFile(path: String): MidiFile = openFile(new File(path))

  def openFile(file: File): MidiFile = new MidiFile(MidiSystem.getSequence(file))

  def openFile(is: InputStream): MidiFile = new MidiFile(MidiSystem.getSequence(is))

  def createDefaultSynthesizerPort: MidiPort = {
    val synthesizer = MidiSystem.getSynthesizer
    synthesizer.open()

    val receiver = synthesizer.getReceiver
    new MidiPort(receiver)
  }
}
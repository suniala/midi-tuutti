package midituutti.midi

import javax.sound.midi.{MetaMessage => JavaMetaMessage, ShortMessage => JavaShortMessage}
import midituutti.midi.MessageDecoder.OnOff.OnOff
import midituutti.midi.MetaType.MetaType

object MessageDecoder {

  case class TimeSignature(numerator: Int, denominator: Int)

  object OnOff extends Enumeration {
    type OnOff = Value
    val On: OnOff = Value
    val Off: OnOff = Value
  }

  case class Note(onOff: OnOff, channel: Int, note: Int, velocity: Int)

  trait MetaAccessor[T] {
    def get(message: MidiMessage): T = getMeta(message.toJava.asInstanceOf[JavaMetaMessage])

    def getMeta(message: JavaMetaMessage): T
  }

  trait ShortAccessor[T] {
    def get(message: MidiMessage): T = getShort(message.toJava.asInstanceOf[JavaShortMessage])

    def getShort(message: JavaShortMessage): T
  }

  /**
    * TODO: might would make sense for these to return Options
    */
  object Accessors {
    val noneAccessor: MetaAccessor[Option[Nothing]] = (_: JavaMetaMessage) => None
    val tempoAccessor: MetaAccessor[Tempo] = (message: JavaMetaMessage) => {
      val data = message.getData
      val midiTempo = ((data(0) & 0xFF) << 16) | ((data(1) & 0xFF) << 8) | (data(2) & 0xFF)
      // Don't know if this check is necessary but I saw this solution somewhere.
      val bpm = if (midiTempo <= 0) 0.1 else 60000000.0 / midiTempo
      Tempo(bpm)
    }
    val timeSignatureAccessor: MetaAccessor[TimeSignature] = (message: JavaMetaMessage) => {
      val abData = message.getData
      TimeSignature(abData(0) & 0xFF, 1 << (abData(1) & 0xFF))
    }
    val noteAccessor: ShortAccessor[Note] = (message: JavaShortMessage) => {
      val onOff = message.getCommand match {
        case JavaShortMessage.NOTE_ON => OnOff.On
        case JavaShortMessage.NOTE_OFF => OnOff.Off
        case _ => throw new IllegalArgumentException
      }
      Note(onOff, message.getChannel + 1, message.getData1, message.getData2)
    }
  }

  def metaTypeOf(message: JavaMetaMessage): MetaType = {
    message.getType match {
      case 0x51 => MetaType.Tempo
      case 0x58 => MetaType.TimeSignature
      case _ => MetaType.NotSupported
    }
  }
}

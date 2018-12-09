package midituutti.midi

import javax.sound.midi.{MetaMessage => JavaMetaMessage}
import midituutti.midi.MetaType.MetaType

object MessageDecoder {

  case class TimeSignature(numerator: Int, denominator: Int)

  trait MetaAccessor[T] {
    def get(message: MidiMessage): T = getMeta(message.toJava.asInstanceOf[JavaMetaMessage])

    def getMeta(message: JavaMetaMessage): T
  }

  /**
    * TODO: might would make sense for these to return Options
    */
  object Accessors {
    val noneAccessor: MetaAccessor[Option[Nothing]] = (_: JavaMetaMessage) => None
    val timeSignatureAccessor: MetaAccessor[TimeSignature] = (message: JavaMetaMessage) => {
      val abData = message.getData
      TimeSignature(abData(0) & 0xFF, 1 << (abData(1) & 0xFF))
    }
  }

  def metaTypeOf(message: JavaMetaMessage): MetaType = {
    message.getType match {
      case 0x58 => MetaType.TimeSignature
      case _ => MetaType.NotSupported
    }
  }
}

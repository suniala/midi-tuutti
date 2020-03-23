package midituutti.midi

import javax.sound.midi.MetaMessage as JavaMetaMessage
import javax.sound.midi.ShortMessage as JavaShortMessage

/**
 * Convenience function for bitwise and between a Byte and an Int
 */
private infix fun Byte.and(other: Int): Int = this.toInt().and(other)

data class TimeSignature(val numerator: Int, val denominator: Int)

enum class OnOff {
    On, Off
}

data class Note(val onOff: OnOff, val channel: Int, val note: Int, val velocity: Int)

interface MetaAccessor<T> {
    fun get(message: MidiMessage): T = getMeta(message.toJava() as JavaMetaMessage)

    fun getMeta(message: JavaMetaMessage): T
}

interface ShortAccessor<T> {
    fun get(message: MidiMessage): T = getShort(message.toJava() as JavaShortMessage)

    fun getShort(message: JavaShortMessage): T
}

/**
 * TODO: might would make sense for these to return nullables
 */
object Accessors {
    val noneAccessor: MetaAccessor<Nothing?> = object : MetaAccessor<Nothing?> {
        override fun getMeta(message: JavaMetaMessage): Nothing? {
            return null
        }
    }
    val tempoAccessor: MetaAccessor<Tempo> = object : MetaAccessor<Tempo> {
        override fun getMeta(message: JavaMetaMessage): Tempo {
            val data = message.data
            val midiTempo = ((data[0] and 0xFF) shl 16) or ((data[1] and 0xFF) shl 8) or (data[2] and 0xFF)
            // Don't know if this check is necessary but I saw this solution somewhere.
            val bpm = if (midiTempo <= 0) 0.1 else 60000000.0 / midiTempo
            return Tempo(bpm)
        }
    }
    val timeSignatureAccessor: MetaAccessor<TimeSignature> = object : MetaAccessor<TimeSignature> {
        override fun getMeta(message: JavaMetaMessage): TimeSignature {
            val data = message.data
            return TimeSignature(data[0] and 0xFF, 1 shl (data[1] and 0xFF))
        }
    }
    val noteAccessor: ShortAccessor<Note> = object : ShortAccessor<Note> {
        override fun getShort(message: JavaShortMessage): Note {
            val onOff = when (message.command) {
                JavaShortMessage.NOTE_ON -> OnOff.On
                JavaShortMessage.NOTE_OFF -> OnOff.Off
                else -> throw IllegalArgumentException()
            }
            return Note(onOff, message.channel + 1, message.data1, message.data2)
        }
    }
}

fun metaTypeOf(message: JavaMetaMessage): MetaType {
    return when (message.type) {
        0x51 -> MetaType.Tempo
        0x58 -> MetaType.TimeSignature
        else -> MetaType.NotSupported
    }
}

package midituutti.midi

import java.io.File
import java.io.InputStream
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import kotlin.math.roundToLong
import javax.sound.midi.MetaMessage as JavaMetaMessage
import javax.sound.midi.MidiMessage as JavaMidiMessage
import javax.sound.midi.Sequence as MidiSequence
import javax.sound.midi.ShortMessage as JavaShortMessage

/**
 * Thin Scala wrappers for the Java MidiSystem.
 */

class Tempo(val bpm: Double) {
    operator fun times(other: Double): Tempo = Tempo(bpm * other)
}

data class Tick(val tick: Long) : Comparable<Tick> {
    override fun compareTo(other: Tick): Int = tick.compareTo(other.tick)

    operator fun minus(other: Tick): Tick {
        return Tick(tick - other.tick)
    }

    operator fun plus(other: Tick): Tick {
        return Tick(tick + other.tick)
    }

    override fun toString(): String {
        return "Tick($tick)"
    }
}

class OutputTimestamp private constructor(private val asMicros: Long) {
    fun nonNil(): Boolean = asMicros > 0

    fun millisPart(): Long = (asMicros / 1000.0).toLong()

    fun nanosPart(): Int = ((asMicros - millisPart() * 1000) * 1000).toInt()

    companion object {
        fun ofTickAndTempo(tick: Tick, resolution: Int, tempo: Tempo): OutputTimestamp {
            val ticksPerSecond = resolution * (tempo.bpm / 60.0)
            val tickSize = 1.0 / ticksPerSecond
            return ofMicros((tick.tick * tickSize * 1000 * 1000).roundToLong())
        }

        private fun ofMicros(micros: Long): OutputTimestamp {
            return OutputTimestamp(micros)
        }
    }
}

// TODO: kotlin, note that accessor was dropped
enum class MetaType {
    Tempo {
        override val label: String
            get() = "tempo"
    },
    TimeSignature {
        override val label: String
            get() = "time-signature"
    },
    NotSupported {
        override val label: String
            get() = "not-supported"
    };

    abstract val label: String
}

sealed class MidiMessage {
    abstract fun ticks(): Tick

    abstract fun toJava(): JavaMidiMessage

    abstract fun isMeta(): Boolean

    abstract fun isNote(): Boolean

    abstract fun metaType(): MetaType?

    fun <T> get(accessor: MetaAccessor<T>): T = accessor.get(this)

    override fun toString(): String =
            "MidiMessage(meta=${isMeta()}, metaType=${metaType()}, " // TODO: kotlin value=${metaType()?.let { t -> get(t.accessor) }}"
}

data class NoteMessage(val ticks: Tick, val note: Note) : MidiMessage() {
    override fun ticks(): Tick = ticks

    override fun toJava(): JavaMidiMessage = JavaShortMessage(
            when (note.onOff) {
                OnOff.On -> JavaShortMessage.NOTE_ON
                else -> JavaShortMessage.NOTE_OFF
            },
            note.channel - 1,
            note.note,
            note.velocity
    )

    override fun isMeta(): Boolean = false

    override fun isNote(): Boolean = true

    override fun metaType(): MetaType? = null
}

private class JavaWrapperMessage(val ticks: Tick, val message: JavaMidiMessage) : MidiMessage() {
    override fun ticks(): Tick = ticks

    override fun toJava(): JavaMidiMessage = message

    override fun isMeta(): Boolean = message is JavaMetaMessage

    fun commandIn(jsm: JavaShortMessage, vararg commands: Int): Boolean = commands.contains(jsm.command)

    override fun isNote(): Boolean = message is JavaShortMessage &&
            commandIn(message, JavaShortMessage.NOTE_OFF, JavaShortMessage.NOTE_ON)

    override fun metaType(): MetaType? =
            when (message) {
                is JavaMetaMessage -> metaTypeOf(message)
                else -> null
            }
}

class MidiPort(private val receiver: Receiver) {
    @Suppress("LocalVariableName")
    fun panic() {
        val CONTROL_ALL_SOUND_OFF = 0x78
        for (channel in 0 until 16) {
            receiver.send(JavaShortMessage(JavaShortMessage.CONTROL_CHANGE.or(channel), CONTROL_ALL_SOUND_OFF, 0), -1)
        }
    }

    fun send(message: MidiMessage) {
        receiver.send(message.toJava(), -1)
    }
}

class MidiFile(private val seq: MidiSequence) {
    fun ticksPerBeat(): Int = seq.resolution

    fun messages(): List<MidiMessage> =
            seq.tracks
                    .flatMap { track ->
                        sequence {
                            for (eventI in 0 until track.size()) {
                                yield(JavaWrapperMessage(Tick(track.get(eventI).tick), track.get(eventI).message))
                            }
                        }.toList()
                    }
                    .sortedBy { m -> m.ticks }
}

fun openFile(path: String): MidiFile = openFile(File(path))

fun openFile(file: File): MidiFile = MidiFile(MidiSystem.getSequence(file))

fun openFile(stream: InputStream): MidiFile = MidiFile(MidiSystem.getSequence(stream))

fun createDefaultSynthesizerPort(): MidiPort {
    val synthesizer = MidiSystem.getSynthesizer()
    synthesizer.open()

    val receiver = synthesizer.receiver
    return MidiPort(receiver)
}

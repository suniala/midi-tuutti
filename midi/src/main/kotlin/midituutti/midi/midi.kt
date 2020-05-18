package midituutti.midi

import fi.kapsi.kosmik.javamididecoder.MidiDecoder
import fi.kapsi.kosmik.javamididecoder.MidiM
import fi.kapsi.kosmik.javamididecoder.MidiMetaM.MidiTempoM
import fi.kapsi.kosmik.javamididecoder.MidiMetaM.MidiTimeSignatureM
import fi.kapsi.kosmik.javamididecoder.MidiShortM.MidiNoteM
import java.io.File
import java.io.InputStream
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.microseconds
import javax.sound.midi.MidiMessage as JavaMidiMessage
import javax.sound.midi.Sequence as MidiSequence
import javax.sound.midi.ShortMessage as JavaShortMessage

/**
 * Thin wrappers for the Java MidiSystem.
 */

enum class OnOff {
    On, Off
}

data class Note(val onOff: OnOff, val channel: Int, val note: Int, val velocity: Int)

data class TimeSignature(val numerator: Int, val denominator: Int)

class Tempo(val bpm: Double) {
    operator fun times(other: Double): Tempo = Tempo(bpm * other)
    operator fun plus(other: Double): Tempo? = Tempo(bpm + other)
    operator fun minus(other: Double): Tempo? = Tempo(bpm - other)
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
        return "$tick"
    }

    @ExperimentalTime
    fun toDuration(resolution: Int, tempo: Tempo): Duration {
        val ticksPerSecond = resolution * (tempo.bpm / 60.0)
        val tickSize = 1.0 / ticksPerSecond
        return (tick * tickSize * 1000 * 1000).microseconds
    }
}

sealed class MidiMessage(private val ticks: Tick) {
    fun ticks(): Tick = ticks

    abstract fun toJava(): JavaMidiMessage
}

class NoteMessage(ticks: Tick, private val original: MidiNoteM) : MidiMessage(ticks) {
    override fun toJava(): JavaMidiMessage = original.rawMessage

    fun note(): Note = Note(if (original.isOn) OnOff.On else OnOff.Off,
            original.channel, original.note, original.velocity)

    companion object {
        fun fromNote(ticks: Tick, note: Note): NoteMessage {
            val javaMidiMessage = JavaShortMessage(
                    when (note.onOff) {
                        OnOff.On -> JavaShortMessage.NOTE_ON
                        else -> JavaShortMessage.NOTE_OFF
                    },
                    note.channel - 1,
                    note.note,
                    note.velocity
            )
            return NoteMessage(ticks, MidiDecoder.decodeMessage(javaMidiMessage) as MidiNoteM)
        }
    }
}

class TempoMessage(ticks: Tick, private val original: MidiTempoM) : MidiMessage(ticks) {
    override fun toJava(): JavaMidiMessage = original.rawMessage

    fun tempo(): Tempo = Tempo(original.bpm.toDouble())
}

class TimeSignatureMessage(ticks: Tick, private val original: MidiTimeSignatureM) : MidiMessage(ticks) {
    override fun toJava(): JavaMidiMessage = original.rawMessage

    fun timeSignature(): TimeSignature = TimeSignature(original.timeSignature.beats, original.timeSignature.unit)
}

class UnspecifiedMessage(ticks: Tick, private val original: MidiM<*>) : MidiMessage(ticks) {
    override fun toJava(): JavaMidiMessage = original.rawMessage
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

    val messages: List<MidiMessage> by lazy {
        seq.tracks
                .flatMap { track ->
                    sequence {
                        for (eventI in 0 until track.size()) {
                            val message = track.get(eventI)
                            val ticks = Tick(message.tick)
                            val dm = MidiDecoder.decodeMessage(message.message)
                            yield(when (dm) {
                                is MidiNoteM -> NoteMessage(ticks, dm)
                                is MidiTempoM -> TempoMessage(ticks, dm)
                                is MidiTimeSignatureM -> TimeSignatureMessage(ticks, dm)
                                else -> UnspecifiedMessage(ticks, dm)
                            })
                        }
                    }.toList()
                }
                .sortedBy { m -> m.ticks() }
    }
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

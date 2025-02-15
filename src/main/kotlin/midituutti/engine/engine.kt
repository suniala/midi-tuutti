package midituutti.engine

import midituutti.midi.MidiFile
import midituutti.midi.MidiMessage
import midituutti.midi.MidiPort
import midituutti.midi.Note
import midituutti.midi.NoteMessage
import midituutti.midi.OnOff
import midituutti.midi.Tempo
import midituutti.midi.TempoMessage
import midituutti.midi.Tick
import midituutti.midi.TimeSignature
import midituutti.midi.createDefaultSynthesizerPort
import midituutti.midi.openFile
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ExperimentalTime
fun Duration.millisPart(): Long = this.toLongMilliseconds()

@ExperimentalTime
fun Duration.nanosPart(): Int = (this.toLongNanoseconds() - (this.millisPart() * 1000 * 1000)).toInt()

sealed class EngineEvent {
    abstract fun ticks(): Tick
}

data class MessageEvent(val message: MidiMessage) : EngineEvent() {
    override fun ticks(): Tick = message.ticks()
}

enum class ClickType {
    One, Quarter, Eight
}

data class ClickEvent(private val ticks: Tick, val click: ClickType) : EngineEvent() {
    override fun ticks(): Tick = ticks
}

sealed class EngineTrack
data class MidiTrack(val channel: Int) : EngineTrack()
object ClickTrack : EngineTrack() {
    override fun toString(): String = "ClickTrack"
}

sealed class PlaybackEvent
data class PlayEvent(val playing: Boolean) : PlaybackEvent()
data class MeasurePlaybackEvent(val measure: Int, val timeSignature: TimeSignature) : PlaybackEvent()
data class TempoEvent(val tempo: Tempo, val adjustedTempo: Tempo) : PlaybackEvent()

typealias PlaybackListener = (PlaybackEvent) -> Unit

data class MixerChannel(val track: EngineTrack, val volumeAdjustment: Double, val muted: Boolean, val solo: Boolean)

interface Player {
    val song: SongStructure

    fun updateMixer(state: Map<EngineTrack, Double>)

    fun isPlaying(): Boolean

    fun play()

    fun stop()

    fun quit()

    fun addPlaybackListener(listener: PlaybackListener)

    fun setTempoModifier(f: (Tempo) -> Tempo)

    fun jumpToBar(f: (Int) -> Int)

    fun resetMeasureRange(start: Int, end: Int)
}

private interface PlayerListener {
    fun tempoChanged()
    fun atMeasureStart(measure: Int, timeSignature: TimeSignature)
}

@ExperimentalTime
private class MidiPlayer(
    val song: SongStructure,
    var tempoModifier: (Tempo) -> Tempo,
    val midiFile: MidiFile,
    val synthesizerPort: MidiPort
) : Thread() {
    val playerListeners = mutableListOf<PlayerListener>()

    init {
        isDaemon = true
    }

    fun addPlayerListener(playerListener: PlayerListener) {
        playerListeners.add(playerListener)
    }

    private var tempo: Tempo = song.measures.first().initialTempo

    private var playing = false

    private val waitLock = Object()

    private var currentMeasure = 1

    private var start: Int = 1

    private var end: Int? = null

    private var mixerState: Map<EngineTrack, Double> = ((1..16).map { MidiTrack(it) } + ClickTrack)
        .map {
            Pair(it, 1.0)
        }
        .toMap()

    fun currentTempo(): Tempo = tempo

    fun currentAdjustedTempo(): Tempo = tempoModifier(tempo)

    override fun run() {
        while (true) {
            waitForPlay()
            var startFrom = currentMeasure

            println("player: playing")

            try {
                while (playing) {
                    val playStartMark: TimeMark = TimeSource.Monotonic.markNow()
                    var afterJump = true
                    var prevTicks: Tick? = null
                    var prevChunkCalculatedTs = Duration.ZERO

                    song.measures.asSequence().drop(startFrom - 1).take(end as Int - startFrom + 1).forEach { measure ->
                        println("player: at ${measure.number}")
                        currentMeasure = measure.number
                        tempo = measure.initialTempo
                        playerListeners.forEach { pl ->
                            pl.atMeasureStart(measure.number, measure.timeSignature)
                            pl.tempoChanged()
                        }

                        measure.chunked(includeAdjustments = afterJump).forEach { (ticks, events) ->
                            if (playing) {
                                val ticksDelta = ticks - (prevTicks ?: ticks)
                                val timestampDelta =
                                    tempo.let { t -> ticksDelta.toDuration(midiFile.ticksPerBeat(), tempoModifier(t)) }
                                val chunkCalculatedTs = prevChunkCalculatedTs + timestampDelta

                                (chunkCalculatedTs - playStartMark.elapsedNow()).let { timeToEventCalculatedNs ->
                                    if (timeToEventCalculatedNs.isPositive()) {
                                        sleep(timeToEventCalculatedNs.millisPart(), timeToEventCalculatedNs.nanosPart())
                                    }
                                }

                                for (event in events) {
                                    val eventMidiMessage: MidiMessage = handleEvent(event)

                                    EngineTraceLogger.trace(
                                        playStartMark, chunkCalculatedTs, ticks,
                                        if (event == events.first()) timestampDelta else Duration.ZERO,
                                        eventMidiMessage, measure.number
                                    )
                                }

                                prevTicks = ticks
                                prevChunkCalculatedTs = chunkCalculatedTs
                            }
                        }
                        afterJump = false
                    }

                    // Start from beginning again
                    startFrom = start
                }
            } catch (e: InterruptedException) {
                println("player: stop playing")
            }
        }
    }

    private fun handleEvent(event: EngineEvent): MidiMessage {
        when (event) {
            is MessageEvent ->
                when (event.message) {
                    is TempoMessage -> {
                        tempo = event.message.tempo()
                        playerListeners.forEach { pl -> pl.tempoChanged() }
                    }

                    else -> {
                    }
                }

            else -> {
            }
        }

        val midiMessage: MidiMessage = when (event) {
            is MessageEvent -> event.message
            is ClickEvent -> with(event) {
                when (click) {
                    ClickType.One -> NoteMessage.fromNote(ticks(), Note(OnOff.On, 10, 31, 100))
                    ClickType.Quarter -> NoteMessage.fromNote(ticks(), Note(OnOff.On, 10, 77, 100))
                    ClickType.Eight -> NoteMessage.fromNote(ticks(), Note(OnOff.On, 10, 75, 100))
                }
            }
        }

        // Some midi programs use a "note on" message with velocity 0 instead of "note off" messages. Let's not
        // adjust the volume of such velocity 0 notes.
        if (midiMessage is NoteMessage && midiMessage.note().onOff == OnOff.On && midiMessage.note().velocity > 0) {
            val track = when (event) {
                is MessageEvent -> MidiTrack(midiMessage.note().channel)
                is ClickEvent -> ClickTrack
            }
            synthesizerPort.send(
                NoteMessage.fromNote(
                    midiMessage.ticks(),
                    midiMessage.note().copy(
                        velocity = (midiMessage.note().velocity * mixerState.getOrDefault(
                            track,
                            1.0
                        )).roundToInt()
                    )
                )
            )
        } else {
            // Let other messages pass as is.
            synthesizerPort.send(midiMessage)
        }

        return midiMessage
    }

    fun isPlaying(): Boolean = playing

    fun play(): Unit = synchronized(waitLock) {
        val before = playing
        playing = true
        if (!before) waitLock.notifyAll()
    }

    fun stopPlaying() {
        playing = false
        interrupt()
        synthesizerPort.panic()
    }

    fun setCurrentMeasure(measure: Int) {
        currentMeasure = minOf(maxOf(measure, start), end as Int)
    }

    fun currentMeasure(): Int = currentMeasure

    fun resetMeasureRange(newStart: Int, newEnd: Int) {
        assert(newEnd in 1..song.measures.last().number)
        assert(newStart in 1..newEnd)

        start = newStart
        end = newEnd
        setCurrentMeasure(start)
    }

    private fun waitForPlay(): Unit = synchronized(waitLock) {
        while (!playing) {
            try {
                waitLock.wait()
            } catch (e: InterruptedException) {
                // ignore, probably we are quitting the app
            }
        }
    }

    fun updateMixer(state: Map<EngineTrack, Double>) {
        mixerState = mixerState.entries
            .map { e ->
                Pair(e.key, state.getOrDefault(e.key, e.value))
            }
            .toMap()
    }
}

@ExperimentalTime
private class PlayerControl(val midiPlayer: MidiPlayer) : Player, PlayerListener {
    private val playbackListeners = mutableSetOf<PlaybackListener>()

    override val song: SongStructure = midiPlayer.song

    override fun updateMixer(state: Map<EngineTrack, Double>) {
        midiPlayer.updateMixer(state)
    }

    override fun isPlaying(): Boolean = midiPlayer.isPlaying()

    override fun play() {
        EngineTraceLogger.start()
        midiPlayer.play()
        notify(PlayEvent(true))
    }

    override fun stop() {
        EngineTraceLogger.stop()
        if (midiPlayer.isPlaying()) {
            signalStop()
        }
    }

    private fun signalStop() {
        midiPlayer.stopPlaying()
        notify(PlayEvent(false))
    }

    override fun quit() {
        signalStop()
    }

    override fun addPlaybackListener(listener: PlaybackListener) {
        playbackListeners.add(listener)
    }

    override fun tempoChanged(): Unit =
        TempoEvent(midiPlayer.currentTempo(), midiPlayer.currentAdjustedTempo()).let { event ->
            playbackListeners.forEach { listener -> listener(event) }
        }

    override fun atMeasureStart(measure: Int, timeSignature: TimeSignature) {
        playbackListeners.forEach { listener -> listener(MeasurePlaybackEvent(measure, timeSignature)) }
    }

    override fun setTempoModifier(f: (Tempo) -> Tempo) {
        midiPlayer.tempoModifier = f
        tempoChanged()
    }

    override fun jumpToBar(f: (Int) -> Int) {
        stop()
        midiPlayer.setCurrentMeasure(f(midiPlayer.currentMeasure()))
        play()
    }

    override fun resetMeasureRange(start: Int, end: Int) {
        val wasPlaying = isPlaying()
        stop()
        midiPlayer.resetMeasureRange(start, end)
        if (wasPlaying) play()
    }

    private fun notify(event: PlaybackEvent) = playbackListeners.forEach { pl -> pl(event) }
}

data class PlayerInitialState(val player: Player)

fun noOpTempoModifier(tempo: Tempo) = tempo

object PlaybackEngine {
    private var synthesizerPort: MidiPort? = null

    fun initialize() {
        synthesizerPort = createDefaultSynthesizerPort()
    }

    @ExperimentalTime
    fun createPlayer(filePath: String, initialFrom: Int?, initialTo: Int?): PlayerInitialState {
        val midiFile = openFile(filePath)
        val song = SongStructure.withClick(midiFile)

        val midiPlayer = MidiPlayer(song, ::noOpTempoModifier, midiFile, synthesizerPort!!)
        midiPlayer.resetMeasureRange(initialFrom ?: 1, initialTo ?: song.measures.size)
        midiPlayer.start()

        val playerControl = PlayerControl(midiPlayer)
        midiPlayer.addPlayerListener(playerControl)

        return PlayerInitialState(playerControl)
    }
}

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
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ExperimentalTime
fun Duration.millisPart(): Long = this.toLongMilliseconds()

@ExperimentalTime
fun Duration.nanosPart(): Int = (this.toLongNanoseconds() - (this.millisPart() * 1000 * 1000)).toInt()

private fun muteOrPass(mutedTracks: Set<EngineTrack>, message: MidiMessage): MidiMessage =
        when (message) {
            is NoteMessage -> {
                if (mutedTracks.any { t: EngineTrack ->
                            when (t) {
                                is MidiTrack -> t.channel == message.note().channel
                                else -> false
                            }
                        }) {
                    NoteMessage.fromNote(message.ticks(), message.note().copy(velocity = 0))
                } else {
                    message
                }
            }
            else -> message
        }

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

interface EngineTrack

data class MidiTrack(val channel: Int) : EngineTrack

sealed class PlaybackEvent
data class PlayEvent(val playing: Boolean) : PlaybackEvent()
data class MutePlaybackEvent(val track: EngineTrack, val muted: Boolean) : PlaybackEvent()
data class MeasurePlaybackEvent(val measure: Int, val timeSignature: TimeSignature) : PlaybackEvent()
data class TempoEvent(val tempo: Tempo, val adjustedTempo: Tempo) : PlaybackEvent()

typealias PlaybackListener = (PlaybackEvent) -> Unit

object ClickTrack : EngineTrack

interface Player {
    fun isPlaying(): Boolean

    fun play()

    fun stop()

    fun quit()

    fun mute(track: EngineTrack): Player

    fun unMute(track: EngineTrack): Player

    fun isMuted(track: EngineTrack): Boolean

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
private class MidiPlayer(val song: SongStructure,
                         var tempoModifier: (Tempo) -> Tempo,
                         val midiFile: MidiFile,
                         val synthesizerPort: MidiPort) : Thread() {
    val playerListeners = mutableListOf<PlayerListener>()

    init {
        isDaemon = true
    }

    fun addPlayerListener(playerListener: PlayerListener) {
        playerListeners.add(playerListener)
    }

    private val mutedTracks = mutableSetOf<EngineTrack>()

    private var tempo: Tempo = song.measures.first().initialTempo

    private var playing = false

    private val waitLock = Object()

    private var currentMeasure = 1

    private var start: Int = 1

    private var end: Int? = null

    fun mute(track: EngineTrack) {
        mutedTracks.add(track)
    }

    fun unMute(track: EngineTrack) {
        mutedTracks.remove(track)
    }

    fun isMuted(track: EngineTrack): Boolean = mutedTracks.contains(track)

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

                        measure.chunked().forEach { (ticks, events) ->
                            if (playing) {
                                val ticksDelta = ticks - (prevTicks ?: ticks)
                                val timestampDelta = tempo.let { t -> ticksDelta.toDuration(midiFile.ticksPerBeat(), tempoModifier(t)) }
                                val chunkCalculatedTs = prevChunkCalculatedTs + timestampDelta

                                (chunkCalculatedTs - playStartMark.elapsedNow()).let { timeToEventCalculatedNs ->
                                    if (timeToEventCalculatedNs.isPositive()) {
                                        sleep(timeToEventCalculatedNs.millisPart(), timeToEventCalculatedNs.nanosPart())
                                    }
                                }

                                for (event in events) {
                                    val eventMidiMessage: MidiMessage? = handleEvent(event)

                                    EngineTraceLogger.trace(playStartMark, chunkCalculatedTs, ticks,
                                            if (event == events.first()) timestampDelta else Duration.ZERO,
                                            eventMidiMessage, measure.number)
                                }

                                prevTicks = ticks
                                prevChunkCalculatedTs = chunkCalculatedTs
                            }
                        }
                    }

                    // Start from beginning again
                    startFrom = start
                }
            } catch (e: InterruptedException) {
                println("player: stop playing")
            }
        }
    }

    private fun handleEvent(event: EngineEvent): MidiMessage? {
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

        val midiMessage: MidiMessage? = when (event) {
            is MessageEvent ->
                muteOrPass(mutedTracks, event.message)
            is ClickEvent ->
                if (!mutedTracks.contains(ClickTrack)) {
                    with(event) {
                        when (click) {
                            ClickType.One -> NoteMessage.fromNote(ticks(), Note(OnOff.On, 10, 31, 100))
                            ClickType.Quarter -> NoteMessage.fromNote(ticks(), Note(OnOff.On, 10, 77, 100))
                            ClickType.Eight -> NoteMessage.fromNote(ticks(), Note(OnOff.On, 10, 75, 100))
                        }
                    }
                } else {
                    null
                }
        }
        if (midiMessage != null) {
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
}

@ExperimentalTime
private class PlayerControl(val midiPlayer: MidiPlayer) : Player, PlayerListener {
    private val playbackListeners = mutableSetOf<PlaybackListener>()

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

    override fun mute(track: EngineTrack): Player {
        midiPlayer.mute(track)
        notify(MutePlaybackEvent(track, true))
        return this
    }

    override fun unMute(track: EngineTrack): Player {
        midiPlayer.unMute(track)
        notify(MutePlaybackEvent(track, false))
        return this
    }

    override fun isMuted(track: EngineTrack): Boolean = midiPlayer.isMuted(track)

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

data class PlayerInitialState(val player: Player, val song: SongStructure)

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

        return PlayerInitialState(playerControl, song)
    }
}

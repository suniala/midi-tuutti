package midituutti.engine

import midituutti.midi.Accessors
import midituutti.midi.MetaType
import midituutti.midi.MidiFile
import midituutti.midi.MidiMessage
import midituutti.midi.MidiPort
import midituutti.midi.Note
import midituutti.midi.NoteMessage
import midituutti.midi.OnOff
import midituutti.midi.Tempo
import midituutti.midi.Tick
import midituutti.midi.TimeSignature
import midituutti.midi.createDefaultSynthesizerPort
import midituutti.midi.openFile
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ExperimentalTime
fun Duration.millisPart(): Long = this.toLongMilliseconds()

@ExperimentalTime
fun Duration.nanosPart(): Int = (this.toLongNanoseconds() - (this.millisPart() * 1000 * 1000)).toInt()

private fun muteOrPass(mutedTracks: Set<EngineTrack>, message: MidiMessage): MidiMessage =
        if (message.isNote()) {
            val note = Accessors.noteAccessor.get(message)
            if (mutedTracks.any { t: EngineTrack ->
                        when (t) {
                            is MidiTrack -> t.channel == note.channel
                            else -> false
                        }
                    }) {
                NoteMessage(message.ticks(), note.copy(velocity = 0))
            } else {
                message
            }
        } else {
            message
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

data class MeasureEvent(private val ticks: Tick, val measure: Int, val timeSignature: TimeSignature) : EngineEvent() {
    override fun ticks(): Tick = ticks
}

interface EngineTrack

data class MidiTrack(val channel: Int) : EngineTrack

sealed class PlaybackEvent
data class PlayEvent(val playing: Boolean) : PlaybackEvent()
data class MutePlaybackEvent(val track: EngineTrack, val muted: Boolean) : PlaybackEvent()
data class MeasurePlaybackEvent(val measure: Int, val timeSignature: TimeSignature) : PlaybackEvent()
data class TempoEvent(val tempo: Tempo?,
                      val adjustedTempo: Tempo?) : PlaybackEvent()

typealias PlaybackListener = (PlaybackEvent) -> Unit

object ClickTrack : EngineTrack

interface Engine {
    fun isPlaying(): Boolean

    fun play()

    fun stop()

    fun quit()

    fun mute(track: EngineTrack): Engine

    fun unMute(track: EngineTrack): Engine

    fun isMuted(track: EngineTrack): Boolean

    fun addPlaybackListener(listener: PlaybackListener)

    fun setTempoModifier(f: (Tempo) -> Tempo)

    fun jumpToBar(f: (Int) -> Int)
}

interface SingleNoteHitEngine {
    fun sendNote(note: Note)

    fun quit()
}

private class PlayControl : PlayerListener {
    private val waitLock = Object()

    private var currentMeasure = 1

    @Volatile
    private var playing = false

    fun play(): Unit = synchronized(waitLock) {
        val before = playing
        playing = true
        if (!before) waitLock.notifyAll()
    }

    fun stop() {
        playing = false
    }

    fun isPlaying(): Boolean = playing

    fun waitForPlay(): Unit = synchronized(waitLock) {
        while (!playing) {
            try {
                waitLock.wait()
            } catch (e: InterruptedException) {
                // ignore, probably we are quitting the app
            }
        }
    }

    fun currentMeasure() = currentMeasure

    fun setCurrentMeasure(measure: Int) {
        currentMeasure = measure
    }

    override fun tempoChanged() { /* no-op */
    }

    override fun atMeasureStart(measure: Int, timeSignature: TimeSignature) {
        currentMeasure = measure
    }
}

fun createSingeNoteHitEngine(): SingleNoteHitEngine {
    val synthesizerPort = createDefaultSynthesizerPort()

    return object : SingleNoteHitEngine {
        override fun sendNote(note: Note): Unit = synthesizerPort.send(NoteMessage(Tick(1), note))

        override fun quit(): Unit = synthesizerPort.panic()
    }
}

private interface PlayerListener {
    fun tempoChanged()
    fun atMeasureStart(measure: Int, timeSignature: TimeSignature)
}

private interface PlayItem
private data class SimultaneousEventsChunk(val events: List<EngineEvent>) : PlayItem {
    val ticks: Tick = events.first().ticks()
}

private object PlayReset : PlayItem

private class Reader(val playControl: PlayControl,
                     @Volatile var from: Int,
                     @Volatile var to: Int,
                     val queue: BlockingQueue<PlayItem>,
                     val song: SongStructure) : Thread() {
    init {
        isDaemon = true
    }

    override fun run() {
        while (true) {
            playControl.waitForPlay()
            println("reader: playing")
            var startFrom = playControl.currentMeasure()

            try {
                while (playControl.isPlaying()) {
                    println("reader: reading $startFrom - $to")
                    for (readerCursor in startFrom..to) {
                        val measure = song.measures[readerCursor - 1]

                        var chunkEvents = arrayListOf<EngineEvent>()
                        chunkEvents.add(MeasureEvent(measure.start, readerCursor, measure.timeSignature))

                        for (event in measure.events) {
                            if (chunkEvents.isEmpty()) {
                                chunkEvents.add(event)
                            } else {
                                if (chunkEvents.first().ticks() == event.ticks()) {
                                    chunkEvents.add(event)
                                } else {
                                    queue.put(SimultaneousEventsChunk(chunkEvents))
                                    chunkEvents = arrayListOf(event)
                                }
                            }
                        }
                        if (chunkEvents.isNotEmpty()) {
                            queue.put(SimultaneousEventsChunk(chunkEvents))
                        }
                    }

                    // Mark a "jump" between measures.
                    queue.put(PlayReset)

                    // Start from beginning again
                    startFrom = from
                }
            } catch (e: InterruptedException) {
                queue.clear()
                println("reader: stop playing")
            }
        }
    }
}

@ExperimentalTime
private class Player(val playControl: PlayControl,
                     var tempoModifier: (Tempo) -> Tempo,
                     val queue: BlockingQueue<PlayItem>,
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

    private var tempo: Tempo? = null

    fun mute(track: EngineTrack) {
        mutedTracks.add(track)
    }

    fun unMute(track: EngineTrack) {
        mutedTracks.remove(track)
    }

    fun isMuted(track: EngineTrack): Boolean = mutedTracks.contains(track)

    fun currentTempo(): Tempo? = tempo

    fun currentAdjustedTempo(): Tempo? = tempo?.let { t -> tempoModifier(t) }

    override fun run() {
        while (true) {
            playControl.waitForPlay()
            var playStartMark: TimeMark? = null
            var prevTicks: Tick? = null
            var prevChunkCalculatedTs = Duration.ZERO
            println("player: playing")

            try {
                while (playControl.isPlaying()) {
                    @Suppress("MoveVariableDeclarationIntoWhen")
                    val playItem = queue.take()

                    when (playItem) {
                        is SimultaneousEventsChunk -> {
                            playStartMark = playStartMark ?: TimeSource.Monotonic.markNow()

                            val ticksDelta = playItem.ticks - (prevTicks ?: playItem.ticks)
                            val timestampDelta = tempo?.let { t -> ticksDelta.toDuration(midiFile.ticksPerBeat(), tempoModifier(t)) }
                            val chunkCalculatedTs = prevChunkCalculatedTs + (timestampDelta ?: Duration.ZERO)

                            (chunkCalculatedTs - playStartMark.elapsedNow()).let { timeToEventCalculatedNs ->
                                if (timeToEventCalculatedNs.isPositive()) {
                                    sleep(timeToEventCalculatedNs.millisPart(), timeToEventCalculatedNs.nanosPart())
                                }
                            }

                            for (event in playItem.events) {
                                val eventMidiMessage: MidiMessage? = handleEvent(event)

                                EngineTraceLogger.trace(playStartMark, chunkCalculatedTs, playItem.ticks,
                                        if (event == playItem.events.first()) timestampDelta else Duration.ZERO,
                                        eventMidiMessage, if (event is MeasureEvent) event.measure else null)
                            }

                            prevTicks = playItem.ticks
                            prevChunkCalculatedTs = chunkCalculatedTs
                        }
                        is PlayReset -> {
                            // We have jumped over some measures, need to reset timing state.
                            playStartMark = null
                            prevTicks = null
                            prevChunkCalculatedTs = Duration.ZERO
                        }
                    }
                }
            } catch (e: InterruptedException) {
                println("player: stop playing")
            }
        }
    }

    private fun handleEvent(event: EngineEvent): MidiMessage? {
        when (event) {
            is MessageEvent ->
                if (event.message.metaType() == MetaType.Tempo) {
                    tempo = Accessors.tempoAccessor.get(event.message)
                    playerListeners.forEach { pl -> pl.tempoChanged() }
                }
            is MeasureEvent -> {
                println("player: at ${event.measure}")
                playerListeners.forEach { pl -> pl.atMeasureStart(event.measure, event.timeSignature) }
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
                            ClickType.One -> NoteMessage(ticks(), Note(OnOff.On, 10, 31, 100))
                            ClickType.Quarter -> NoteMessage(ticks(), Note(OnOff.On, 10, 77, 100))
                            ClickType.Eight -> NoteMessage(ticks(), Note(OnOff.On, 10, 75, 100))
                        }
                    }
                } else {
                    null
                }
            else -> null
        }
        if (midiMessage != null) {
            synthesizerPort.send(midiMessage)
        }

        return midiMessage
    }
}

@ExperimentalTime
private class PlayerEngine(val song: SongStructure, val playControl: PlayControl, val player: Player, val reader: Reader) : Engine, PlayerListener {
    private val playbackListeners = mutableSetOf<PlaybackListener>()

    override fun isPlaying(): Boolean = playControl.isPlaying()

    override fun play() {
        EngineTraceLogger.start()
        playControl.play()
        notify(PlayEvent(true))
    }

    override fun stop() {
        EngineTraceLogger.stop()
        if (playControl.isPlaying()) {
            signalStop()
        }
    }

    private fun signalStop() {
        playControl.stop()
        reader.interrupt()
        player.interrupt()
        player.synthesizerPort.panic()
        notify(PlayEvent(false))
    }

    override fun quit() {
        signalStop()
    }

    override fun mute(track: EngineTrack): Engine {
        player.mute(track)
        notify(MutePlaybackEvent(track, true))
        return this
    }

    override fun unMute(track: EngineTrack): Engine {
        player.unMute(track)
        notify(MutePlaybackEvent(track, false))
        return this
    }

    override fun isMuted(track: EngineTrack): Boolean = player.isMuted(track)

    override fun addPlaybackListener(listener: PlaybackListener) {
        playbackListeners.add(listener)
    }

    override fun tempoChanged(): Unit =
            TempoEvent(player.currentTempo(), player.currentAdjustedTempo()).let { event ->
                playbackListeners.forEach { listener -> listener(event) }
            }

    override fun atMeasureStart(measure: Int, timeSignature: TimeSignature) {
        playbackListeners.forEach { listener -> listener(MeasurePlaybackEvent(measure, timeSignature)) }
    }

    override fun setTempoModifier(f: (Tempo) -> Tempo) {
        player.tempoModifier = f
        tempoChanged()
    }

    override fun jumpToBar(f: (Int) -> Int) {
        stop()
        playControl.setCurrentMeasure(minOf(maxOf(f(playControl.currentMeasure()), 1), song.measures.size))
        play()
    }

    private fun notify(event: PlaybackEvent) = playbackListeners.forEach { pl -> pl(event) }
}

data class EngineInitialState(val engine: Engine, val measures: Int)

fun noOpTempoModifier(tempo: Tempo) = tempo

@ExperimentalTime
fun createEngine(filePath: String, initialFrom: Int?, initialTo: Int?): EngineInitialState {
    val synthesizerPort = createDefaultSynthesizerPort()
    val midiFile = openFile(filePath)
    val song = SongStructure.withClick(midiFile)

    val queue = LinkedBlockingQueue<PlayItem>(1000)

    val playControl = PlayControl()

    val player = Player(playControl, ::noOpTempoModifier, queue, midiFile, synthesizerPort)
    player.addPlayerListener(playControl)
    player.start()

    val reader = Reader(playControl, initialFrom ?: 1, initialTo ?: song.measures.size, queue, song)
    reader.start()

    val playerEngine = PlayerEngine(song, playControl, player, reader)
    player.addPlayerListener(playerEngine)

    return EngineInitialState(playerEngine, song.measures.size)
}




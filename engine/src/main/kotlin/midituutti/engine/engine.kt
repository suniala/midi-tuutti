package midituutti.engine

import midituutti.midi.Accessors
import midituutti.midi.MetaType
import midituutti.midi.MidiFile
import midituutti.midi.MidiMessage
import midituutti.midi.MidiPort
import midituutti.midi.Note
import midituutti.midi.NoteMessage
import midituutti.midi.OnOff
import midituutti.midi.OutputTimestamp
import midituutti.midi.Tempo
import midituutti.midi.Tick
import midituutti.midi.createDefaultSynthesizerPort
import midituutti.midi.openFile
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

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

data class MeasureEvent(private val ticks: Tick, val measure: Int) : EngineEvent() {
    override fun ticks(): Tick = ticks
}

interface EngineTrack

data class MidiTrack(val channel: Int) : EngineTrack

sealed class PlaybackEvent
data class PlayEvent(val playing: Boolean) : PlaybackEvent()
data class MutePlaybackEvent(val track: EngineTrack, val muted: Boolean) : PlaybackEvent()
data class TempoEvent(val tempo: Tempo?,
                      val multiplier: Double,
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

    fun updateTempoMultiplier(f: (Double) -> Double)

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

    override fun atMeasureStart(measure: Int) {
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
    fun atMeasureStart(measure: Int)
}

private class Reader(val playControl: PlayControl,
                     @Volatile var from: Int,
                     @Volatile var to: Int,
                     val queue: BlockingQueue<EngineEvent>,
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
                        queue.put(MeasureEvent(measure.start, readerCursor))

                        for (event in measure.events) {
                            queue.put(event)
                        }
                    }

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

private class Player(val playControl: PlayControl,
                     var tempoMultiplier: Double,
                     val queue: BlockingQueue<EngineEvent>,
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

    fun currentAdjustedTempo(): Tempo? = tempo?.let { t -> t * tempoMultiplier }

    override fun run() {
        while (true) {
            playControl.waitForPlay()
            val playStartNs = System.nanoTime()
            var prevTicks: Tick? = null
            var prevEventCalculatedNs: Long = playStartNs
            println("player: playing")

            try {
                while (playControl.isPlaying()) {
                    val event = queue.take()

                    val ticksDelta = event.ticks() - (prevTicks ?: event.ticks())
                    val timestampDelta = tempo?.let { t -> OutputTimestamp.ofTickAndTempo(ticksDelta, midiFile.ticksPerBeat(), t * tempoMultiplier) }
                    val eventCalculatedNs = prevEventCalculatedNs + (timestampDelta?.toNanos() ?: 0)

                    if (timestampDelta != null) {
                        if (timestampDelta.nonNil()) {
                            sleep(timestampDelta.millisPart(), timestampDelta.nanosPart())
                        }
                    }

                    when (event) {
                        is MessageEvent ->
                            if (event.message.metaType() == MetaType.Tempo) {
                                tempo = Accessors.tempoAccessor.get(event.message)
                                playerListeners.forEach { pl -> pl.tempoChanged() }
                            }
                        is MeasureEvent -> {
                            println("player: at ${event.measure}")
                            playerListeners.forEach { pl -> pl.atMeasureStart(event.measure) }
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

                    EngineTraceLogger.trace(playStartNs, eventCalculatedNs, event.ticks(), timestampDelta, midiMessage,
                            if (event is MeasureEvent) event.measure else null)

                    prevTicks = event.ticks()
                    prevEventCalculatedNs = eventCalculatedNs
                }
            } catch (e: InterruptedException) {
                println("player: stop playing")
            }
        }
    }
}

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
            TempoEvent(player.currentTempo(), player.tempoMultiplier, player.currentAdjustedTempo()).let { event ->
                playbackListeners.forEach { listener -> listener(event) }
            }

    override fun atMeasureStart(measure: Int) {
        /* no-op */
    }

    override fun updateTempoMultiplier(f: (Double) -> Double) {
        player.tempoMultiplier = minOf(maxOf(f(player.tempoMultiplier), 0.1), 3.0)
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

fun createEngine(filePath: String, initialFrom: Int?, initialTo: Int?): EngineInitialState {
    val synthesizerPort = createDefaultSynthesizerPort()
    val midiFile = openFile(filePath)
    val song = SongStructure.withClick(midiFile)

    val queue = LinkedBlockingQueue<EngineEvent>(1000)

    val playControl = PlayControl()

    val player = Player(playControl, 1.0, queue, midiFile, synthesizerPort)
    player.addPlayerListener(playControl)
    player.start()

    val reader = Reader(playControl, initialFrom ?: 1, initialTo ?: song.measures.size, queue, song)
    reader.start()

    val playerEngine = PlayerEngine(song, playControl, player, reader)
    player.addPlayerListener(playerEngine)

    return EngineInitialState(playerEngine, song.measures.size)
}




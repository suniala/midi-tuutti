package midituutti

import javafx.application.Platform
import midituutti.engine.ClickTrack
import midituutti.engine.EngineTrack
import midituutti.engine.MidiTrack
import midituutti.engine.MixerChannel
import midituutti.engine.PlaybackEngine
import midituutti.engine.PlaybackEvent
import midituutti.engine.Player
import midituutti.engine.SongStructure
import midituutti.midi.Tempo
import java.io.File

class PlayerController {
    private var player: Player? = null

    private var song: SongStructure? = null

    private var mixerState: Map<EngineTrack, MixerChannel> =
        (1..16)
            .map {
                MixerChannel(MidiTrack(it), 1.0, muted = false, solo = false)
            }
            .plus(
                MixerChannel(ClickTrack, 1.0, muted = true, solo = false)
            )
            .associateBy { it.track }

    private val uiEventListeners = mutableListOf<(PlaybackEvent) -> Unit>()

    fun addUiEventListener(listener: (PlaybackEvent) -> Unit) {
        uiEventListeners.add(listener)
    }

    fun load(file: File) {
        val playerInitialState = PlaybackEngine.createPlayer(file.absolutePath, null, null)

        player?.quit()
        player = playerInitialState.player

        // Initialize the engine to correspond with the mixer's initial state
        updateEngineMixer(mixerState)

        // Pass events from the player thread to the ui thread
        player().addPlaybackListener { event ->
            Platform.runLater { uiEventListeners.forEach { it(event) } }
        }

        song = playerInitialState.player.song
    }

    fun togglePlay() {
        if (player().isPlaying()) player().stop()
        else player().play()
    }

    private fun player(): Player {
        return player ?: throw IllegalStateException()
    }

    fun jump(f: (Int) -> Int) = player().jumpToBar(f)

    fun resetMeasureRange(range: Pair<Int, Int>) {
        player().resetMeasureRange(range.first, range.second)
    }

    fun setTempoModifier(f: (Tempo) -> Tempo) = player().setTempoModifier(f)

    fun song(): SongStructure {
        return song ?: throw IllegalStateException()
    }

    fun updateMixerChannel(track: EngineTrack, update: (MixerChannel) -> MixerChannel) =
        updateMixerChannel(update(mixerChannelState(track)))

    fun mixerChannelState(track: EngineTrack): MixerChannel = mixerState.getValue(track)

    private fun updateMixerChannel(mixerChannel: MixerChannel) {
        val newMixerState = mixerState.plus(Pair(mixerChannel.track, mixerChannel))
        updateEngineMixer(newMixerState)
        mixerState = newMixerState
    }

    private fun updateEngineMixer(newMixerState: Map<EngineTrack, MixerChannel>) {
        val maximumVolume = maxOf(1.0, newMixerState.values.map { s -> s.volumeAdjustment }.maxOrNull() ?: 1.0)
        val someSolo = newMixerState.values.filter { it.solo }.any()

        val trackVolumes = newMixerState.values
            .map { s ->
                with(s) {
                    val volume = if (muted || (someSolo && !solo)) 0.0 else volumeAdjustment / maximumVolume
                    Pair(track, volume)
                }
            }
            .toMap()
        player().updateMixer(trackVolumes)
    }
}

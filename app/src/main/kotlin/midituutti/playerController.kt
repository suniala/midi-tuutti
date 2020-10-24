package midituutti

import midituutti.engine.ClickTrack
import midituutti.engine.EngineTrack
import midituutti.engine.MidiTrack
import midituutti.engine.PlaybackEngine
import midituutti.engine.PlaybackEvent
import midituutti.engine.Player
import midituutti.engine.SongStructure
import midituutti.midi.Tempo
import tornadofx.*
import java.io.File
import kotlin.time.ExperimentalTime

class UiPlaybackEvent(val pe: PlaybackEvent) : FXEvent()

val drumTrack = MidiTrack(10)

@ExperimentalTime
class PlayerController : Controller() {
    private var player: Player? = null

    private var song: SongStructure? = null

    fun load(file: File) {
        val playerInitialState = PlaybackEngine.createPlayer(file.absolutePath, null, null)

        player?.quit()
        player = playerInitialState.player

        // Pass events from the player thread to the ui thread via TornadoFX EventBus
        player().addPlaybackListener(fun(event: PlaybackEvent): Unit = fire(UiPlaybackEvent(event)))

        // Propagating current button positions to the new player instance is a bit difficult so let's just
        // reset everything.
        player().mute(ClickTrack)
        player().unMute(drumTrack)

        song = playerInitialState.song
    }

    fun togglePlay() {
        if (player().isPlaying()) player().stop()
        else player().play()
    }

    fun toggleTrack(track: EngineTrack) {
        if (player().isMuted(track)) player().unMute((track))
        else player().mute(track)
    }

    fun toggleClick() {
        if (player().isMuted(ClickTrack)) player().unMute(ClickTrack)
        else player().mute(ClickTrack)
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
}

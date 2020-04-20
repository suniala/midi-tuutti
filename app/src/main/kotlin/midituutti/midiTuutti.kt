package midituutti

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.stage.FileChooser
import midituutti.engine.ClickTrack
import midituutti.engine.Engine
import midituutti.engine.EngineTrack
import midituutti.engine.MeasurePlaybackEvent
import midituutti.engine.MidiTrack
import midituutti.engine.MutePlaybackEvent
import midituutti.engine.PlayEvent
import midituutti.engine.PlaybackEvent
import midituutti.engine.TempoEvent
import midituutti.engine.createEngine
import midituutti.midi.Tempo
import tornadofx.*
import java.io.File
import kotlin.time.ExperimentalTime

val drumTrack = MidiTrack(10)

class UiPlaybackEvent(val pe: PlaybackEvent) : FXEvent()
class LoadEvent(val measures: Int) : FXEvent()

enum class TempoMode {
    CONSTANT, MULTIPLIER
}

enum class TempoAdjustment {
    DECREASE, RESET, INCREASE
}

@ExperimentalTime
class EngineController : Controller() {
    private var engine: Engine? = null

    fun openFile(file: File) {
        engine?.quit()
        val engineState = createEngine(file.absolutePath, null, null)
        engine = engineState.engine
        // Pass events from the engine thread to the ui thread via TornadoFX EventBus
        engine().addPlaybackListener(fun(event: PlaybackEvent): Unit = fire(UiPlaybackEvent(event)))

        // Propagating current button positions to the new engine instance is a bit difficult so let's just
        // reset everything.
        engine().mute(ClickTrack)
        engine().unMute(drumTrack)

        fire(LoadEvent(engineState.measures))
    }

    fun togglePlay() {
        if (engine().isPlaying()) engine().stop()
        else engine().play()
    }

    fun toggleTrack(track: EngineTrack) {
        if (engine().isMuted(track)) engine().unMute((track))
        else engine().mute(track)
    }

    fun toggleClick() {
        if (engine().isMuted(ClickTrack)) engine().unMute(ClickTrack)
        else engine().mute(ClickTrack)
    }

    private fun engine(): Engine {
        return engine ?: throw IllegalStateException()
    }

    fun jump(f: (Int) -> Int) = engine().jumpToBar(f)

    fun setTempoModifier(f: (Tempo) -> Tempo) = engine().setTempoModifier(f)
}

@ExperimentalTime
class PlayerView : View("Player") {
    val engineController: EngineController by param()
    private var playButton: ToggleButton by singleAssign()
    private var clickButton: ToggleButton by singleAssign()
    private var drumMuteButton: ToggleButton by singleAssign()
    private val songTempo = SimpleObjectProperty<Tempo?>()
    private val tempoMultiplier = SimpleObjectProperty(1.0)
    private val constantTempo = SimpleObjectProperty(Tempo(120.0))
    private val adjustedTempo = SimpleObjectProperty<Tempo?>()
    private val currentMeasure = SimpleObjectProperty<Int?>()
    private val measureCount = SimpleObjectProperty<Int?>()
    private val tempoModeGroup = ToggleGroup()
    private val tempoMode = tempoModeGroup.selectedValueProperty<TempoMode>()

    private fun updateTempoModifier(tempoMode: TempoMode, multiplier: Double, constant: Tempo) {
        when (tempoMode) {
            TempoMode.MULTIPLIER -> engineController.setTempoModifier(fun(tempo: Tempo) = tempo * multiplier)
            TempoMode.CONSTANT -> engineController.setTempoModifier(fun(_) = constant)
        }
    }

    private fun adjustCurrentTempoMode(adjustment: TempoAdjustment) {
        when (tempoMode.value as TempoMode) {
            TempoMode.MULTIPLIER -> tempoMultiplier.value = when (adjustment) {
                TempoAdjustment.DECREASE -> tempoMultiplier.value - 0.01
                TempoAdjustment.RESET -> 1.0
                TempoAdjustment.INCREASE -> tempoMultiplier.value + 0.01
            }
            TempoMode.CONSTANT -> constantTempo.value = when (adjustment) {
                TempoAdjustment.DECREASE -> constantTempo.value - 1.0
                TempoAdjustment.RESET -> Tempo(120.0)
                TempoAdjustment.INCREASE -> constantTempo.value + 1.0
            }
        }
    }

    override val root = vbox {
        // Disable until a file is opened.
        isDisable = true

        playButton = togglebutton("Play") {
            shortcut("Space") { fire() }
            isSelected = false
            isFocusTraversable = false
            action {
                engineController.togglePlay()
            }
        }
        clickButton = togglebutton {
            shortcut("C") { fire() }
            val stateText = selectedProperty().stringBinding {
                if (it == true) "Click On" else "Click Off"
            }
            textProperty().bind(stateText)
            isSelected = false
            isFocusTraversable = false
            action {
                engineController.toggleClick()
            }
        }
        drumMuteButton = togglebutton {
            shortcut("M") { fire() }
            val stateText = selectedProperty().stringBinding {
                if (it == true) "Drums Off" else "Drums On"
            }
            textProperty().bind(stateText)
            isSelected = false
            isFocusTraversable = false
            action {
                engineController.toggleTrack(drumTrack)
            }
        }
        button("<<") {
            shortcut("Home")
            isFocusTraversable = false
            action {
                engineController.jump { 0 }
            }
        }
        button("<") {
            shortcut("A")
            isFocusTraversable = false
            action {
                engineController.jump { m -> m - 1 }
            }
        }
        button(">") {
            shortcut("D")
            isFocusTraversable = false
            action {
                engineController.jump { m -> m + 1 }
            }
        }

        hbox {
            label("Playback tempo: ")
            label(adjustedTempo.stringBinding { t -> t?.bpm?.let { String.format("%.2f", it) } ?: "---" }) {
                minWidth = 50.0
                maxWidth = 50.0
            }
        }
        hbox {
            label("Song tempo: ")
            label(songTempo.stringBinding { t -> t?.bpm?.let { String.format("%.2f", it) } ?: "---" }) {
                minWidth = 50.0
                maxWidth = 50.0
            }
        }
        vbox {
            label(tempoMultiplier.stringBinding { m -> String.format("Tempo multiplier: %.2f", m) }) {
                minWidth = 200.0
                maxWidth = 200.0
            }
        }
        vbox {
            radiobutton("Multiply", tempoModeGroup, value = TempoMode.MULTIPLIER)
            radiobutton("Constant", tempoModeGroup, value = TempoMode.CONSTANT)
        }
        hbox {
            button("+") {
                shortcut("W")
                isFocusTraversable = false
                action {
                    adjustCurrentTempoMode(TempoAdjustment.INCREASE)
                }
            }
            button("o") {
                isFocusTraversable = false
                action {
                    adjustCurrentTempoMode(TempoAdjustment.RESET)
                }
            }
            button("-") {
                shortcut("S")
                isFocusTraversable = false
                action {
                    adjustCurrentTempoMode(TempoAdjustment.DECREASE)
                }
            }
        }
        hbox {
            label("Current measure: ")
            label(currentMeasure.stringBinding { c -> c?.toString() ?: "" }) {
                minWidth = 50.0
                maxWidth = 50.0
            }
        }
        hbox {
            label("Measure count: ")
            label(measureCount.stringBinding { c -> c?.toString() ?: "" }) {
                minWidth = 50.0
                maxWidth = 50.0
            }
        }

        shortcut("T") {
            tempoMode.value = when (tempoMode.value as TempoMode) {
                TempoMode.CONSTANT -> TempoMode.MULTIPLIER
                TempoMode.MULTIPLIER -> TempoMode.CONSTANT
            }
        }

        subscribe<UiPlaybackEvent> { event ->
            event.pe.let { playbackEvent ->
                when (playbackEvent) {
                    is PlayEvent -> playButton.selectedProperty().value = playbackEvent.playing
                    is MutePlaybackEvent -> when (playbackEvent.track) {
                        is ClickTrack -> clickButton.selectedProperty().value = !playbackEvent.muted
                        is MidiTrack -> when ((playbackEvent.track as MidiTrack).channel) {
                            10 -> drumMuteButton.selectedProperty().value = playbackEvent.muted
                        }
                    }
                    is MeasurePlaybackEvent -> {
                        currentMeasure.value = playbackEvent.measure
                    }
                    is TempoEvent -> {
                        songTempo.value = playbackEvent.tempo
                        adjustedTempo.value = playbackEvent.adjustedTempo
                    }
                }
            }
        }

        subscribe<LoadEvent> { event ->
            run {
                tempoMode.value = TempoMode.MULTIPLIER
                measureCount.value = event.measures
                isDisable = false
            }
        }

        tempoMultiplier.onChange { multiplier ->
            updateTempoModifier(tempoMode.value, multiplier as Double, constantTempo.value)
        }
        constantTempo.onChange { tempo ->
            updateTempoModifier(tempoMode.value, tempoMultiplier.value, tempo as Tempo)
        }
        tempoMode.onChange { mode ->
            updateTempoModifier(mode as TempoMode, tempoMultiplier.value, constantTempo.value)
        }
    }
}

@ExperimentalTime
class RootView : View("Root") {
    private val engineController = EngineController()

    private val playerView = find<PlayerView>(mapOf(PlayerView::engineController to engineController))

    override val root = borderpane() {
        top = menubar {
            menu("File") {
                item("Open", "Shortcut+O").action {
                    val fileChooser = FileChooser().apply {
                        title = "Open Midi File"
                        initialDirectory = System.getProperty("midituutti.initialDir")?.let { File(it) }
                        extensionFilters + listOf(
                                FileChooser.ExtensionFilter("Midi Files", "*.mid"),
                                FileChooser.ExtensionFilter("All Files", "*.*")
                        )
                    }
                    val selectedFile = fileChooser.showOpenDialog(primaryStage)
                    if (selectedFile != null) {
                        engineController.openFile(selectedFile)
                    }
                }
                separator()
                item("Quit")
            }
        }

        bottom = playerView.root
    }
}

@ExperimentalTime
class MidiTuuttiApp : App(RootView::class)

@ExperimentalTime
fun main(args: Array<String>) {
    // TODO kotlin: how to get command line args to the view?
    launch<MidiTuuttiApp>(args)
}
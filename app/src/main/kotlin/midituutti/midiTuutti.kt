package midituutti

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.TabPane
import javafx.scene.control.ToggleButton
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
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

val drumTrack = MidiTrack(10)

class UiPlaybackEvent(val pe: PlaybackEvent) : FXEvent()
class LoadEvent(val measures: Int) : FXEvent()

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
    private val tempoMultiplierFun = fun(tempo: Tempo): Tempo = tempo * tempoMultiplier.value
    private val constantTempoFun = fun(_: Tempo): Tempo = constantTempo.value

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
        tabpane {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tab("Multiplier") {
                whenSelected { engineController.setTempoModifier(tempoMultiplierFun) }
                vbox {
                    hbox {
                        label("Tempo multiplier: ")
                        label(tempoMultiplier.stringBinding { m -> String.format("%.2f", m) }) {
                            minWidth = 50.0
                            maxWidth = 50.0
                        }
                    }
                    hbox {
                        label("Adjusted song tempo: ")
                        label(adjustedTempo.stringBinding { t -> t?.bpm?.let { String.format("%.2f", it) } ?: "---" }) {
                            minWidth = 50.0
                            maxWidth = 50.0
                        }
                    }
                    hbox {
                        button("+") {
                            shortcut("W")
                            isFocusTraversable = false
                            action {
                                tempoMultiplier.value += 0.01
                            }
                        }
                        button("o") {
                            isFocusTraversable = false
                            action {
                                tempoMultiplier.value = 1.0
                            }
                        }
                        button("-") {
                            shortcut("S")
                            isFocusTraversable = false
                            action {
                                tempoMultiplier.value -= -0.01
                            }
                        }
                    }
                }
            }
            tab("Constant") {
                whenSelected { engineController.setTempoModifier(constantTempoFun) }
                vbox {
                    hbox {
                        label("Constant tempo: ")
                        label(constantTempo.stringBinding { m -> if (m != null) String.format("%.2f", m.bpm) else "" }) {
                            minWidth = 50.0
                            maxWidth = 50.0
                        }
                    }
                    hbox {
                        button("+") {
                            isFocusTraversable = false
                            action {
                                constantTempo.value += 1.0
                            }
                        }
                        button("o") {
                            isFocusTraversable = false
                            action {
                                constantTempo.value = Tempo(songTempo.value?.bpm?.roundToInt()?.toDouble() ?: 120.0)
                            }
                        }
                        button("-") {
                            isFocusTraversable = false
                            action {
                                constantTempo.value -= 1.0
                            }
                        }
                    }
                }
            }
        }
        hbox {
            label("Song tempo: ")
            label(songTempo.stringBinding { t -> t?.bpm?.let { String.format("%.2f", it) } ?: "---" }) {
                minWidth = 50.0
                maxWidth = 50.0
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
                engineController.setTempoModifier(tempoMultiplierFun)
                measureCount.value = event.measures
                isDisable = false
            }
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
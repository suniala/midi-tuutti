package midituutti

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import javafx.stage.Stage
import midituutti.components.measureRangeControl
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
import midituutti.midi.TimeSignature
import tornadofx.*
import java.io.File
import kotlin.math.roundToInt
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

    fun resetMeasureRange(range: Pair<Int, Int>) {
        engine().resetMeasureRange(range.first, range.second)
    }

    fun setTempoModifier(f: (Tempo) -> Tempo) = engine().setTempoModifier(f)
}

@ExperimentalTime
class PlayerView : View("Player") {
    val rootFontSize: DoubleProperty by param()
    val engineController: EngineController by param()

    private val measureRange = SimpleObjectProperty<Pair<Int, Int>>()
    private val measureRangeChanging = SimpleBooleanProperty(false)
    private var measureRangeBounds: ObjectProperty<Pair<Int, Int>> by singleAssign()
    private var measureRangeBlink: BooleanProperty by singleAssign()

    private var playButton: ToggleButton by singleAssign()
    private var clickButton: ToggleButton by singleAssign()
    private var drumMuteButton: ToggleButton by singleAssign()
    private val songTempo = SimpleObjectProperty<Tempo?>()
    private val tempoMultiplier = SimpleObjectProperty(1.0)
    private val constantTempo = SimpleObjectProperty(Tempo(120.0))
    private val adjustedTempo = SimpleObjectProperty<Tempo?>()
    private val currentMeasure = SimpleObjectProperty<Int?>()
    private val currentTimeSignature = SimpleObjectProperty<TimeSignature?>()
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

        style(rootFontSize) { prop(padding, Style.padRemCommon) }

        vbox {
            addClass(Style.display)

            hbox {
                spacer()

                vbox {
                    style(rootFontSize) { prop(padding, Style.padRemCommon) }

                    vbox {
                        label("position") {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                        }
                        label {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplayMain) }
                            textProperty().bind(currentMeasure.stringBinding { v ->
                                (v ?: 1).let { m -> "$m".padStart(3, ' ') }
                            })
                        }
                    }

                    hbox {
                        vbox {
                            hgrow = Priority.ALWAYS
                            alignment = Pos.TOP_LEFT

                            label("play range") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(measureRange.stringBinding { v ->
                                    (v ?: Pair("?", "?")).let { (s, e) -> "$s ‒ $e" }
                                })
                                measureRangeBlink = nodeBlinker(this, Style.blink)
                                measureRangeBlink.bind(measureRangeChanging)
                            }
                        }
                        vbox {
                            hgrow = Priority.ALWAYS
                            alignment = Pos.TOP_RIGHT

                            label("measures") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(measureCount.stringBinding { v ->
                                    (v ?: 1).let { c -> "$c".padStart(3, ' ') }
                                })
                            }
                        }
                    }
                }

                pane {
                    addClass(Style.displaySectionSeparator)
                    style(rootFontSize) {
                        prop(borderWidth,
                                fun(rem: (Double) -> String): String = "${rem(Style.padRemCommon)} 0px ${rem(Style.padRemCommon)} 0px")
                    }
                }

                vbox {
                    style(rootFontSize) {
                        prop(padding, Style.padRemCommon)
                        prop(minWidth, Style.widthTimeSigHead)
                    }
                    label("timesig") {
                        addClass(Style.displayFont)
                        style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                    }
                    vbox {
                        addClass(Style.timeSignatureValue)
                        hbox {
                            spacer()
                            label {
                                useMaxWidth = true
                                addClass(Style.displayFont, Style.timeSignatureValue)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplayTimeSignature) }
                                textProperty().bind(currentTimeSignature.stringBinding { ts ->
                                    ts?.numerator?.toString() ?: ""
                                })
                            }
                            spacer()
                        }
                        pane {
                            addClass(Style.timeSignatureSeparator)
                        }
                        hbox {
                            spacer()
                            label("4") {
                                useMaxWidth = true
                                addClass(Style.displayFont, Style.timeSignatureValue)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplayTimeSignature) }
                                textProperty().bind(currentTimeSignature.stringBinding { ts ->
                                    ts?.denominator?.toString() ?: ""
                                })
                            }
                            spacer()
                        }
                    }

                    spacer()

                    vbox {
                        hgrow = Priority.ALWAYS
                        alignment = Pos.TOP_LEFT

                        label("next") {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                        }
                        label("?/?") {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            // TODO: next measure time signature
                        }
                    }
                }

                pane {
                    addClass(Style.displaySectionSeparator)
                    style(rootFontSize) {
                        prop(borderWidth,
                                fun(rem: (Double) -> String): String = "${rem(Style.padRemCommon)} 0px ${rem(Style.padRemCommon)} 0px")
                    }
                }

                vbox {
                    style(rootFontSize) { prop(padding, Style.padRemCommon) }

                    vbox {
                        label("bpm") {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                        }
                        label {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplayMain) }
                            textProperty().bind(adjustedTempo.stringBinding { t ->
                                t?.bpm?.roundToInt()?.toString()?.padStart(3, ' ') ?: "---"
                            })
                        }
                    }

                    hbox {
                        vbox {
                            hgrow = Priority.ALWAYS
                            alignment = Pos.TOP_LEFT

                            label("adjust") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(tempoMultiplier.stringBinding { v ->
                                    (v as Double).let { m -> "${(m * 100).roundToInt()} %" }
                                })
                            }
                        }
                        vbox {
                            hgrow = Priority.ALWAYS
                            alignment = Pos.TOP_RIGHT

                            label("song tempo") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(songTempo.stringBinding { t ->
                                    t?.bpm?.roundToInt()?.toString()?.plus(" bpm") ?: "---"
                                })
                            }
                        }
                    }
                }

                spacer()
            }
        }

        hbox {
            style(rootFontSize) { prop(padding, Style.padRemCommon) }

            spacer()

            vbox {
                style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                measureRangeControl(rootFontSize).run {
                    measureRange.bindBidirectional(valueProperty())
                    measureRangeChanging.bind(valueChangingProperty())
                    measureRangeBounds = boundsProperty()
                }
            }

            spacer()
        }

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
                        currentTimeSignature.value = playbackEvent.timeSignature
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

                measureRangeBounds.value = Pair(1, event.measures)
                measureRange.value = measureRangeBounds.value

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

        measureRangeChanging.onChange { changing ->
            if (!changing) {
                engineController.resetMeasureRange(measureRange.value)
            }
        }
    }
}

@ExperimentalTime
class RootView : View("Root") {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    private val engineController = tornadofx.find(EngineController::class)

    private val playerView = find<PlayerView>(mapOf(
            PlayerView::rootFontSize to rootFontSize,
            PlayerView::engineController to engineController))

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

        shortcut("F11") { primaryStage.isFullScreen = true }
    }
}

@ExperimentalTime
class MidiTuuttiApp : App() {
    override val primaryView = RootView::class

    private val preferredHeight = 400.0
    private val preferredWidth = preferredHeight * 1.4

    override fun start(stage: Stage) {
        with(stage) {
            super.start(this)
            importStylesheet(Style::class)

            val view = find(RootView::class)
            view.rootFontSize.bind(scene.heightProperty().divide(22))

            parameters.raw.firstOrNull()?.let { path ->
                val file = File(path)
                if (file.exists() && file.isFile && file.canRead()) {
                    val engineController = find(EngineController::class)
                    engineController.openFile(file)
                }
            }

            // Set dimensions after view has been initialized so as to make view contents scale according to
            // window dimensions.
            height = preferredHeight
            width = preferredWidth
            isResizable = false
        }
    }
}

@ExperimentalTime
fun main(args: Array<String>) {
    launch<MidiTuuttiApp>(args)
}
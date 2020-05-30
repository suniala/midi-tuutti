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
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import javafx.stage.FileChooser
import javafx.stage.Stage
import midituutti.components.measureRangeControl
import midituutti.components.nonFocusableButton
import midituutti.components.nonFocusableToggleButton
import midituutti.engine.ClickTrack
import midituutti.engine.EngineTrack
import midituutti.engine.MeasurePlaybackEvent
import midituutti.engine.MidiTrack
import midituutti.engine.MutePlaybackEvent
import midituutti.engine.PlayEvent
import midituutti.engine.PlaybackEngine
import midituutti.engine.PlaybackEngine.createPlayer
import midituutti.engine.PlaybackEvent
import midituutti.engine.Player
import midituutti.engine.SongStructure
import midituutti.engine.TempoEvent
import midituutti.midi.Tempo
import midituutti.midi.TimeSignature
import tornadofx.*
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

val drumTrack = MidiTrack(10)

class UiPlaybackEvent(val pe: PlaybackEvent) : FXEvent()
class LoadEvent(val song: SongStructure) : FXEvent()

enum class TempoMode {
    CONSTANT, MULTIPLIER
}

enum class TempoAdjustment {
    DECREASE, RESET, INCREASE
}

@ExperimentalTime
class PlayerController : Controller() {
    private var player: Player? = null

    fun openFile(file: File) {
        player?.quit()
        val playerState = createPlayer(file.absolutePath, null, null)
        player = playerState.player
        // Pass events from the player thread to the ui thread via TornadoFX EventBus
        player().addPlaybackListener(fun(event: PlaybackEvent): Unit = fire(UiPlaybackEvent(event)))

        // Propagating current button positions to the new player instance is a bit difficult so let's just
        // reset everything.
        player().mute(ClickTrack)
        player().unMute(drumTrack)

        fire(LoadEvent(playerState.song))
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
}

@ExperimentalTime
class PlayerView : View("Player") {
    val rootFontSize: DoubleProperty by param()
    val playerController: PlayerController by param()

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
    private val song = SimpleObjectProperty<SongStructure?>()
    private val tempoModeGroup = ToggleGroup()
    private val tempoMode = tempoModeGroup.selectedValueProperty<TempoMode>()

    private fun updateTempoModifier(tempoMode: TempoMode, multiplier: Double, constant: Tempo) {
        when (tempoMode) {
            TempoMode.MULTIPLIER -> playerController.setTempoModifier(fun(tempo: Tempo) = tempo * multiplier)
            TempoMode.CONSTANT -> playerController.setTempoModifier(fun(_) = constant)
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
                                textProperty().bind(song.stringBinding { s ->
                                    s?.let {
                                        s.measures.size.let { c -> "$c".padStart(3, ' ') }
                                    }
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
                                    ts?.beats?.toString() ?: ""
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
                                    ts?.unit?.toString() ?: ""
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
                        label {
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            textProperty().bind(currentMeasure.stringBinding { m ->
                                m?.let {
                                    song.value?.let { s ->
                                        run {
                                            val next = s.measures.getOrElse(m) { s.measures.first() }
                                            "${next.timeSignature.beats}/${next.timeSignature.unit}"
                                        }
                                    }
                                }
                            })
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

                hbox {
                    style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                    vbox {
                        style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                        label("Play") {
                            style(rootFontSize) { prop(fontSize, Style.fontRemControlTitle) }
                        }

                        playButton = nonFocusableToggleButton("Play") {
                            useMaxWidth = true
                            style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                            shortcut("Space") { fire() }
                            action {
                                playerController.togglePlay()
                            }
                        }

                        hbox {
                            style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }
                            nonFocusableButton("<<") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("Home")
                                action {
                                    playerController.jump { 0 }
                                }
                            }
                            nonFocusableButton("<") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("A")
                                action {
                                    playerController.jump { m -> m - 1 }
                                }
                            }
                            nonFocusableButton(">") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("D")
                                action {
                                    playerController.jump { m -> m + 1 }
                                }
                            }
                        }
                    }

                    pane {
                        addClass(Style.controlSectionSeparator)
                    }

                    vbox {
                        style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                        label("Channels") {
                            style(rootFontSize) { prop(fontSize, Style.fontRemControlTitle) }
                        }

                        vbox {
                            style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                            clickButton = nonFocusableToggleButton("Click off") {
                                useMaxWidth = true
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("C") { fire() }
                                val stateText = selectedProperty().stringBinding {
                                    if (it == true) "Click On" else "Click Off"
                                }
                                textProperty().bind(stateText)
                                action {
                                    playerController.toggleClick()
                                }
                            }

                            drumMuteButton = nonFocusableToggleButton("Drums on") {
                                useMaxWidth = true
                                isSelected = true
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("M") { fire() }
                                val stateText = selectedProperty().stringBinding {
                                    if (it == false) "Drums Off" else "Drums On"
                                }
                                textProperty().bind(stateText)
                                action {
                                    playerController.toggleTrack(drumTrack)
                                }
                            }
                        }
                    }
                    pane {
                        addClass(Style.controlSectionSeparator)
                    }

                    vbox {
                        style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                        label("Tempo") {
                            style(rootFontSize) { prop(fontSize, Style.fontRemControlTitle) }
                        }

                        vbox {
                            style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                            hbox {
                                style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                                nonFocusableToggleButton("Song", tempoModeGroup, selectFirst = true,
                                        value = TempoMode.MULTIPLIER) {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    addEventFilter(MouseEvent.ANY, preventDeselect())
                                }
                                nonFocusableToggleButton("Fixed", tempoModeGroup, value = TempoMode.CONSTANT) {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    addEventFilter(MouseEvent.ANY, preventDeselect())
                                }
                            }

                            hbox {
                                style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }
                                nonFocusableButton("‒") {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    shortcut("S")
                                    action {
                                        adjustCurrentTempoMode(TempoAdjustment.DECREASE)
                                    }
                                }
                                nonFocusableButton("O") {
                                    hgrow = Priority.ALWAYS
                                    useMaxWidth = true
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    action {
                                        adjustCurrentTempoMode(TempoAdjustment.RESET)
                                    }
                                }
                                nonFocusableButton("+") {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    shortcut("W")
                                    action {
                                        adjustCurrentTempoMode(TempoAdjustment.INCREASE)
                                    }
                                }
                            }
                        }
                    }
                }

                measureRangeControl(rootFontSize).run {
                    measureRange.bindBidirectional(valueProperty())
                    measureRangeChanging.bind(valueChangingProperty())
                    measureRangeBounds = boundsProperty()
                }
            }

            spacer()
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
                            10 -> drumMuteButton.selectedProperty().value = !playbackEvent.muted
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

                measureRangeBounds.value = Pair(1, event.song.measures.size)
                measureRange.value = measureRangeBounds.value

                song.value = event.song
                currentTimeSignature.value = event.song.measures.first().timeSignature
                currentMeasure.value = 1

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

        measureRange.onChange { range -> playerController.resetMeasureRange(range as Pair<Int, Int>) }
    }
}

@ExperimentalTime
class RootView : View("Midi-Tuutti") {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    private val playerController = tornadofx.find(PlayerController::class)

    private val playerView = find<PlayerView>(mapOf(
            PlayerView::rootFontSize to rootFontSize,
            PlayerView::playerController to playerController))

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
                        playerController.openFile(selectedFile)
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
        PlaybackEngine.initialize()

        with(stage) {
            super.start(this)
            importStylesheet(Style::class)

            val view = find(RootView::class)
            view.rootFontSize.bind(scene.heightProperty().divide(22))

            parameters.raw.firstOrNull()?.let { path ->
                val file = File(path)
                if (file.exists() && file.isFile && file.canRead()) {
                    val playerController = find(PlayerController::class)
                    playerController.openFile(file)
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
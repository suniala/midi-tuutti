package midituutti

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import midituutti.components.measureRangeControl
import midituutti.components.nonFocusableButton
import midituutti.components.nonFocusableToggleButton
import midituutti.engine.MeasurePlaybackEvent
import midituutti.engine.PlayEvent
import midituutti.engine.TempoEvent
import midituutti.midi.Tempo
import tornadofx.*
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

private enum class TempoMode {
    CONSTANT, MULTIPLIER
}

private enum class TempoAdjustment {
    DECREASE, RESET, INCREASE
}

/**
 * Our player component is a fragment as we want to create a new instance for each file as
 * this makes initialisation easier.
 */
@ExperimentalTime
class PlayerFragment : Fragment("Player") {
    val rootFontSize: DoubleProperty by param()
    val playerController: PlayerController by param()

    private val song = SimpleObjectProperty(playerController.song())

    private val measureRangeChanging = SimpleBooleanProperty(false)
    private var measureRangeBounds = SimpleObjectProperty(Pair(1, song.value.measures.size))
    private val measureRange = SimpleObjectProperty(Pair(1, song.value.measures.size))

    //    private val measureRange = SimpleObjectProperty(measureRangeBounds.value)
    private var measureRangeBlink: BooleanProperty by singleAssign()

    private var playButton: ToggleButton by singleAssign()
    private val songTempo = SimpleObjectProperty(song.value.measures.first().initialTempo)
    private val tempoMultiplier = SimpleObjectProperty(1.0)
    private val constantTempo = SimpleObjectProperty(songTempo.value)
    private val adjustedTempo = SimpleObjectProperty(songTempo.value)
    private val currentMeasure = SimpleObjectProperty(1)
    private val currentTimeSignature = SimpleObjectProperty(song.value.measures.first().timeSignature)
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
                            textProperty().bind(currentMeasure.stringBinding { m ->
                                "$m".padStart(3, ' ')
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
                                textProperty().bind(measureRange.nonNullStringBinding { (s, e) -> "$s ‒ $e" })
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
                                textProperty().bind(song.nonNullStringBinding { s ->
                                    s.measures.size.let { c -> "$c".padStart(3, ' ') }
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
                                textProperty().bind(currentTimeSignature.nonNullStringBinding { it.beats.toString() })
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
                                textProperty().bind(currentTimeSignature.nonNullStringBinding { it.unit.toString() })
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
                            textProperty().bind(currentMeasure.nonNullStringBinding { m ->
                                song.value.let { s ->
                                    val next = s.measures.getOrElse(m) { s.measures.first() }
                                    "${next.timeSignature.beats}/${next.timeSignature.unit}"
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
                            textProperty().bind(adjustedTempo.nonNullStringBinding { t ->
                                t.bpm.roundToInt().toString().padStart(3, ' ')
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
                                textProperty().bind(tempoMultiplier.nonNullStringBinding { "${(it * 100).roundToInt()} %" })
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
                                textProperty().bind(songTempo.nonNullStringBinding { t ->
                                    t.bpm.roundToInt().toString().plus(" bpm")
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
                                shortcut("Left")
                                action {
                                    playerController.jump { m -> m - 1 }
                                }
                            }
                            nonFocusableButton(">") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("Right")
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
                                    shortcut("Down")
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
                                    shortcut("Up")
                                    action {
                                        adjustCurrentTempoMode(TempoAdjustment.INCREASE)
                                    }
                                }
                            }
                        }
                    }
                }

                measureRangeControl(rootFontSize).run {
                    valueProperty().value = measureRange.value
                    measureRange.bindBidirectional(valueProperty())
                    measureRangeChanging.bind(valueChangingProperty())
                    val boundsProperty = boundsProperty()
                    boundsProperty.value = measureRangeBounds.value
                    measureRangeBounds.bindBidirectional(boundsProperty)
                }
            }

            spacer()
        }

        shortcut("Ctrl+Space") {
            tempoMode.value = when (tempoMode.value as TempoMode) {
                TempoMode.CONSTANT -> TempoMode.MULTIPLIER
                TempoMode.MULTIPLIER -> TempoMode.CONSTANT
            }
        }

        subscribe<UiPlaybackEvent> { event ->
            event.pe.let { playbackEvent ->
                when (playbackEvent) {
                    is PlayEvent -> playButton.selectedProperty().value = playbackEvent.playing
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

        tempoMultiplier.onChange { multiplier ->
            updateTempoModifier(tempoMode.value, multiplier as Double, constantTempo.value)
        }
        constantTempo.onChange { tempo ->
            updateTempoModifier(tempoMode.value, tempoMultiplier.value, tempo as Tempo)
        }
        tempoMode.onChange { mode ->
            constantTempo.value = songTempo.value as Tempo
            updateTempoModifier(mode as TempoMode, tempoMultiplier.value, constantTempo.value)
        }

        measureRange.onChange { range -> playerController.resetMeasureRange(range as Pair<Int, Int>) }
    }
}

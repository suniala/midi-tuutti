package midituutti

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import midituutti.components.measureRangeControl
import midituutti.components.nonFocusableButton
import midituutti.components.nonFocusableToggleButton
import midituutti.engine.MeasurePlaybackEvent
import midituutti.engine.PlayEvent
import midituutti.engine.TempoEvent
import midituutti.midi.Tempo
import kotlin.math.roundToInt

private enum class TempoMode {
    CONSTANT, MULTIPLIER
}

private enum class TempoAdjustment {
    DECREASE, RESET, INCREASE
}

/**
 * Our player component is created anew for each file as this makes initialisation easier.
 */
class PlayerFragment(
    private val rootFontSize: DoubleProperty,
    private val playerController: PlayerController
) {
    private val song = SimpleObjectProperty(playerController.song())

    private val measureRangeChanging = SimpleBooleanProperty(false)
    private var measureRangeBounds = SimpleObjectProperty(Pair(1, song.value.measures.size))
    private val measureRange = SimpleObjectProperty(Pair(1, song.value.measures.size))

    private lateinit var measureRangeBlink: BooleanProperty

    private lateinit var playButton: ToggleButton
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

    val root: VBox = VBox().also { root ->
        root.style(rootFontSize) { prop(padding, Style.padRemCommon) }

        root.vbox {
            styleClass.add(Style.display)

            hbox {
                spacer()

                vbox {
                    style(rootFontSize) { prop(padding, Style.padRemCommon) }

                    vbox {
                        label("position") {
                            styleClass.add(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                        }
                        label {
                            styleClass.add(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplayMain) }
                            textProperty().bind(currentMeasure.nonNullStringBinding { m ->
                                "$m".padStart(3, ' ')
                            })
                        }
                    }

                    hbox {
                        vbox {
                            HBox.setHgrow(this, Priority.ALWAYS)
                            alignment = Pos.TOP_LEFT

                            label("play range") {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(measureRange.nonNullStringBinding { (s, e) -> "$s ‒ $e" })
                                measureRangeBlink = nodeBlinker(this, Style.blink)
                                measureRangeBlink.bind(measureRangeChanging)
                            }
                        }
                        vbox {
                            HBox.setHgrow(this, Priority.ALWAYS)
                            alignment = Pos.TOP_RIGHT

                            label("measures") {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(song.nonNullStringBinding { s ->
                                    s.measures.size.let { c -> "$c".padStart(3, ' ') }
                                })
                            }
                        }
                    }
                }

                pane {
                    styleClass.add(Style.displaySectionSeparator)
                    style(rootFontSize) {
                        prop(
                            borderWidth,
                            fun(rem: (Double) -> String): String =
                                "${rem(Style.padRemCommon)} 0px ${rem(Style.padRemCommon)} 0px")
                    }
                }

                vbox {
                    style(rootFontSize) {
                        prop(padding, Style.padRemCommon)
                        prop(minWidth, Style.widthTimeSigHead)
                    }
                    label("timesig") {
                        styleClass.add(Style.displayFont)
                        style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                    }
                    vbox {
                        styleClass.add(Style.timeSignatureValue)
                        hbox {
                            spacer()
                            label {
                                maxWidth = Region.USE_COMPUTED_SIZE
                                styleClass.addAll(Style.displayFont, Style.timeSignatureValue)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplayTimeSignature) }
                                textProperty().bind(currentTimeSignature.nonNullStringBinding { it.beats.toString() })
                            }
                            spacer()
                        }
                        pane {
                            styleClass.add(Style.timeSignatureSeparator)
                        }
                        hbox {
                            spacer()
                            label("4") {
                                maxWidth = Region.USE_COMPUTED_SIZE
                                styleClass.addAll(Style.displayFont, Style.timeSignatureValue)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplayTimeSignature) }
                                textProperty().bind(currentTimeSignature.nonNullStringBinding { it.unit.toString() })
                            }
                            spacer()
                        }
                    }

                    spacer()

                    vbox {
                        HBox.setHgrow(this, Priority.ALWAYS)
                        alignment = Pos.TOP_LEFT

                        label("next") {
                            styleClass.add(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                        }
                        label {
                            styleClass.add(Style.displayFont)
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
                    styleClass.add(Style.displaySectionSeparator)
                    style(rootFontSize) {
                        prop(
                            borderWidth,
                            fun(rem: (Double) -> String): String =
                                "${rem(Style.padRemCommon)} 0px ${rem(Style.padRemCommon)} 0px")
                    }
                }

                vbox {
                    style(rootFontSize) { prop(padding, Style.padRemCommon) }

                    vbox {
                        label("bpm") {
                            styleClass.add(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                        }
                        label {
                            styleClass.add(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, Style.fontRemDisplayMain) }
                            textProperty().bind(adjustedTempo.nonNullStringBinding { t ->
                                t.bpm.roundToInt().toString().padStart(3, ' ')
                            })
                        }
                    }

                    hbox {
                        vbox {
                            HBox.setHgrow(this, Priority.ALWAYS)
                            alignment = Pos.TOP_LEFT

                            label("adjust") {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                                textProperty().bind(tempoMultiplier.nonNullStringBinding { "${(it * 100).roundToInt()} %" })
                            }
                        }
                        vbox {
                            HBox.setHgrow(this, Priority.ALWAYS)
                            alignment = Pos.TOP_RIGHT

                            label("song tempo") {
                                styleClass.add(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, Style.fontRemDisplaySub) }
                            }
                            label {
                                styleClass.add(Style.displayFont)
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

        root.hbox {
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

                        hbox {
                            style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

                            playButton = nonFocusableToggleButton("Play") {
                                maxWidth = Region.USE_COMPUTED_SIZE
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("Space") { fire() }
                                setOnAction { playerController.togglePlay() }
                            }

                            nonFocusableButton("<<") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("Home") { fire() }
                                setOnAction { playerController.jump { 0 } }
                            }
                            nonFocusableButton("<") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("Left") { fire() }
                                setOnAction { playerController.jump { m -> m - 1 } }
                            }
                            nonFocusableButton(">") {
                                style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                shortcut("Right") { fire() }
                                setOnAction { playerController.jump { m -> m + 1 } }
                            }
                        }
                    }

                    pane {
                        styleClass.add(Style.controlSectionSeparator)
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

                                nonFocusableToggleButton(
                                    "Song", tempoModeGroup, selectFirst = true,
                                    value = TempoMode.MULTIPLIER
                                ) {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    addEventFilter(MouseEvent.ANY, preventDeselect())
                                }
                                nonFocusableToggleButton("Fixed", tempoModeGroup, value = TempoMode.CONSTANT) {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    addEventFilter(MouseEvent.ANY, preventDeselect())
                                }

                                nonFocusableButton("‒") {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    shortcut("Down") { fire() }
                                    setOnAction { adjustCurrentTempoMode(TempoAdjustment.DECREASE) }
                                }
                                nonFocusableButton("O") {
                                    HBox.setHgrow(this, Priority.ALWAYS)
                                    maxWidth = Region.USE_COMPUTED_SIZE
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    setOnAction { adjustCurrentTempoMode(TempoAdjustment.RESET) }
                                }
                                nonFocusableButton("+") {
                                    style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
                                    shortcut("Up") { fire() }
                                    setOnAction { adjustCurrentTempoMode(TempoAdjustment.INCREASE) }
                                }
                            }
                        }
                    }
                }

                measureRangeControl(rootFontSize).run {
                    val boundsProperty = boundsProperty()
                    boundsProperty.value = measureRangeBounds.value
                    measureRangeBounds.bindBidirectional(boundsProperty)

                    valueProperty().value = measureRange.value
                    measureRange.bindBidirectional(valueProperty())
                    measureRangeChanging.bind(valueChangingProperty())
                }
            }

            spacer()
        }

        root.shortcut("Ctrl+Space") {
            tempoMode.value = when (tempoMode.value as TempoMode) {
                TempoMode.CONSTANT -> TempoMode.MULTIPLIER
                TempoMode.MULTIPLIER -> TempoMode.CONSTANT
            }
        }

        playerController.addUiEventListener { playbackEvent ->
            when (playbackEvent) {
                is PlayEvent -> playButton.isSelected = playbackEvent.playing
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

        tempoMultiplier.addListener { _, _, multiplier ->
            updateTempoModifier(tempoMode.value, multiplier.toDouble(), constantTempo.value)
        }
        constantTempo.addListener { _, _, tempo ->
            updateTempoModifier(tempoMode.value, tempoMultiplier.value, tempo as Tempo)
        }
        tempoMode.addListener { _, _, mode ->
            constantTempo.value = songTempo.value as Tempo
            updateTempoModifier(mode as TempoMode, tempoMultiplier.value, constantTempo.value)
        }

        measureRange.addListener { _, _, range -> playerController.resetMeasureRange(range as Pair<Int, Int>) }
    }
}

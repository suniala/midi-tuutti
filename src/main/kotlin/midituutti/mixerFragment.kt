package midituutti

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Slider
import javafx.scene.control.ToggleButton
import javafx.scene.layout.Priority
import midituutti.components.nonFocusableSlider
import midituutti.components.nonFocusableToggleButton
import midituutti.engine.ClickTrack
import midituutti.engine.EngineTrack
import midituutti.engine.MidiTrack
import tornadofx.*
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

private data class TrackShortCuts(val up: String, val down: String)

private data class SliderProps(val volume: DoubleProperty, val solo: BooleanProperty, val muted: BooleanProperty)

private val supportedTracks = (1..16).map { MidiTrack(it) } + ClickTrack

private val shortcutsInOrder = listOf(
        TrackShortCuts("1", "Q"),
        TrackShortCuts("2", "W"),
        TrackShortCuts("3", "E"),
        TrackShortCuts("4", "R"),
        TrackShortCuts("5", "T"),
        TrackShortCuts("6", "Y"),
        TrackShortCuts("7", "U"),
        TrackShortCuts("8", "I"),
        TrackShortCuts("9", "O"),
        TrackShortCuts("0", "P"),
        TrackShortCuts("A", "Z"),
        TrackShortCuts("S", "X"),
        TrackShortCuts("D", "C"),
        TrackShortCuts("F", "V"),
        TrackShortCuts("G", "B"),
        TrackShortCuts("H", "N"),
        TrackShortCuts("J", "M")
)

private val trackShortcuts = supportedTracks.zip(shortcutsInOrder).toMap()

private fun EventTarget.mixerSlider(rootFontSize: DoubleProperty,
                                    track: EngineTrack,
                                    enabled: Boolean,
                                    shortcut: (String, () -> Unit) -> Unit,
                                    op: Node.() -> Unit = {}): SliderProps {
    var theSlider: Slider by singleAssign()
    var solo: ToggleButton by singleAssign()
    var muted: ToggleButton by singleAssign()

    vbox {
        isDisable = !enabled
        alignment = Pos.CENTER
        style(rootFontSize) {
            prop(spacing, 0.2)
            prop(padding,
                    fun(rem: (Double) -> String): String = "${rem(0.1)} ${rem(0.1)} ${rem(0.4)} ${rem(0.1)}")
        }

        label(when (track) {
            is MidiTrack -> "${track.channel}"
            is ClickTrack -> "C"
        }) {
            style(rootFontSize) { prop(fontSize, 0.5) }
        }

        solo = nonFocusableToggleButton("S") {
            useMaxWidth = true
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            shortcut("Shift+${trackShortcuts.getValue(track).up}") { if (enabled) fire() }
        }

        muted = nonFocusableToggleButton("M") {
            useMaxWidth = true
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            shortcut("Shift+${trackShortcuts.getValue(track).down}") { if (enabled) fire() }
        }

        theSlider = nonFocusableSlider(0.0, 2.0) {
            orientation = Orientation.VERTICAL
            vgrow = Priority.ALWAYS
            style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
            shortcut(trackShortcuts.getValue(track).up) { if (enabled) value += 0.1 }
            shortcut(trackShortcuts.getValue(track).down) { if (enabled) value -= 0.1 }
        }

        op()
    }

    theSlider.value = 1.0

    // Round slider values to two decimals.
    theSlider.valueProperty().mutateOnChange { d -> (d as Double * 100).roundToInt().toDouble() / 100.0 }

    return SliderProps(theSlider.valueProperty(), solo.selectedProperty(), muted.selectedProperty())
}

@ExperimentalTime
class MixerFragment : Fragment("Mixer") {
    val rootFontSize: DoubleProperty by param()
    val playerController: PlayerController by param()

    override val root = hbox {
        style(rootFontSize) {
            prop(padding, Style.padRemCommon)
        }

        supportedTracks.forEach { t ->
            mixerSlider(
                    rootFontSize,
                    track = t,
                    enabled = playerController.song().tracks.contains(t),
                    shortcut = { c, a -> shortcut(c, a) }
            ) {
                tooltip(trackInstruments(t)?.let {
                    it.joinToString("\n") { p -> if (p.first != null) "${p.first} ${p.second}" else p.second }
                }) {
                    showDelay = 50.millis
                }
            }.run {
                volume.onChange { f ->
                    playerController.updateMixerChannel(t) { mc -> mc.copy(volumeAdjustment = f) }
                }
                solo.onChange {
                    playerController.updateMixerChannel(t) { mc -> mc.copy(solo = it) }
                }
                muted.onChange {
                    playerController.updateMixerChannel(t) { mc -> mc.copy(muted = it) }
                }
            }

            spacer()
        }
    }

    private fun trackInstruments(t: EngineTrack) = when (t) {
        is MidiTrack -> playerController.song().trackInstruments[t]
        is ClickTrack -> listOf(Pair(null, "Click"))
    }
}
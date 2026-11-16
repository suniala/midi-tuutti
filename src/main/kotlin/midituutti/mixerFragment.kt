package midituutti

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.Duration
import midituutti.components.nonFocusableSlider
import midituutti.components.nonFocusableToggleButton
import midituutti.engine.ClickTrack
import midituutti.engine.EngineTrack
import midituutti.engine.MidiTrack
import midituutti.engine.MixerChannel
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

private fun Pane.mixerSlider(
    rootFontSize: DoubleProperty,
    mixerChannelState: MixerChannel,
    enabled: Boolean,
    op: Node.() -> Unit = {}
): SliderProps {
    lateinit var theSlider: Slider
    lateinit var solo: ToggleButton
    lateinit var muted: ToggleButton

    val track = mixerChannelState.track

    VBox().also { box ->
        box.isDisable = !enabled
        box.alignment = Pos.CENTER
        box.style(rootFontSize) {
            prop(spacing, 0.2)
            prop(
                padding,
                fun(rem: (Double) -> String): String = "${rem(0.1)} ${rem(0.1)} ${rem(0.4)} ${rem(0.1)}")
        }

        box.children.add(Label(when (track) {
            is MidiTrack -> "${track.channel}"
            is ClickTrack -> "C"
        }).also { label ->
            label.style(rootFontSize) { prop(fontSize, 0.5) }
        })

        solo = box.nonFocusableToggleButton("S") {
            isSelected = mixerChannelState.solo

            maxWidth = Region.USE_COMPUTED_SIZE
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            shortcut("Shift+${trackShortcuts.getValue(track).up}") { if (enabled) fire() }
        }

        muted = box.nonFocusableToggleButton("M") {
            isSelected = mixerChannelState.muted

            maxWidth = Region.USE_COMPUTED_SIZE
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            shortcut("Shift+${trackShortcuts.getValue(track).down}") { if (enabled) fire() }
        }

        theSlider = box.nonFocusableSlider(0.0, 2.0) {
            orientation = Orientation.VERTICAL
            style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
            shortcut(trackShortcuts.getValue(track).up) { if (enabled) value += 0.1 }
            shortcut(trackShortcuts.getValue(track).down) { if (enabled) value -= 0.1 }
        }
        VBox.setVgrow(theSlider, Priority.ALWAYS)

        box.op()
    }.also { children.add(it) }

    theSlider.value = 1.0

    // Round slider values to two decimals.
    theSlider.valueProperty().addListener { _, _, newValue ->
        val rounded = (newValue.toDouble() * 100).roundToInt() / 100.0
        if (rounded != newValue.toDouble()) theSlider.value = rounded
    }

    return SliderProps(theSlider.valueProperty(), solo.selectedProperty(), muted.selectedProperty())
}

@ExperimentalTime
class MixerFragment(
    private val rootFontSize: DoubleProperty,
    private val playerController: PlayerController
) {
    val root: HBox = HBox().also { box ->
        box.style(rootFontSize) {
            prop(padding, Style.padRemCommon)
        }

        supportedTracks.forEach { t ->
            box.mixerSlider(
                rootFontSize,
                mixerChannelState = playerController.mixerChannelState(t),
                enabled = playerController.song().tracks.contains(t)
            ) {
                Tooltip.install(this, Tooltip(trackInstruments(t)?.let {
                    it.joinToString("\n") { p -> if (p.first != null) "${p.first} ${p.second}" else p.second }
                }).apply {
                    showDelay = Duration.millis(50.0)
                })
            }.run {
                volume.addListener { _, _, f ->
                    playerController.updateMixerChannel(t) { mc -> mc.copy(volumeAdjustment = f.toDouble()) }
                }
                solo.addListener { _, _, v ->
                    playerController.updateMixerChannel(t) { mc -> mc.copy(solo = v) }
                }
                muted.addListener { _, _, v ->
                    playerController.updateMixerChannel(t) { mc -> mc.copy(muted = v) }
                }
            }

            box.spacer()
        }
    }

    private fun trackInstruments(t: EngineTrack) = when (t) {
        is MidiTrack -> playerController.song().trackInstruments[t]
        is ClickTrack -> listOf(Pair(null, "Click"))
    }
}

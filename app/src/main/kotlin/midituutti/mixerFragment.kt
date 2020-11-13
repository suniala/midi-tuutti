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

data class SliderProps(val volume: DoubleProperty, val solo: BooleanProperty, val muted: BooleanProperty)

fun EventTarget.mixerSlider(rootFontSize: DoubleProperty,
                            track: EngineTrack,
                            enabled: Boolean,
                            op: Node.() -> Unit = {}): SliderProps {
    var theSlider: Slider by singleAssign()
    var solo: ToggleButton by singleAssign()
    var muted: ToggleButton by singleAssign()

    vbox {
        isDisable = !enabled
        alignment = Pos.CENTER
        style(rootFontSize) { prop(spacing, 0.2) }

        label(when (track) {
            is MidiTrack -> "${track.channel}"
            is ClickTrack -> "C"
        }) {
            style(rootFontSize) { prop(fontSize, 0.8) }
        }

        solo = nonFocusableToggleButton("S") {
            useMaxWidth = true
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            action {
            }
        }

        muted = nonFocusableToggleButton("M") {
            useMaxWidth = true
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            action {
            }
        }

        theSlider = nonFocusableSlider(0.0, 2.0) {
            orientation = Orientation.VERTICAL
            vgrow = Priority.ALWAYS
            style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
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

    private val supportedTracks = (1..16).map { MidiTrack(it) } + ClickTrack

    override val root = hbox {
        style(rootFontSize) {
            prop(spacing, Style.spacingRemCommon)
        }

        supportedTracks.forEach { t ->
            mixerSlider(rootFontSize, t, playerController.song().tracks.contains(t)).run {
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
        }
    }
}
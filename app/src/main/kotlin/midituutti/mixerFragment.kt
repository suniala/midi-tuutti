package midituutti

import javafx.beans.property.DoubleProperty
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import midituutti.components.nonFocusableSlider
import midituutti.engine.ClickTrack
import midituutti.engine.EngineTrack
import midituutti.engine.MidiTrack
import midituutti.engine.MixerChannel
import tornadofx.*
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime


fun EventTarget.mixerSlider(rootFontSize: DoubleProperty,
                            track: EngineTrack,
                            enabled: Boolean,
                            op: Node.() -> Unit = {}): DoubleProperty {
    var theSlider: Slider by singleAssign()

    vbox {
        isDisable = !enabled

        label(when (track) {
            is MidiTrack -> "${track.channel}"
            is ClickTrack -> "C"
        })

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

    return theSlider.valueProperty()
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
            run {
                mixerSlider(rootFontSize, t, playerController.song().tracks.contains(t)) { }
                        .onChange { v ->
                            playerController.updateMixer(MixerChannel(t, v, muted = false, solo = false))
                        }
            }
        }
    }
}

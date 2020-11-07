package midituutti

import javafx.beans.property.DoubleProperty
import javafx.geometry.Side
import javafx.scene.control.TabPane
import tornadofx.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
class TabsFragment : Fragment("Tabs") {
    val rootFontSize: DoubleProperty by param()
    val playerController: PlayerController by param()

    private fun newMixerFragment(): MixerFragment =
            find(
                    MixerFragment::rootFontSize to rootFontSize,
                    MixerFragment::playerController to playerController)

    private fun newPlayerFragment(): PlayerFragment =
            find(
                    PlayerFragment::rootFontSize to rootFontSize,
                    PlayerFragment::playerController to playerController)

    private val mixer = newMixerFragment()

    private val player = newPlayerFragment()

    override val root = tabpane {
        side = Side.LEFT
        tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        tab("Player") {
            // Is there a smarter way to do this?
            player.root.attachTo(this)
        }
        tab("Mixer") {
            mixer.root.attachTo(this)
        }
    }
}

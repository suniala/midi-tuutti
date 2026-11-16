package midituutti

import javafx.beans.property.DoubleProperty
import javafx.geometry.Side
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import tornadofx.FX
import tornadofx.find
import kotlin.time.ExperimentalTime

@ExperimentalTime
class TabsFragment(
    private val rootFontSize: DoubleProperty,
    private val playerController: PlayerController
) {
    private val mixer = MixerFragment(rootFontSize, playerController)

    // PlayerFragment is still a tornadofx component until step 4.
    private val player = find<PlayerFragment>(
        FX.defaultScope,
        PlayerFragment::rootFontSize to rootFontSize,
        PlayerFragment::playerController to playerController
    )

    val root: TabPane = TabPane().also { tabs ->
        tabs.side = Side.LEFT
        tabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        val playerTab = Tab("Player").also { it.content = player.root }
        val mixerTab = Tab("Mixer").also { it.content = mixer.root }
        tabs.tabs.addAll(playerTab, mixerTab)

        tabs.shortcut("Tab") {
            if (playerTab.isSelected) tabs.selectionModel.select(mixerTab)
            else tabs.selectionModel.select(playerTab)
        }
    }
}

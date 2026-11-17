package midituutti

import javafx.beans.property.DoubleProperty
import javafx.geometry.Side
import javafx.scene.control.Tab
import javafx.scene.control.TabPane

class TabsFragment(
    private val rootFontSize: DoubleProperty,
    private val playerController: PlayerController
) {
    private val mixer = MixerFragment(rootFontSize, playerController)

    private val player = PlayerFragment(rootFontSize, playerController)

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

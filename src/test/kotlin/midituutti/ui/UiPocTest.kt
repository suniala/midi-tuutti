package midituutti.ui

import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.stage.Stage
import midituutti.RootView
import midituutti.Style
import midituutti.engine.PlaybackEngine
import org.junit.jupiter.api.Test
import org.testfx.framework.junit5.ApplicationTest
import org.testfx.util.WaitForAsyncUtils
import java.io.File

const val stageWidth = 704.0
const val stageHeight = 440.0

class UiPocTest : ApplicationTest() {
    private lateinit var stage: Stage

    override fun start(stage: Stage) {
        this.stage = stage
        PlaybackEngine.initialize()

        val midiFile = File("src/test/resources/midituutti/engine/measures-44-34-58.mid")
        val rootView = RootView(stage, SimpleObjectProperty(null), midiFile)
        val scene = Scene(rootView.root, stageWidth, stageHeight)
        rootView.rootFontSize.bind(scene.heightProperty().divide(19.0))

        stage.title = "Midi-Tuutti"
        stage.scene = scene
        scene.stylesheets.add(Style.cssUrl)

        // pin the window size so the snapshot dimensions are deterministic (headless Monocle
        // has no window decorations, so the scene stays exactly 704x440)
        stage.width = stageWidth
        stage.height = stageHeight
        stage.minWidth = stageWidth
        stage.minHeight = stageHeight
        stage.maxWidth = stageWidth
        stage.maxHeight = stageHeight

        stage.show()
    }

    @Test
    fun loadAndJumpToNextMeasure() {
        waitForFxEventsAndSettle()
        interact { Screenshot.assertScreenshot(stage.scene, "player-01-initial") }

        // ">" button shortcut: jump to next measure
        push(KeyCode.RIGHT)
        waitForLabelText("  2")

        // jumping starts playback; stop it so the screenshot is stable
        push(KeyCode.SPACE)
        waitForFxEventsAndSettle()
        interact { Screenshot.assertScreenshot(stage.scene, "player-02-next-measure") }
    }

    private fun waitForFxEventsAndSettle() {
        WaitForAsyncUtils.waitForFxEvents()
        Thread.sleep(200)
        WaitForAsyncUtils.waitForFxEvents()
    }

    private fun waitForLabelText(text: String, timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val found = arrayOf(false)
            interact {
                found[0] = stage.scene.root.lookupAll(".label").filterIsInstance<Label>().any { it.text == text }
            }
            if (found[0]) return
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for label with text '$text'")
            }
            WaitForAsyncUtils.waitForFxEvents()
            Thread.sleep(50)
        }
    }
}

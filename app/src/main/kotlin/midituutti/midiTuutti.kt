package midituutti

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.stage.FileChooser
import javafx.stage.Stage
import midituutti.engine.PlaybackEngine
import tornadofx.*
import java.io.File
import kotlin.time.ExperimentalTime

class StartView : View("Start") {
    override val root = borderpane {
        center = label("Please open a midi file")
    }
}

@ExperimentalTime
class RootView : View("Midi-Tuutti") {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    private val rootViewController = find<RootViewController>()

    private val playerController = tornadofx.find(PlayerController::class)

    private val initialView =
            if (rootViewController.initialFile.value != null) {
                params
                playerController.load(rootViewController.initialFile.value)
                newTabs()
            } else find<StartView>()

    private var currentView: UIComponent = initialView

    override val root = borderpane {
        top = menubar {
            menu("File") {
                item("Open", "Shortcut+O").action {
                    val fileChooser = FileChooser().apply {
                        title = "Open Midi File"
                        initialDirectory = rootViewController.lastDir.value
                        extensionFilters + listOf(
                                FileChooser.ExtensionFilter("Midi Files", "*.mid"),
                                FileChooser.ExtensionFilter("All Files", "*.*")
                        )
                    }
                    val selectedFile = fileChooser.showOpenDialog(primaryStage)
                    if (selectedFile != null) {
                        rootViewController.lastDir.value = selectedFile.parentFile
                        playerController.load(selectedFile)

                        val tabsFragment = newTabs()
                        currentView.replaceWith(tabsFragment)
                        currentView = tabsFragment
                    }
                }
                separator()
                item("Quit")
            }
        }

        bottom = initialView.root

        shortcut("F11") { primaryStage.isFullScreen = true }
    }

    private fun newTabs(): TabsFragment =
            find(
                    TabsFragment::rootFontSize to rootFontSize,
                    TabsFragment::playerController to playerController)

}

class RootViewController : Controller() {
    val lastDir = SimpleObjectProperty<File>()
    val initialFile = SimpleObjectProperty<File>()
}

@ExperimentalTime
class MidiTuuttiApp : App() {
    override val primaryView = RootView::class

    private val preferredHeight = 440.0
    private val preferredWidth = preferredHeight * 1.6

    override fun start(stage: Stage) {
        PlaybackEngine.initialize()

        val rootViewController = find<RootViewController>()
        rootViewController.lastDir.value = System.getProperty("midituutti.initialDir")?.let { File(it) }
        rootViewController.initialFile.value = parameters.raw.firstOrNull()?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead()) file
            else null
        }

        with(stage) {
            super.start(this)
            importStylesheet(Style::class)

            val view = find<RootView>()
            view.rootFontSize.bind(scene.heightProperty().divide(19.0))

            // Set dimensions after view has been initialized so as to make view contents scale according to
            // window dimensions.
            height = preferredHeight
            width = preferredWidth
            isResizable = true
        }
    }
}

@ExperimentalTime
fun main(args: Array<String>) {
    launch<MidiTuuttiApp>(args)
}
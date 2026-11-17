package midituutti

import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.input.KeyCombination
import javafx.scene.layout.BorderPane
import javafx.stage.FileChooser
import javafx.stage.Stage
import midituutti.engine.PlaybackEngine
import java.io.File

class StartView {
    val root: BorderPane = BorderPane(Label("Please open a midi file"))
}

class RootView(
    private val stage: Stage,
    private val lastDir: SimpleObjectProperty<File?>,
    initialFile: File?
) {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    private val playerController = PlayerController()

    private val initialView: Parent =
        if (initialFile != null) {
            playerController.load(initialFile)
            newTabs().root
        } else StartView().root

    val root: BorderPane = BorderPane().also { root ->
        root.top = MenuBar().also { menuBar ->
            menuBar.menus.add(Menu("File").also { menu ->
                menu.items.add(MenuItem("Open").also { openItem ->
                    openItem.accelerator = KeyCombination.keyCombination("Shortcut+O")
                    openItem.setOnAction {
                        val fileChooser = FileChooser().apply {
                            title = "Open Midi File"
                            initialDirectory = lastDir.value
                            extensionFilters.addAll(
                                FileChooser.ExtensionFilter("Midi Files", "*.mid"),
                                FileChooser.ExtensionFilter("All Files", "*.*")
                            )
                        }
                        val selectedFile = fileChooser.showOpenDialog(stage)
                        if (selectedFile != null) {
                            lastDir.value = selectedFile.parentFile
                            playerController.load(selectedFile)

                            root.bottom = newTabs().root
                        }
                    }
                })
                menu.items.add(SeparatorMenuItem())
                menu.items.add(MenuItem("Quit").also { quitItem ->
                    quitItem.setOnAction { Platform.exit() }
                })
            })
        }

        root.bottom = initialView

        root.shortcut("F11") { stage.isFullScreen = true }
    }

    private fun newTabs(): TabsFragment = TabsFragment(rootFontSize, playerController)
}

class MidiTuuttiApp : Application() {
    private val preferredHeight = 440.0
    private val preferredWidth = preferredHeight * 1.6

    override fun start(stage: Stage) {
        PlaybackEngine.initialize()

        val lastDir = SimpleObjectProperty(System.getProperty("midituutti.initialDir")?.let { File(it) })
        val initialFile = parameters.raw.firstOrNull()?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead()) file
            else null
        }

        val rootView = RootView(stage, lastDir, initialFile)
        val scene = Scene(rootView.root)
        rootView.rootFontSize.bind(scene.heightProperty().divide(19.0))

        stage.apply {
            title = "Midi-Tuutti"
            this.scene = scene
            scene.stylesheets.add(Style.cssUrl)
            height = preferredHeight
            width = preferredWidth
            isResizable = true
            show()
        }
    }
}

fun main(args: Array<String>) {
    Application.launch(MidiTuuttiApp::class.java, *args)
}

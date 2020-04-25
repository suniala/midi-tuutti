package midituutti

import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.stage.Stage
import tornadofx.*

class MainView : View("Root") {
    val fontSize: DoubleProperty = SimpleDoubleProperty(10.0)

    override val root = vbox() {
        val dynamic = vbox {
            button("play")
            label("label text")
            textarea("Text area text\nand some numbers 1234567890")
        }
        hbox { label("Non-dynamic label") }

        dynamic.styleProperty().bind(Bindings.concat("-fx-font-size: ", fontSize.asString()))
    }
}

class LayoutProtoApp : App() {
    override val primaryView = MainView::class

    override fun start(stage: Stage) {
        with(stage) {
            super.start(this)

            val view = find(MainView::class)
            view.fontSize.bind(scene.widthProperty().add(scene.heightProperty()).divide(50))
        }
    }
}

fun main(args: Array<String>) {
    launch<LayoutProtoApp>(args)
}
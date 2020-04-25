package midituutti

import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import tornadofx.*

class MyStyle : Stylesheet() {

    companion object {
        val tempoDisplay by cssclass()
        val displayFont by cssclass()
        val playTempo by cssclass()
        val playTempoUnit by cssclass()
        val multiplier by cssclass()

        private val ledColor = Color.LIGHTGREEN
    }

    init {
        tempoDisplay {
            padding = box(20.px)
            backgroundColor = multi(Color.BLACK)
        }

        displayFont {
            fontFamily = "Fixed"
            fontWeight = FontWeight.BOLD
            textFill = ledColor
        }

        playTempo {
            fontSize = 6.em
        }
        playTempoUnit {
            padding = box(4.8.em, 0.em, 0.2.em, 0.em)
        }
        multiplier {
            padding = box(0.em, 1.em, 0.em, 0.em)
        }
    }
}

class MainView : View("Root") {
    val fontSize: DoubleProperty = SimpleDoubleProperty(10.0)

    override val root = vbox {
        val dynamic = vbox {
            vbox {
                addClass(MyStyle.tempoDisplay)

                hbox {
                    label("123") {
                        addClass(MyStyle.displayFont, MyStyle.playTempo)
                    }
                    label("bpm") {
                        addClass(MyStyle.displayFont, MyStyle.playTempoUnit)
                    }
                }

                hbox {
                    vbox {
                        addClass(MyStyle.multiplier)
                        label("multiplier") {
                            addClass(MyStyle.displayFont)
                        }
                        label("1.05x") {
                            addClass(MyStyle.displayFont)
                        }
                    }
                    vbox {
                        label("song tempo") {
                            addClass(MyStyle.displayFont)
                        }
                        label("120 bpm") {
                            addClass(MyStyle.displayFont)
                        }
                    }
                }
            }

            button("play")
            label("label text")
            textarea("Text area text\nand some numbers 1234567890")
        }
        hbox {
            label("Non-dynamic label")
        }

        dynamic.styleProperty().bind(Bindings.concat("-fx-font-size: ", fontSize.asString()))
    }
}

class LayoutProtoApp : App() {
    override val primaryView = MainView::class

    override fun start(stage: Stage) {
        with(stage) {
            super.start(this)
            importStylesheet(MyStyle::class)

            val view = find(MainView::class)
            view.fontSize.bind(scene.widthProperty().add(scene.heightProperty()).divide(50))
        }
    }
}

fun main(args: Array<String>) {
    launch<LayoutProtoApp>(args)
}
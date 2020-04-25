package midituutti

import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ToggleButton
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import tornadofx.*

fun EventTarget.mytogglebutton(
        text: String? = null,
        op: ToggleButton.() -> Unit = {}
) = togglebutton(text) {
    addClass(MyStyle.mytogglebutton)
    isSelected = false
    isFocusTraversable = false
    op()
}

fun EventTarget.mybutton(text: String = "", graphic: Node? = null, op: Button.() -> Unit = {}) =
        button(text, graphic) {
            addClass(MyStyle.mytogglebutton)
            isFocusTraversable = false
            op()
        }

class MyStyle : Stylesheet() {

    companion object {
        val root by cssclass()
        val button by cssclass()
        val mytogglebutton by cssclass()
        val tempoDisplay by cssclass()
        val displayFont by cssclass()
        val playTempo by cssclass()
        val playTempoUnit by cssclass()
        val multiplier by cssclass()

        private val ledColor = Color.LIGHTGREEN
    }

    init {
        root {
            padding = box(20.px)
            backgroundColor = multi(Color.BLACK)
        }

        mytogglebutton {
            backgroundColor = multi(c(10, 10, 10))
            textFill = Color.GRAY
            fontWeight = FontWeight.BOLD

            and(selected) {
                textFill = ledColor
            }
            and(pressed) {
                textFill = ledColor
            }
        }

        tempoDisplay {
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
    val fontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    override val root = vbox {
        addClass(MyStyle.root)

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

            hbox {
                vbox {
                    mytogglebutton("play")
                    hbox {
                        mybutton("<<")
                        mybutton("<")
                        mybutton(">")
                    }
                }
            }
        }

        dynamic.styleProperty().bind(Bindings.concat("-fx-font-size: ", fontSize.asString()))
    }
}

class LayoutProtoApp : App() {
    override val primaryView = MainView::class

    override fun start(stage: Stage) {
        with(stage) {
            minWidth = 400.0
            minHeight = 300.0

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
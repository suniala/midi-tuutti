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
    addClass(MyStyle.mybutton)
    isSelected = false
    isFocusTraversable = false
    op()
}

fun EventTarget.mybutton(text: String = "", graphic: Node? = null, op: Button.() -> Unit = {}) =
        button(text, graphic) {
            addClass(MyStyle.mybutton)
            isFocusTraversable = false
            op()
        }

class MyStyle : Stylesheet() {

    companion object {
        val root by cssclass()
        val mybutton by cssclass()
        val display by cssclass()
        val displayFont by cssclass()
        val playTempo by cssclass()
        val playTempoUnit by cssclass()
        val multiplier by cssclass()
        val controls by cssclass()

        private val ledColor = Color.LIGHTGREEN
    }

    init {
        root {
            padding = box(10.px)
        }

        mybutton {
            fontWeight = FontWeight.BOLD
        }

        display {
            padding = box(10.px)
            backgroundColor = multi(Color.BLACK)
            borderRadius = multi(box(10.px))
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

        controls {
            padding = box(10.px, 0.px)
        }
    }
}

class MainView : View("Root") {
    val fontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    override val root = vbox {
        addClass(MyStyle.root)

        val dynamic = vbox {
            vbox {
                addClass(MyStyle.display)

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
                addClass(MyStyle.controls)

                vbox {
                    spacing = 10.0

                    mytogglebutton("play") {
                        useMaxWidth = true
                    }

                    hbox {
                        spacing = 10.0
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
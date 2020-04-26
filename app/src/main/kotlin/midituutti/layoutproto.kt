package midituutti

import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ToggleButton
import javafx.scene.layout.Priority
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
        val displaySection by cssclass()
        val displayFont by cssclass()
        val displaySectionSeparator by cssclass()
        val display by cssclass()
        val displayMain by cssclass()
        val displaySectionTitle by cssclass()
        val controls by cssclass()

        private val colorDisplayBg = Color.BLACK
        private val colorDisplayText = Color.LIGHTGREEN
        private val padCommon = 0.5.em
    }

    init {
        root {
            padding = box(padCommon)
        }

        mybutton {
            fontWeight = FontWeight.BOLD
        }

        display {
            backgroundColor = multi(colorDisplayBg)
        }

        displaySection {
            padding = box(padCommon)
        }

        displaySectionSeparator {
            borderColor = multi(box(colorDisplayBg))
            borderWidth = multi(box(padCommon, 0.px, padCommon, 0.px))
            backgroundColor = multi(c(20, 20, 20))
            fitToHeight = true
            minWidth = 4.px
            maxWidth = 4.px
        }

        displayFont {
            fontFamily = "DejaVu Sans Mono"
            fontWeight = FontWeight.BOLD
            textFill = colorDisplayText
            fontSize = 0.8.em
        }

        displayMain {
            fontSize = 6.em
        }

        displaySectionTitle {
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
                    pane {
                        // spacer
                        hgrow = Priority.ALWAYS
                    }

                    vbox {
                        addClass(MyStyle.displaySection)

                        vbox {
                            label("bpm") {
                                addClass(MyStyle.displayFont, MyStyle.displaySectionTitle)
                            }
                            label(" 93") {
                                addClass(MyStyle.displayFont, MyStyle.displayMain)
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("adjust") {
                                    addClass(MyStyle.displayFont)
                                }
                                label("105 %") {
                                    addClass(MyStyle.displayFont)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("song tempo") {
                                    addClass(MyStyle.displayFont)
                                }
                                label("120 bpm") {
                                    addClass(MyStyle.displayFont)
                                }
                            }
                        }
                    }

                    pane {
                        addClass(MyStyle.displaySectionSeparator)
                    }

                    vbox {
                        addClass(MyStyle.displaySection)

                        vbox {
                            label("position") {
                                addClass(MyStyle.displayFont, MyStyle.displaySectionTitle)
                            }
                            label("  1") {
                                addClass(MyStyle.displayFont, MyStyle.displayMain)
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("play range") {
                                    addClass(MyStyle.displayFont)
                                }
                                label("120 ‒ 165") {
                                    addClass(MyStyle.displayFont)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("measures") {
                                    addClass(MyStyle.displayFont)
                                }
                                label("185") {
                                    addClass(MyStyle.displayFont)
                                }
                            }
                        }
                    }

                    pane {
                        // spacer
                        hgrow = Priority.ALWAYS
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
    // println(javafx.scene.text.Font.getFamilies())
    launch<LayoutProtoApp>(args)
}
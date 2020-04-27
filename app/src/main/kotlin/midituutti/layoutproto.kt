package midituutti

import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import midituutti.MyStyle.Companion.commonSpacing
import midituutti.MyStyle.Companion.fontRemControlButton
import midituutti.MyStyle.Companion.fontRemControlSliderButton
import midituutti.MyStyle.Companion.fontRemControlTitle
import midituutti.MyStyle.Companion.fontRemDisplayMain
import midituutti.MyStyle.Companion.fontRemDisplaySub
import tornadofx.*

fun EventTarget.mytogglebutton(
        text: String? = null,
        group: ToggleGroup? = null,
        op: ToggleButton.() -> Unit = {}
) = togglebutton(text = text, group = group) {
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

fun EventTarget.myslider(rootFontSize: DoubleProperty, op: Node.() -> Unit = {}) =
        hbox {
            style {
                spacing = commonSpacing
            }

            mybutton("<") {
                remFontBinding(fontRemControlSliderButton, rootFontSize)
            }
            slider(1, 100) {
                hgrow = Priority.ALWAYS
                remFontBinding(fontRemControlButton, rootFontSize)
            }
            mybutton(">") {
                remFontBinding(fontRemControlSliderButton, rootFontSize)
            }

            op()
        }

fun Node.remFontBinding(rem: Double, rootFontSize: DoubleProperty) =
        styleProperty().bind(Bindings.concat("-fx-font-size: ", rootFontSize.multiply(rem)))

class MyStyle : Stylesheet() {

    companion object {
        val root by cssclass()
        val mybutton by cssclass()
        val displaySection by cssclass()
        val displayFont by cssclass()
        val displaySectionSeparator by cssclass()
        val controlSectionSeparator by cssclass()
        val display by cssclass()
        val displayMain by cssclass()
        val displaySectionTitle by cssclass()
        val controls by cssclass()
        val controlTitle by cssclass()

        private val colorDisplayBg = Color.BLACK
        private val colorDisplayText = Color.LIGHTGREEN
        private val padCommon = 0.5.em
        val commonSpacing = 0.4.em

        const val fontRemDisplayMain = 6.0
        const val fontRemDisplaySub = 0.8
        const val fontRemControlTitle = 0.8
        const val fontRemControlButton = 0.8
        const val fontRemControlSliderButton = 0.5
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

        controlSectionSeparator {
            backgroundColor = multi(c(200, 200, 200))
            fitToHeight = true
            minWidth = 2.px
            maxWidth = 2.px
        }

        displayFont {
            fontFamily = "DejaVu Sans Mono"
            fontWeight = FontWeight.BOLD
            textFill = colorDisplayText
        }

        displayMain {
        }

        displaySectionTitle {
        }

        controls {
            padding = box(10.px, 0.px)
        }

        controlTitle {
        }
    }
}

class MainView : View("Root") {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

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
                                remFontBinding(fontRemDisplaySub, rootFontSize)
                            }
                            label(" 93") {
                                addClass(MyStyle.displayFont, MyStyle.displayMain)
                                remFontBinding(fontRemDisplayMain, rootFontSize)
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("adjust") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
                                }
                                label("105 %") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("song tempo") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
                                }
                                label("120 bpm") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
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
                                remFontBinding(fontRemDisplaySub, rootFontSize)
                            }
                            label("  1") {
                                addClass(MyStyle.displayFont, MyStyle.displayMain)
                                remFontBinding(fontRemDisplayMain, rootFontSize)
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("play range") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
                                }
                                label("120 ‒ 165") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("measures") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
                                }
                                label("185") {
                                    addClass(MyStyle.displayFont)
                                    remFontBinding(fontRemDisplaySub, rootFontSize)
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

                pane {
                    // spacer
                    hgrow = Priority.ALWAYS
                }

                vbox {
                    style {
                        spacing = commonSpacing
                    }

                    hbox {
                        style {
                            spacing = commonSpacing
                        }

                        vbox {
                            style {
                                spacing = commonSpacing
                            }

                            label("Play") {
                                addClass(MyStyle.controlTitle)
                                remFontBinding(fontRemControlTitle, rootFontSize)
                            }

                            mytogglebutton("Play") {
                                useMaxWidth = true
                                remFontBinding(fontRemControlButton, rootFontSize)
                            }

                            hbox {
                                style {
                                    spacing = commonSpacing
                                }
                                mybutton("<<") {
                                    remFontBinding(fontRemControlButton, rootFontSize)
                                }
                                mybutton("<") {
                                    remFontBinding(fontRemControlButton, rootFontSize)
                                }
                                mybutton(">") {
                                    remFontBinding(fontRemControlButton, rootFontSize)
                                }
                            }
                        }

                        pane {
                            addClass(MyStyle.controlSectionSeparator)
                        }

                        vbox {
                            style {
                                spacing = commonSpacing
                            }

                            label("Channels") {
                                addClass(MyStyle.controlTitle)
                                remFontBinding(fontRemControlTitle, rootFontSize)
                            }

                            vbox {
                                style {
                                    spacing = commonSpacing
                                }

                                mytogglebutton("Click off") {
                                    useMaxWidth = true
                                    remFontBinding(fontRemControlButton, rootFontSize)
                                }

                                mytogglebutton("Drums on") {
                                    useMaxWidth = true
                                    isSelected = true
                                    remFontBinding(fontRemControlButton, rootFontSize)
                                }
                            }
                        }

                        pane {
                            addClass(MyStyle.controlSectionSeparator)
                        }

                        vbox {
                            style {
                                spacing = commonSpacing
                            }

                            label("Tempo") {
                                addClass(MyStyle.controlTitle)
                                remFontBinding(fontRemControlTitle, rootFontSize)
                            }

                            vbox {
                                useMaxWidth = true
                                style {
                                    spacing = commonSpacing
                                }

                                hbox {
                                    style {
                                        spacing = commonSpacing
                                    }
                                    useMaxWidth = true

                                    val tg = togglegroup()
                                    mytogglebutton("Song", tg) {
                                        remFontBinding(fontRemControlButton, rootFontSize)
                                    }
                                    mytogglebutton("Fixed", tg) {
                                        remFontBinding(fontRemControlButton, rootFontSize)
                                    }
                                }

                                hbox {
                                    style {
                                        spacing = commonSpacing
                                    }
                                    mybutton("‒") {
                                        remFontBinding(fontRemControlButton, rootFontSize)
                                    }
                                    mybutton("O") {
                                        remFontBinding(fontRemControlButton, rootFontSize)
                                    }
                                    mybutton("+") {
                                        remFontBinding(fontRemControlButton, rootFontSize)
                                    }
                                }
                            }
                        }
                    }
                    myslider(rootFontSize) { }
                    myslider(rootFontSize) { }
                }
                pane {
                    // spacer
                    hgrow = Priority.ALWAYS
                }
            }
        }
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
            view.rootFontSize.bind(scene.widthProperty().add(scene.heightProperty()).divide(65))
        }
    }
}

fun main(args: Array<String>) {
    // println(javafx.scene.text.Font.getFamilies())
    launch<LayoutProtoApp>(args)
}
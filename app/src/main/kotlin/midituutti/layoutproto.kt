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
import midituutti.MyStyle.Companion.fontRemControlButton
import midituutti.MyStyle.Companion.fontRemControlSliderButton
import midituutti.MyStyle.Companion.fontRemControlTitle
import midituutti.MyStyle.Companion.fontRemDisplayMain
import midituutti.MyStyle.Companion.fontRemDisplaySub
import midituutti.MyStyle.Companion.padRemCommon
import midituutti.MyStyle.Companion.spacingRemCommon
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
            remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

            mybutton("<") {
                remBinding(CssProperty.fontSize, fontRemControlSliderButton, rootFontSize)
            }
            slider(1, 100) {
                hgrow = Priority.ALWAYS
                remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
            }
            mybutton(">") {
                remBinding(CssProperty.fontSize, fontRemControlSliderButton, rootFontSize)
            }

            op()
        }

@Suppress("EnumEntryName")
enum class CssProperty(val propName: String) {
    borderWidth("-fx-border-width"),
    fontSize("-fx-font-size"),
    padding("-fx-padding"),
    spacing("-fx-spacing"),
}

fun Node.remBinding(cssProperty: CssProperty, rem: Double, rootFontSize: DoubleProperty): Unit =
        styleProperty().bind(Bindings.concat("${cssProperty.propName}: ",
                rootFontSize.multiply(rem).stringBinding { pxSize -> "${pxSize}px" }))

fun Node.remBinding(cssProperty: CssProperty, f: (rem: (Double) -> String) -> String, rootFontSize: DoubleProperty): Unit =
        styleProperty().bind(Bindings.concat("${cssProperty.propName}: ",
                rootFontSize.stringBinding { rfs -> f(fun(rems: Double): String = "${rems * rfs as Double}px") }))

class MyStyle : Stylesheet() {

    companion object {
        val mybutton by cssclass()
        val displayFont by cssclass()
        val displaySectionSeparator by cssclass()
        val controlSectionSeparator by cssclass()
        val display by cssclass()

        private val colorDisplayBg = Color.BLACK
        private val colorDisplayText = Color.LIGHTGREEN

        const val fontRemDisplayMain = 6.0
        const val fontRemDisplaySub = 0.8
        const val fontRemControlTitle = 0.8
        const val fontRemControlButton = 0.8
        const val fontRemControlSliderButton = 0.5
        const val padRemCommon = 0.5
        const val spacingRemCommon = 0.4
    }

    init {
        mybutton {
            fontWeight = FontWeight.BOLD
        }

        display {
            backgroundColor = multi(colorDisplayBg)
        }

        displaySectionSeparator {
            borderColor = multi(box(colorDisplayBg))
            backgroundColor = multi(c(20, 20, 20))
            minWidth = 4.px
            maxWidth = 4.px
        }

        controlSectionSeparator {
            backgroundColor = multi(c(200, 200, 200))
            minWidth = 2.px
            maxWidth = 2.px
        }

        displayFont {
            fontFamily = "DejaVu Sans Mono"
            fontWeight = FontWeight.BOLD
            textFill = colorDisplayText
        }
    }
}

class MainView : View("Root") {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    override val root = borderpane() {
        top = menubar {
            menu("File") {
                item("Open", "Shortcut+O").action { }
                separator()
                item("Quit")
            }
        }

        bottom = vbox {
            remBinding(CssProperty.padding, padRemCommon, rootFontSize)

            vbox {
                addClass(MyStyle.display)

                hbox {
                    pane {
                        // spacer
                        hgrow = Priority.ALWAYS
                    }

                    vbox {
                        remBinding(CssProperty.padding, padRemCommon, rootFontSize)

                        vbox {
                            label("bpm") {
                                addClass(MyStyle.displayFont)
                                remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                            }
                            label(" 93") {
                                addClass(MyStyle.displayFont)
                                remBinding(CssProperty.fontSize, fontRemDisplayMain, rootFontSize)
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("adjust") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                                label("105 %") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("song tempo") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                                label("120 bpm") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                            }
                        }
                    }

                    pane {
                        addClass(MyStyle.displaySectionSeparator)
                        remBinding(CssProperty.borderWidth,
                                fun(rem: (Double) -> String): String = "${rem(padRemCommon)} 0px ${rem(padRemCommon)} 0px",
                                rootFontSize)
                    }

                    vbox {
                        remBinding(CssProperty.padding, padRemCommon, rootFontSize)

                        vbox {
                            label("position") {
                                addClass(MyStyle.displayFont)
                                remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                            }
                            label("  1") {
                                addClass(MyStyle.displayFont)
                                remBinding(CssProperty.fontSize, fontRemDisplayMain, rootFontSize)
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("play range") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                                label("120 ‒ 165") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("measures") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
                                }
                                label("185") {
                                    addClass(MyStyle.displayFont)
                                    remBinding(CssProperty.fontSize, fontRemDisplaySub, rootFontSize)
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
                remBinding(CssProperty.padding, padRemCommon, rootFontSize)

                pane {
                    // spacer
                    hgrow = Priority.ALWAYS
                }

                vbox {
                    remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                    hbox {
                        remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                        vbox {
                            remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                            label("Play") {
                                remBinding(CssProperty.fontSize, fontRemControlTitle, rootFontSize)
                            }

                            mytogglebutton("Play") {
                                useMaxWidth = true
                                remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                            }

                            hbox {
                                remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)
                                mybutton("<<") {
                                    remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                }
                                mybutton("<") {
                                    remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                }
                                mybutton(">") {
                                    remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                }
                            }
                        }

                        pane {
                            addClass(MyStyle.controlSectionSeparator)
                        }

                        vbox {
                            remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                            label("Channels") {
                                remBinding(CssProperty.fontSize, fontRemControlTitle, rootFontSize)
                            }

                            vbox {
                                remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                                mytogglebutton("Click off") {
                                    useMaxWidth = true
                                    remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                }

                                mytogglebutton("Drums on") {
                                    useMaxWidth = true
                                    isSelected = true
                                    remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                }
                            }
                        }

                        pane {
                            addClass(MyStyle.controlSectionSeparator)
                        }

                        vbox {
                            remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                            label("Tempo") {
                                remBinding(CssProperty.fontSize, fontRemControlTitle, rootFontSize)
                            }

                            vbox {
                                remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                                hbox {
                                    remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)

                                    val tg = togglegroup()
                                    mytogglebutton("Song", tg) {
                                        remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                    }
                                    mytogglebutton("Fixed", tg) {
                                        remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                    }
                                }

                                hbox {
                                    remBinding(CssProperty.spacing, spacingRemCommon, rootFontSize)
                                    mybutton("‒") {
                                        remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                    }
                                    mybutton("O") {
                                        hgrow = Priority.ALWAYS
                                        useMaxWidth = true
                                        remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
                                    }
                                    mybutton("+") {
                                        remBinding(CssProperty.fontSize, fontRemControlButton, rootFontSize)
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

        shortcut("F11") { primaryStage.isFullScreen = true }
    }
}

class LayoutProtoApp : App() {
    override val primaryView = MainView::class

    private val preferredHeight = 400.0
    private val preferredWidth = preferredHeight * 1.2

    override fun start(stage: Stage) {
        with(stage) {
            super.start(this)
            importStylesheet(MyStyle::class)

            val view = find(MainView::class)
            view.rootFontSize.bind(scene.heightProperty().divide(22))

            // Set dimensions after view has been initialized so as to make view contents scale according to
            // window dimensions.
            height = preferredHeight
            width = preferredWidth
            isResizable = false

            /*
            // Limit window aspect ratio. Basically works but only allows resizing vertically. Also, maximizing
            // the window does not work.
            minHeight = 300.0
            minWidth = minHeight * 1.2
            val w: DoubleBinding = stage.heightProperty().multiply(1.2)
            stage.minWidthProperty().bind(w)
            stage.maxWidthProperty().bind(w)
             */
        }
    }
}

fun main(args: Array<String>) {
    // println(javafx.scene.text.Font.getFamilies())
    launch<LayoutProtoApp>(args)
}
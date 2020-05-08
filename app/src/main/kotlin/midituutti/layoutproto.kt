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
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import midituutti.MyStyle.Companion.fontRemControlButton
import midituutti.MyStyle.Companion.fontRemControlSliderButton
import midituutti.MyStyle.Companion.fontRemControlTitle
import midituutti.MyStyle.Companion.fontRemDisplayMain
import midituutti.MyStyle.Companion.fontRemDisplaySub
import midituutti.MyStyle.Companion.fontRemDisplayTimeSignature
import midituutti.MyStyle.Companion.padRemCommon
import midituutti.MyStyle.Companion.spacingRemCommon
import midituutti.MyStyle.Companion.widthTimeSigHead
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
            style(rootFontSize) { prop(spacing, spacingRemCommon) }

            mybutton("<") {
                style(rootFontSize) { prop(fontSize, fontRemControlSliderButton) }
            }
            slider(1, 100) {
                hgrow = Priority.ALWAYS
                style(rootFontSize) { prop(fontSize, fontRemControlButton) }
            }
            mybutton(">") {
                style(rootFontSize) { prop(fontSize, fontRemControlSliderButton) }
            }

            op()
        }

class RemStyle(private val list: MutableList<(Double) -> String>) {
    data class CssProperty(val propName: String)

    val borderWidth = CssProperty("-fx-border-width")
    val fontSize = CssProperty("-fx-font-size")
    val minWidth = CssProperty("-fx-min-width")
    val padding = CssProperty("-fx-padding")
    val spacing = CssProperty("-fx-spacing")

    fun prop(cssProperty: CssProperty, rem: Double) {
        list.add(fun(rfs: Double): String = "${cssProperty.propName}: ${rem * rfs}px;")
    }

    fun prop(cssProperty: CssProperty, f: (rem: (Double) -> String) -> String) {
        list.add(fun(rfs: Double): String = "${cssProperty.propName}: ${f(fun(rems: Double): String = "${rems * rfs}px")};")
    }
}

fun Node.style(rootFontSize: DoubleProperty, op: RemStyle.() -> Unit = {}): RemStyle {
    val cssPropFunctions = mutableListOf<(Double) -> String>()
    val remStyle = RemStyle(cssPropFunctions)
    remStyle.run(op)

    val propBindings = cssPropFunctions.map { f ->
        rootFontSize.stringBinding { f(it as Double) }
    }.toTypedArray()
    styleProperty().bind(Bindings.concat(*propBindings))

    return remStyle
}

class MyStyle : Stylesheet() {

    companion object {
        val mybutton by cssclass()
        val displayFont by cssclass()
        val displaySectionSeparator by cssclass()
        val timeSignatureValue by cssclass()
        val timeSignatureSeparator by cssclass()
        val controlSectionSeparator by cssclass()
        val display by cssclass()

        private val colorDisplayBg = Color.BLACK
        private val colorDisplayText = Color.LIGHTGREEN

        const val fontRemDisplayMain = 6.0
        const val fontRemDisplayTimeSignature = 2.8
        const val fontRemDisplaySub = 0.8
        const val widthTimeSigHead = 5.0 * fontRemDisplaySub
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

        timeSignatureValue {
            textAlignment = TextAlignment.CENTER
        }

        timeSignatureSeparator {
            backgroundColor = multi(colorDisplayText)
            minHeight = 2.px
            maxHeight = 2.px
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
            style(rootFontSize) { prop(padding, padRemCommon) }

            vbox {
                addClass(MyStyle.display)

                hbox {
                    spacer()

                    vbox {
                        style(rootFontSize) { prop(padding, padRemCommon) }

                        vbox {
                            label("bpm") {
                                addClass(MyStyle.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                            label(" 93") {
                                addClass(MyStyle.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplayMain) }
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("adjust") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("105 %") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("song tempo") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("120 bpm") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                            }
                        }
                    }

                    pane {
                        addClass(MyStyle.displaySectionSeparator)
                        style(rootFontSize) {
                            prop(borderWidth,
                                    fun(rem: (Double) -> String): String = "${rem(padRemCommon)} 0px ${rem(padRemCommon)} 0px")
                        }
                    }

                    vbox {
                        style(rootFontSize) {
                            prop(padding, padRemCommon)
                            prop(minWidth, widthTimeSigHead)
                        }
                        label("timesig") {
                            addClass(MyStyle.displayFont)
                            style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                        }
                        vbox {
                            addClass(MyStyle.timeSignatureValue)
                            hbox {
                                spacer()
                                label("12") {
                                    useMaxWidth = true
                                    addClass(MyStyle.displayFont, MyStyle.timeSignatureValue)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplayTimeSignature) }
                                }
                                spacer()
                            }
                            pane {
                                addClass(MyStyle.timeSignatureSeparator)
                            }
                            hbox {
                                spacer()
                                label("4") {
                                    useMaxWidth = true
                                    addClass(MyStyle.displayFont, MyStyle.timeSignatureValue)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplayTimeSignature) }
                                }
                                spacer()
                            }
                        }

                        spacer()

                        vbox {
                            hgrow = Priority.ALWAYS
                            alignment = Pos.TOP_LEFT

                            label("next") {
                                addClass(MyStyle.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                            label("4/4") {
                                addClass(MyStyle.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                        }
                    }

                    pane {
                        addClass(MyStyle.displaySectionSeparator)
                        style(rootFontSize) {
                            prop(borderWidth,
                                    fun(rem: (Double) -> String): String = "${rem(padRemCommon)} 0px ${rem(padRemCommon)} 0px")
                        }
                    }

                    vbox {
                        style(rootFontSize) { prop(padding, padRemCommon) }

                        vbox {
                            label("position") {
                                addClass(MyStyle.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                            label("  1") {
                                addClass(MyStyle.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplayMain) }
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("play range") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("120 ‒ 165") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("measures") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("185") {
                                    addClass(MyStyle.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                            }
                        }
                    }

                    spacer()
                }
            }

            hbox {
                style(rootFontSize) { prop(padding, padRemCommon) }

                spacer()

                vbox {
                    style(rootFontSize) { prop(spacing, spacingRemCommon) }

                    hbox {
                        style(rootFontSize) { prop(spacing, spacingRemCommon) }

                        vbox {
                            style(rootFontSize) { prop(spacing, spacingRemCommon) }

                            label("Play") {
                                style(rootFontSize) { prop(fontSize, fontRemControlTitle) }
                            }

                            mytogglebutton("Play") {
                                useMaxWidth = true
                                style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                            }

                            hbox {
                                style(rootFontSize) { prop(spacing, spacingRemCommon) }
                                mybutton("<<") {
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                                mybutton("<") {
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                                mybutton(">") {
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                            }
                        }

                        pane {
                            addClass(MyStyle.controlSectionSeparator)
                        }

                        vbox {
                            style(rootFontSize) { prop(spacing, spacingRemCommon) }

                            label("Channels") {
                                style(rootFontSize) { prop(fontSize, fontRemControlTitle) }
                            }

                            vbox {
                                style(rootFontSize) { prop(spacing, spacingRemCommon) }

                                mytogglebutton("Click off") {
                                    useMaxWidth = true
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }

                                mytogglebutton("Drums on") {
                                    useMaxWidth = true
                                    isSelected = true
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                            }
                        }

                        pane {
                            addClass(MyStyle.controlSectionSeparator)
                        }

                        vbox {
                            style(rootFontSize) { prop(spacing, spacingRemCommon) }

                            label("Tempo") {
                                style(rootFontSize) { prop(fontSize, fontRemControlTitle) }
                            }

                            vbox {
                                style(rootFontSize) { prop(spacing, spacingRemCommon) }

                                hbox {
                                    style(rootFontSize) { prop(spacing, spacingRemCommon) }

                                    val tg = togglegroup()
                                    mytogglebutton("Song", tg) {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                    mytogglebutton("Fixed", tg) {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                }

                                hbox {
                                    style(rootFontSize) { prop(spacing, spacingRemCommon) }
                                    mybutton("‒") {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                    mybutton("O") {
                                        hgrow = Priority.ALWAYS
                                        useMaxWidth = true
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                    mybutton("+") {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                }
                            }
                        }
                    }
                    myslider(rootFontSize) { }
                    myslider(rootFontSize) { }
                }

                spacer()
            }
        }

        shortcut("F11") { primaryStage.isFullScreen = true }
    }
}

class LayoutProtoApp : App() {
    override val primaryView = MainView::class

    private val preferredHeight = 400.0
    private val preferredWidth = preferredHeight * 1.4

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
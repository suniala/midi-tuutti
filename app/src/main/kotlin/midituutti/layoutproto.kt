package midituutti

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.layout.Priority
import javafx.stage.Stage
import midituutti.Style.Companion.blink
import midituutti.Style.Companion.fontRemControlButton
import midituutti.Style.Companion.fontRemControlTitle
import midituutti.Style.Companion.fontRemDisplayMain
import midituutti.Style.Companion.fontRemDisplaySub
import midituutti.Style.Companion.fontRemDisplayTimeSignature
import midituutti.Style.Companion.padRemCommon
import midituutti.Style.Companion.spacingRemCommon
import midituutti.Style.Companion.widthTimeSigHead
import midituutti.components.measureRangeControl
import midituutti.components.nonFocusableButton
import midituutti.components.nonFocusableToggleButton
import tornadofx.*

class MainView : View("Root") {
    val rootFontSize: DoubleProperty = SimpleDoubleProperty(50.0)

    private val measureRange = SimpleObjectProperty<Pair<Int, Int>>()

    private val measureRangeChanging = SimpleBooleanProperty(false)

    private var measureRangeBlink: BooleanProperty by singleAssign()

    override val root = borderpane() {
        top = menubar {
            menu("File") {
                item("Open", "Shortcut+O").action {
                    measureRange.value = Pair(21, 42)
                }
                separator()
                item("Quit")
            }
        }

        bottom = vbox {
            style(rootFontSize) { prop(padding, padRemCommon) }

            vbox {
                addClass(Style.display)

                hbox {
                    spacer()

                    vbox {
                        style(rootFontSize) { prop(padding, padRemCommon) }

                        vbox {
                            label("position") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                            label("  1") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplayMain) }
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("play range") {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                    textProperty().bind(measureRange.stringBinding { v ->
                                        (v ?: Pair("?", "?")).let { (s, e) -> "$s ‒ $e" }
                                    })
                                    measureRangeBlink = nodeBlinker(this, blink)
                                    measureRangeBlink.bind(measureRangeChanging)
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("measures") {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("185") {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                            }
                        }
                    }

                    pane {
                        addClass(Style.displaySectionSeparator)
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
                            addClass(Style.displayFont)
                            style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                        }
                        vbox {
                            addClass(Style.timeSignatureValue)
                            hbox {
                                spacer()
                                label("12") {
                                    useMaxWidth = true
                                    addClass(Style.displayFont, Style.timeSignatureValue)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplayTimeSignature) }
                                }
                                spacer()
                            }
                            pane {
                                addClass(Style.timeSignatureSeparator)
                            }
                            hbox {
                                spacer()
                                label("4") {
                                    useMaxWidth = true
                                    addClass(Style.displayFont, Style.timeSignatureValue)
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
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                            label("4/4") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                        }
                    }

                    pane {
                        addClass(Style.displaySectionSeparator)
                        style(rootFontSize) {
                            prop(borderWidth,
                                    fun(rem: (Double) -> String): String = "${rem(padRemCommon)} 0px ${rem(padRemCommon)} 0px")
                        }
                    }

                    vbox {
                        style(rootFontSize) { prop(padding, padRemCommon) }

                        vbox {
                            label("bpm") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                            }
                            label(" 93") {
                                addClass(Style.displayFont)
                                style(rootFontSize) { prop(fontSize, fontRemDisplayMain) }
                            }
                        }

                        hbox {
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_LEFT

                                label("adjust") {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("105 %") {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                            }
                            vbox {
                                hgrow = Priority.ALWAYS
                                alignment = Pos.TOP_RIGHT

                                label("song tempo") {
                                    addClass(Style.displayFont)
                                    style(rootFontSize) { prop(fontSize, fontRemDisplaySub) }
                                }
                                label("120 bpm") {
                                    addClass(Style.displayFont)
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

                            nonFocusableToggleButton("Play") {
                                useMaxWidth = true
                                style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                            }

                            hbox {
                                style(rootFontSize) { prop(spacing, spacingRemCommon) }
                                nonFocusableButton("<<") {
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                                nonFocusableButton("<") {
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                                nonFocusableButton(">") {
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                            }
                        }

                        pane {
                            addClass(Style.controlSectionSeparator)
                        }

                        vbox {
                            style(rootFontSize) { prop(spacing, spacingRemCommon) }

                            label("Channels") {
                                style(rootFontSize) { prop(fontSize, fontRemControlTitle) }
                            }

                            vbox {
                                style(rootFontSize) { prop(spacing, spacingRemCommon) }

                                nonFocusableToggleButton("Click off") {
                                    useMaxWidth = true
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }

                                nonFocusableToggleButton("Drums on") {
                                    useMaxWidth = true
                                    isSelected = true
                                    style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                }
                            }
                        }

                        pane {
                            addClass(Style.controlSectionSeparator)
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
                                    nonFocusableToggleButton("Song", tg) {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                    nonFocusableToggleButton("Fixed", tg) {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                }

                                hbox {
                                    style(rootFontSize) { prop(spacing, spacingRemCommon) }
                                    nonFocusableButton("‒") {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                    nonFocusableButton("O") {
                                        hgrow = Priority.ALWAYS
                                        useMaxWidth = true
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                    nonFocusableButton("+") {
                                        style(rootFontSize) { prop(fontSize, fontRemControlButton) }
                                    }
                                }
                            }
                        }
                    }

                    measureRangeControl(rootFontSize).run {
                        measureRange.bindBidirectional(valueProperty())
                        measureRangeChanging.bind(valueChangingProperty())
                    }
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
            importStylesheet(Style::class)

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
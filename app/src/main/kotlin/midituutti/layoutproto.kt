package midituutti

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Slider
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import midituutti.MyStyle.Companion.blink
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

interface BidirectionalBridge {
    fun leftSideObservables(): Collection<ObservableValue<*>>
    fun rightSideObservables(): Collection<ObservableValue<*>>
    fun leftSideChanged()
    fun rightSideChanged()
}

fun bindBidirectional(bridge: BidirectionalBridge) {
    fun flaggedChangeListener(onChange: () -> Unit) =
            object : ChangeListener<Any> {
                private var alreadyCalled = false
                override fun changed(observable: ObservableValue<out Any>?, oldValue: Any?, newValue: Any?) {
                    if (!alreadyCalled) {
                        try {
                            alreadyCalled = true
                            onChange()
                        } finally {
                            alreadyCalled = false
                        }
                    }
                }
            }

    bridge.leftSideObservables().forEach { p -> p.addListener(flaggedChangeListener(bridge::leftSideChanged)) }
    bridge.rightSideObservables().forEach { p -> p.addListener(flaggedChangeListener(bridge::rightSideChanged)) }
}

interface MeasureSlider {
    fun valueProperty(): IntegerProperty
    fun valueChangingProperty(): BooleanProperty
}

fun EventTarget.measureSlider(rootFontSize: DoubleProperty, op: Node.() -> Unit = {}): MeasureSlider {
    var theSlider: Slider by singleAssign()
    val value = SimpleIntegerProperty(1)

    hbox {
        style(rootFontSize) { prop(spacing, spacingRemCommon) }

        mybutton("<") {
            style(rootFontSize) { prop(fontSize, fontRemControlSliderButton) }
            action {
                value.minusAssign(1)
            }
        }
        theSlider = slider(1, 100) {
            hgrow = Priority.ALWAYS
            style(rootFontSize) { prop(fontSize, fontRemControlButton) }
        }
        mybutton(">") {
            style(rootFontSize) { prop(fontSize, fontRemControlSliderButton) }
            action {
                value.plusAssign(1)
            }
        }

        op()
    }

    theSlider.valueProperty().bindBidirectional(value)

    return object : MeasureSlider {
        override fun valueProperty(): IntegerProperty = value
        override fun valueChangingProperty(): BooleanProperty = theSlider.valueChangingProperty()
    }
}

interface MeasureRangeControl {
    fun valueProperty(): ObjectProperty<Pair<Int, Int>>
    fun valueChangingProperty(): BooleanProperty
}

fun EventTarget.measureRangeControl(rootFontSize: DoubleProperty, op: Node.() -> Unit = {}): MeasureRangeControl {
    var startSlider: MeasureSlider by singleAssign()
    var endSlider: MeasureSlider by singleAssign()

    vbox {
        startSlider = measureSlider(rootFontSize)
        endSlider = measureSlider(rootFontSize)
        op()
    }

    val range = SimpleObjectProperty<Pair<Int, Int>>(null)

    bindBidirectional(object : BidirectionalBridge {
        override fun leftSideObservables(): Collection<ObservableValue<*>> = listOf(
                startSlider.valueProperty(),
                endSlider.valueProperty())

        override fun rightSideObservables(): Collection<ObservableValue<*>> = listOf(range)

        override fun leftSideChanged() {
            range.value = Pair(startSlider.valueProperty().value, endSlider.valueProperty().value)
        }

        override fun rightSideChanged() {
            val newRange = range.value
            startSlider.valueProperty().value = newRange.first
            endSlider.valueProperty().value = newRange.second
        }
    })

    startSlider.valueProperty().onChange { value ->
        run {
            if (endSlider.valueProperty().value - value < 0) endSlider.valueProperty().value = value
        }
    }
    endSlider.valueProperty().onChange { value ->
        run {
            if (value - startSlider.valueProperty().value < 0) startSlider.valueProperty().value = value
        }
    }

    val valueChanging = SimpleBooleanProperty(false)
    valueChanging.bind(startSlider.valueChangingProperty().or(endSlider.valueChangingProperty()))

    return object : MeasureRangeControl {
        override fun valueProperty(): ObjectProperty<Pair<Int, Int>> = range
        override fun valueChangingProperty(): BooleanProperty = valueChanging
    }
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
        val blink by csspseudoclass()

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

            and(blink) {
                textFill = colorDisplayBg
            }
        }
    }
}

fun nodeBlinker(node: Node, blinkPseudoClass: CssRule): BooleanProperty {
    val javaFxPseudoClass = PseudoClass.getPseudoClass(blinkPseudoClass.name)
    val timeline = Timeline(
            KeyFrame(0.5.seconds, EventHandler { node.pseudoClassStateChanged(javaFxPseudoClass, true) }),
            KeyFrame(1.0.seconds, EventHandler { node.pseudoClassStateChanged(javaFxPseudoClass, false) })
    )
    timeline.cycleCount = Animation.INDEFINITE

    val blink = SimpleBooleanProperty(false)
    blink.onChange { blinkOn ->
        run {
            if (blinkOn) {
                timeline.play()
            } else {
                timeline.stop()
                node.pseudoClassStateChanged(javaFxPseudoClass, false)
            }
        }
    }
    return blink
}

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
                addClass(MyStyle.display)

                hbox {
                    spacer()

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
                                label {
                                    addClass(MyStyle.displayFont)
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
package midituutti.components

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import midituutti.BidirectionalBridge
import midituutti.Style
import midituutti.bindBidirectional
import midituutti.style
import tornadofx.*
import kotlin.math.roundToInt

interface MeasureSlider {
    fun valueProperty(): IntegerProperty
    fun valueChangingProperty(): BooleanProperty
    fun setBounds(bounds: Pair<Int, Int>?)
}

class NonFocusableSlider() : Slider() {
    override fun requestFocus() {
        // Ignore focus request.
    }
}

fun EventTarget.nonFocusableSlider(
        min: Number? = null,
        max: Number? = null,
        op: Slider.() -> Unit = {}
) = NonFocusableSlider().attachTo(this, op) {
    if (min != null) it.min = min.toDouble()
    if (max != null) it.max = max.toDouble()
}

fun EventTarget.measureSlider(rootFontSize: DoubleProperty, op: Node.() -> Unit = {}): MeasureSlider {
    var theSlider: Slider by singleAssign()
    val value = SimpleIntegerProperty(1)

    hbox {
        style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

        nonFocusableButton("<") {
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            action {
                value.minusAssign(1)
            }
        }
        theSlider = nonFocusableSlider(1, 100) {
            hgrow = Priority.ALWAYS
            style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
        }
        nonFocusableButton(">") {
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            action {
                value.plusAssign(1)
            }
        }

        op()
    }

    // Rounding to int seems result in a slider that snaps nicely onto int values
    theSlider.valueProperty().mutateOnChange { d -> (d as Double).roundToInt() }
    theSlider.valueProperty().bindBidirectional(value)

    return object : MeasureSlider {
        override fun setBounds(bounds: Pair<Int, Int>?) {
            theSlider.min = (bounds?.first ?: 1).toDouble()
            theSlider.max = (bounds?.second ?: 100).toDouble()
        }

        override fun valueProperty(): IntegerProperty = value
        override fun valueChangingProperty(): BooleanProperty = theSlider.valueChangingProperty()
    }
}

interface MeasureRangeControl {
    fun valueProperty(): ObjectProperty<Pair<Int, Int>>
    fun valueChangingProperty(): BooleanProperty
    fun boundsProperty(): ObjectProperty<Pair<Int, Int>>
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

    val bounds = SimpleObjectProperty<Pair<Int, Int>>()
    bounds.onChange { newBounds ->
        run {
            startSlider.setBounds(newBounds)
            endSlider.setBounds(newBounds)
        }
    }

    return object : MeasureRangeControl {
        override fun boundsProperty(): ObjectProperty<Pair<Int, Int>> = bounds
        override fun valueProperty(): ObjectProperty<Pair<Int, Int>> = range
        override fun valueChangingProperty(): BooleanProperty = valueChanging
    }
}
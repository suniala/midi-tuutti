package midituutti.components

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.IntegerProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.Node
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import midituutti.BidirectionalBridge
import midituutti.Style
import midituutti.bindBidirectional
import midituutti.style
import kotlin.math.roundToInt

interface MeasureSlider {
    fun valueProperty(): IntegerProperty
    fun valueChangingProperty(): BooleanProperty
    fun setBounds(bounds: Pair<Int, Int>?)
}

fun Pane.measureSlider(rootFontSize: DoubleProperty, op: Node.() -> Unit = {}): MeasureSlider {
    lateinit var theSlider: Slider
    val value = SimpleIntegerProperty(1)

    val box = HBox().also { box ->
        box.style(rootFontSize) { prop(spacing, Style.spacingRemCommon) }

        box.nonFocusableButton("<") {
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            setOnAction { value.value -= 1 }
        }
        theSlider = box.nonFocusableSlider(1, 100) {
            style(rootFontSize) { prop(fontSize, Style.fontRemControlButton) }
        }
        HBox.setHgrow(theSlider, Priority.ALWAYS)
        box.nonFocusableButton(">") {
            style(rootFontSize) { prop(fontSize, Style.fontRemControlSliderButton) }
            setOnAction { value.value += 1 }
        }

        box.op()
    }
    children.add(box)

    // Rounding to int seems result in a slider that snaps nicely onto int values
    theSlider.valueProperty().addListener { _, _, newValue ->
        val rounded = newValue.toDouble().roundToInt()
        if (rounded.toDouble() != newValue.toDouble()) theSlider.value = rounded.toDouble()
    }
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

fun Pane.measureRangeControl(rootFontSize: DoubleProperty, op: Node.() -> Unit = {}): MeasureRangeControl {
    lateinit var startSlider: MeasureSlider
    lateinit var endSlider: MeasureSlider

    val box = VBox().also { box ->
        startSlider = box.measureSlider(rootFontSize)
        endSlider = box.measureSlider(rootFontSize)
        box.op()
    }
    children.add(box)

    val range = SimpleObjectProperty<Pair<Int, Int>>(null)

    bindBidirectional(object : BidirectionalBridge {
        override fun leftSideObservables(): Collection<ObservableValue<*>> = listOf(
            startSlider.valueProperty(),
            endSlider.valueProperty()
        )

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

    startSlider.valueProperty().addListener { _, _, newValue ->
        val v = newValue.toInt()
        if (endSlider.valueProperty().value - v < 0) endSlider.valueProperty().value = v
    }
    endSlider.valueProperty().addListener { _, _, newValue ->
        val v = newValue.toInt()
        if (v - startSlider.valueProperty().value < 0) startSlider.valueProperty().value = v
    }

    val valueChanging = SimpleBooleanProperty(false)
    valueChanging.bind(startSlider.valueChangingProperty().or(endSlider.valueChangingProperty()))

    val bounds = SimpleObjectProperty<Pair<Int, Int>>()
    bounds.addListener { _, _, newBounds ->
        startSlider.setBounds(newBounds)
        endSlider.setBounds(newBounds)
    }

    return object : MeasureRangeControl {
        override fun boundsProperty(): ObjectProperty<Pair<Int, Int>> = bounds
        override fun valueProperty(): ObjectProperty<Pair<Int, Int>> = range
        override fun valueChangingProperty(): BooleanProperty = valueChanging
    }
}

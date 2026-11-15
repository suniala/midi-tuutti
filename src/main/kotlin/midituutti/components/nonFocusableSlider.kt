package midituutti.components

import javafx.scene.control.Slider
import javafx.scene.layout.Pane

class NonFocusableSlider : Slider() {
    override fun requestFocus() {
        // Ignore focus request.
    }
}

fun Pane.nonFocusableSlider(
    min: Number? = null,
    max: Number? = null,
    op: Slider.() -> Unit = {}
): NonFocusableSlider {
    val slider = NonFocusableSlider().also { slider ->
        if (min != null) slider.min = min.toDouble()
        if (max != null) slider.max = max.toDouble()
        slider.op()
    }
    children.add(slider)
    return slider
}

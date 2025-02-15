package midituutti.components

import javafx.event.EventTarget
import javafx.scene.control.Slider
import tornadofx.attachTo

class NonFocusableSlider : Slider() {
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
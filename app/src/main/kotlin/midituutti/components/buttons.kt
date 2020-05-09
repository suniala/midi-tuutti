package midituutti.components

import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import midituutti.Style
import tornadofx.*

fun EventTarget.nonFocusableToggleButton(
        text: String? = null,
        group: ToggleGroup? = null,
        selectFirst: Boolean = true,
        value: Any? = null,
        op: ToggleButton.() -> Unit = {}
) = togglebutton(text = text, group = group, selectFirst = selectFirst, value = value) {
    addClass(Style.button)
    isSelected = false
    isFocusTraversable = false
    op()
}

fun EventTarget.nonFocusableButton(text: String = "", graphic: Node? = null, op: Button.() -> Unit = {}) =
        button(text, graphic) {
            addClass(Style.button)
            isFocusTraversable = false
            op()
        }
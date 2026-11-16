package midituutti.components

import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Pane
import midituutti.Style
import midituutti.TOGGLE_GROUP_VALUE_KEY

fun Pane.nonFocusableToggleButton(
    text: String? = null,
    group: ToggleGroup? = null,
    selectFirst: Boolean = false,
    value: Any? = null,
    op: ToggleButton.() -> Unit = {}
): ToggleButton {
    val button = ToggleButton(text).also { button ->
        button.toggleGroup = group
        // Read back by ToggleGroup.selectedValueProperty() in utils.kt.
        button.properties[TOGGLE_GROUP_VALUE_KEY] = value
        button.styleClass.add(Style.button)
        button.isSelected = selectFirst
        button.isFocusTraversable = false
        button.op()
    }
    children.add(button)
    return button
}

fun Pane.nonFocusableButton(text: String = "", graphic: Node? = null, op: Button.() -> Unit = {}): Button {
    val button = Button(text, graphic).also { button ->
        button.styleClass.add(Style.button)
        button.isFocusTraversable = false
        button.op()
    }
    children.add(button)
    return button
}

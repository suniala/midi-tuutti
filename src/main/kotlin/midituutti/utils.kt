package midituutti

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.binding.StringBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.input.KeyCombination
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.Duration

/** Key under which a toggle's value is stored in its properties map. */
const val TOGGLE_GROUP_VALUE_KEY = "midituutti.toggleGroupValue"

@Suppress("UNCHECKED_CAST")
fun <T> ToggleGroup.selectedValueProperty(): ObjectProperty<T> {
    val prop = SimpleObjectProperty<T>(selectedToggle?.properties?.get(TOGGLE_GROUP_VALUE_KEY) as T?)
    selectedToggleProperty().addListener { _, _, toggle ->
        prop.value = toggle?.properties?.get(TOGGLE_GROUP_VALUE_KEY) as T?
    }
    // Write back: setting the property selects the toggle holding that value.
    prop.addListener { _, _, newValue ->
        val toggleToSelect = toggles.find { it.properties?.get(TOGGLE_GROUP_VALUE_KEY) == newValue }
        if (toggleToSelect != null && toggleToSelect != selectedToggle) {
            selectToggle(toggleToSelect)
        }
    }
    return prop
}

fun <T> ObservableValue<T>.nonNullStringBinding(vararg dependencies: Observable, op: (T) -> String): StringBinding =
    Bindings.createStringBinding({ op(value) }, this, *dependencies)

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

fun nodeBlinker(node: Node, blinkPseudoClassName: String): BooleanProperty {
    val javaFxPseudoClass = PseudoClass.getPseudoClass(blinkPseudoClassName)
    val timeline = Timeline(
        KeyFrame(Duration.seconds(0.5), { node.pseudoClassStateChanged(javaFxPseudoClass, true) }),
        KeyFrame(Duration.seconds(1.0), { node.pseudoClassStateChanged(javaFxPseudoClass, false) })
    ).also { timeline -> timeline.cycleCount = Animation.INDEFINITE }

    return SimpleBooleanProperty(false).also { blink ->
        blink.addListener { _, _, blinkOn ->
            if (blinkOn) {
                timeline.play()
            } else {
                timeline.stop()
                node.pseudoClassStateChanged(javaFxPseudoClass, false)
            }
        }
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
        list.add(fun(rfs: Double): String =
            "${cssProperty.propName}: ${f(fun(rems: Double): String = "${rems * rfs}px")};")
    }
}

fun Node.style(rootFontSize: DoubleProperty, op: RemStyle.() -> Unit = {}): RemStyle {
    val cssPropFunctions = mutableListOf<(Double) -> String>()
    val remStyle = RemStyle(cssPropFunctions).also { remStyle -> remStyle.run(op) }

    val propBindings = cssPropFunctions.map { f ->
        Bindings.createStringBinding({ f(rootFontSize.get()) }, rootFontSize)
    }.toTypedArray()
    styleProperty().bind(Bindings.concat(*propBindings))

    return remStyle
}

fun ToggleButton.preventDeselect(): EventHandler<MouseEvent> = EventHandler { e -> if (isSelected) e.consume() }

/**
 * Registers a key handler on the scene once the node is part of it. A scene-level event filter
 * is used (instead of an accelerator) so that the key event is consumed before controls like
 * TabPane can handle it. Works even for nodes that never receive keyboard focus.
 */
fun Node.shortcut(combination: String, op: () -> Unit) {
    val keyCombination = KeyCombination.valueOf(combination)
    fun install(scene: Scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (keyCombination.match(event)) {
                op()
                event.consume()
            }
        }
    }
    sceneProperty().addListener { _, _, newScene -> if (newScene != null) install(newScene) }
    if (scene != null) install(scene)
}

fun Pane.spacer(): Region =
    Region().also { spacer ->
        HBox.setHgrow(spacer, Priority.ALWAYS)
        VBox.setVgrow(spacer, Priority.ALWAYS)
    }.also { children.add(it) }

fun Pane.hbox(op: HBox.() -> Unit = {}): HBox {
    val box = HBox().also { box -> box.op() }
    children.add(box)
    return box
}

fun Pane.vbox(op: VBox.() -> Unit = {}): VBox {
    val box = VBox().also { box -> box.op() }
    children.add(box)
    return box
}

fun Pane.label(text: String? = null, op: Label.() -> Unit = {}): Label {
    val label = Label(text).also { label -> label.op() }
    children.add(label)
    return label
}

fun Pane.pane(op: Pane.() -> Unit = {}): Pane {
    val pane = Pane().also { pane -> pane.op() }
    children.add(pane)
    return pane
}

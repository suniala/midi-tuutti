package midituutti

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.binding.StringBinding
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.input.MouseEvent
import javafx.util.Duration

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

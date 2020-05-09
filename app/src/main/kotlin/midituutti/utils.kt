package midituutti

import javafx.animation.Animation
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.scene.Node
import tornadofx.*

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
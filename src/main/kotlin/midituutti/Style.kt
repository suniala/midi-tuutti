package midituutti

import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import tornadofx.Stylesheet
import tornadofx.box
import tornadofx.c
import tornadofx.cssclass
import tornadofx.csspseudoclass
import tornadofx.multi
import tornadofx.px
import java.lang.invoke.MethodHandles

/**
 * I can't figure out the correct resource path when using CSSKt#loadFont so let's use our own function instead.
 */
fun myLoadFont(path: String, size: Number): Font? {
    return MethodHandles.lookup().lookupClass().getResourceAsStream(path)?.use { Font.loadFont(it, size.toDouble()) }
}

class Style : Stylesheet() {

    companion object {
        // NOTE: font size does not matter here
        val mainFont = myLoadFont("fonts/DejaVuSansMono-Bold.ttf", -1)!!

        val button by cssclass()
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
        const val fontRemControlTitle = 0.6
        const val fontRemControlButton = 0.8
        const val fontRemControlSliderButton = 0.5
        const val padRemCommon = 0.5
        const val spacingRemCommon = 0.4
    }

    init {
        button {
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
            mainFont.let { fontFamily = it.family }

            fontWeight = FontWeight.BOLD
            textFill = colorDisplayText

            and(blink) {
                textFill = colorDisplayBg
            }
        }
    }
}
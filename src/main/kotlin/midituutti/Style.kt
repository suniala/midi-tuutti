package midituutti

import javafx.scene.text.Font
import java.lang.invoke.MethodHandles

/**
 * I can't figure out the correct resource path when using CSSKt#loadFont so let's use our own function instead.
 */
fun myLoadFont(path: String, size: Number): Font? {
    return MethodHandles.lookup().lookupClass().getResourceAsStream(path)?.use { Font.loadFont(it, size.toDouble()) }
}

object Style {
    // NOTE: font size does not matter here
    val mainFont = myLoadFont("fonts/DejaVuSansMono-Bold.ttf", -1)!!

    // URL of the stylesheet holding the rules for the class names below.
    val cssUrl: String = Style::class.java.getResource("Style.css")!!.toExternalForm()

    // CSS class and pseudo-class names, defined in midituutti/Style.css
    const val button = "button"
    const val displayFont = "display-font"
    const val displaySectionSeparator = "display-section-separator"
    const val timeSignatureValue = "time-signature-value"
    const val timeSignatureSeparator = "time-signature-separator"
    const val controlSectionSeparator = "control-section-separator"
    const val display = "display"
    const val blink = "blink"

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

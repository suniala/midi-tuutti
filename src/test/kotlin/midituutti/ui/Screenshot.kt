package midituutti.ui

import javafx.scene.Scene
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import org.junit.jupiter.api.Assertions.fail
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Pixel-perfect screenshot assertions. Goldens live in src/test/resources/midituutti/ui/screenshots.
 *
 * Update mode (./gradlew updateScreenshots, or -Dmidituutti.updateScreenshots=true on test)
 * writes the captured image as the new golden instead of comparing.
 *
 * Must be called on the FX Application Thread.
 */
object Screenshot {
    val updateMode: Boolean = System.getProperty("midituutti.updateScreenshots") == "true"

    private val goldenDir = File("src/test/resources/midituutti/ui/screenshots")
    private val failureDir = File("build/screenshots-failures")

    private fun WritableImage.toBufferedImage(): BufferedImage {
        val w = width.toInt()
        val h = height.toInt()
        val pixels = IntArray(w * h)
        pixelReader.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, w, h, pixels, 0, w) }
    }

    fun assertScreenshot(scene: Scene, name: String) {
        val image = WritableImage(scene.width.toInt(), scene.height.toInt())
        val actual = scene.snapshot(image).toBufferedImage()
        val goldenFile = File(goldenDir, "$name.png")

        if (updateMode) {
            goldenDir.mkdirs()
            ImageIO.write(actual, "png", goldenFile)
            println("updated golden screenshot: $goldenFile")
            return
        }

        if (!goldenFile.exists()) {
            fail<Unit>("No golden screenshot at $goldenFile — run ./gradlew updateScreenshots to create it")
        }
        val expected = ImageIO.read(goldenFile)!!

        if (expected.width != actual.width || expected.height != actual.height) {
            fail<Unit>(
                "Screenshot '$name' size differs: expected ${expected.width}x${expected.height}, " +
                    "actual ${actual.width}x${actual.height}"
            )
        }

        var diffCount = 0
        var firstDiff: String? = null
        val diffImage = BufferedImage(actual.width, actual.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val e = expected.getRGB(x, y)
                val a = actual.getRGB(x, y)
                if (e != a) {
                    diffCount++
                    firstDiff = firstDiff ?: "($x,$y) expected=${hex(e)} actual=${hex(a)}"
                    diffImage.setRGB(x, y, 0xFFFF0000.toInt())
                }
            }
        }

        if (diffCount > 0) {
            failureDir.mkdirs()
            ImageIO.write(actual, "png", File(failureDir, "$name-actual.png"))
            ImageIO.write(diffImage, "png", File(failureDir, "$name-diff.png"))
            val pct = diffCount * 100.0 / (actual.width * actual.height)
            fail<Unit>(
                "Screenshot '$name' differs in $diffCount pixels (${"%.3f".format(pct)}%). " +
                    "First: $firstDiff. Actual and diff images written to $failureDir"
            )
        }
    }

    private fun hex(argb: Int) = "#%08X".format(argb.toLong() and 0xFFFFFFFFL)
}

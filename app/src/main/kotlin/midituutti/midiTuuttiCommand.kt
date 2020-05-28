package midituutti

import midituutti.engine.PlaybackEngine
import midituutti.engine.PlaybackEngine.createPlayer
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun main(args: Array<String>) {
    PlaybackEngine.initialize()

    val filePath = if (args.isNotEmpty()) args[0] else throw IllegalArgumentException("must give path to midi file")
    val startMeasure = if (args.size >= 2) args[1].toInt() else null
    val endMeasure = if (args.size >= 3) args[2].toInt() else null

    val engine = createPlayer(filePath, startMeasure, endMeasure).player
    engine.play()
    print("Press enter to stop.")
    readLine()
    engine.stop()
    print("Press enter to play.")
    readLine()
    engine.play()
    print("Press enter to quit.")
    readLine()
    engine.quit()
}

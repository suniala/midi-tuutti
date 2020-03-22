package midituutti

import midituutti.engine.createEngine

fun main(args: Array<String>) {
    val filePath = if (args.isNotEmpty()) args[0] else throw IllegalArgumentException("must give path to midi file")
    val startMeasure = if (args.size >= 2) args[1].toInt() else null
    val endMeasure = if (args.size >= 3) args[2].toInt() else null

    val engine = createEngine(filePath, startMeasure, endMeasure).engine
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

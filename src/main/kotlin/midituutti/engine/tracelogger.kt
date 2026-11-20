package midituutti.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import midituutti.midi.MidiMessage
import midituutti.midi.Tick
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration
import kotlin.time.TimeMark

private data class LogMessage(
    val ticks: Tick, val timestamp: Duration, val actualDeltaTs: Duration?, val expectedDeltaTs: Duration?,
    val expectedTs: Duration, val midiMessage: MidiMessage?, val measure: Int
)

private object RowFormatter {
    fun heading(): String = arrayOf(
        "ticks",
        "timestamp ms",
        "expected ts ms",
        "lag ms",
        "actual delta ms",
        "expected delta ms",
        "delta diff ms",
        "measure",
        "message"
    ).joinToString(";")

    fun row(logMessage: LogMessage): String = logMessage.run {
        arrayOf<Any>(
            "$ticks",
            formatTimeToMs(timestamp),
            formatTimeToMs(expectedTs),
            formatTimeToMs(timestamp - expectedTs),
            formatTimeToMs(actualDeltaTs),
            formatTimeToMs(expectedDeltaTs),
            formatTimeToMs(actualDeltaTs?.minus(expectedDeltaTs ?: Duration.ZERO)),
            measure,
            "${midiMessage ?: '-'}"
        ).joinToString(";")
    }

    private fun formatTimeToMs(time: Duration?): String =
        String.format("%.3f", (time ?: Duration.ZERO).inWholeMicroseconds / 1000.0)
}

object EngineTraceLogger {
    private val traceEnabled = System.getProperty("midituutti.engine.trace") == "true"
    private val queue = LinkedBlockingQueue<LogMessage>(1000 * 1000)
    private var previous: LogMessage? = null
    private var flushJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun trace(
        playStartMark: TimeMark, expectedTimestampTs: Duration, ticks: Tick, expectedDeltaTs: Duration?,
        midiMessage: MidiMessage?, currentMeasure: Int
    ) {
        if (flushJob?.isActive == true && traceEnabled) {
            val eventTs = playStartMark.elapsedNow()
            val actualDeltaTs = previous?.let { p -> eventTs - p.timestamp }
            val expectedDeltaTsFromPrevious = if (previous != null) expectedDeltaTs else null
            val logMessage = LogMessage(
                ticks, eventTs, actualDeltaTs, expectedDeltaTsFromPrevious,
                expectedTimestampTs, midiMessage, currentMeasure
            )
            queue.put(logMessage)
            previous = logMessage
        }
    }

    fun start() {
        val outputPath = "engine-trace-${System.currentTimeMillis()}.csv"

        if (traceEnabled) {
            flushRows(sequenceOf(RowFormatter.heading()), outputPath)

            flushJob = coroutineScope.launch {
                while (isActive) {
                    delay(1000)
                    flush(outputPath)
                }
            }
        }
    }

    fun stop() {
        previous = null
        flushJob?.cancel()
    }

    private fun flush(outputPath: String) {
        println("flushing...")
        val messages = arrayListOf<LogMessage>()
        queue.drainTo(messages)
        flushMessages(messages, outputPath)
    }

    private fun flushMessages(messages: List<LogMessage>, outputPath: String) {
        if (messages.isNotEmpty()) {
            flushRows(messages.asSequence().map(RowFormatter::row), outputPath)
        }
        println("flushed ${messages.size} messages")
    }

    private fun flushRows(rows: Sequence<String>, outputPath: String) {
        val writer = PrintWriter(FileWriter(outputPath, true))

        // NOTE: would be better to use writer.use here but I couldn't get it to work.
        rows.forEach { r ->
            writer.println(r)
        }

        writer.close()
    }
}
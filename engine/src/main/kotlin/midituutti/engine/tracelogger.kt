package midituutti.engine

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import midituutti.midi.MidiMessage
import midituutti.midi.OutputTimestamp
import midituutti.midi.Tick
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.LinkedBlockingQueue

private data class LogMessage(val ticks: Tick, val timestampNs: Long, val actualDeltaNs: Long?, val expectedDeltaNs: Long?,
                              val expectedTimestampNs: Long, val midiMessage: MidiMessage?, val measure: Int)

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
        arrayOf(
                "$ticks",
                formatTimeToMs(timestampNs),
                formatTimeToMs(expectedTimestampNs),
                formatTimeToMs(timestampNs - expectedTimestampNs),
                formatTimeToMs(actualDeltaNs),
                formatTimeToMs(expectedDeltaNs),
                formatTimeToMs(actualDeltaNs?.minus(expectedDeltaNs ?: 0)),
                measure,
                "\"${midiMessage ?: '-'}\""
        ).joinToString(";")
    }

    private fun formatTimeToMs(nanos: Long?): String =
            String.format("%.3f", (nanos ?: 0).toDouble() / 1000 / 1000)
}

object EngineTraceLogger {
    private val traceEnabled = System.getProperty("midituutti.engine.trace") == "true"
    private val queue = LinkedBlockingQueue<LogMessage>(1000 * 1000)
    private var previous: LogMessage? = null
    private var measureCount = 0
    private var flushJob: Job? = null

    fun trace(playStartNs: Long, expectedTimestampNs: Long, ticks: Tick, expectedDeltaTs: OutputTimestamp?,
              midiMessage: MidiMessage?, isMeasureStart: Boolean) {
        if (flushJob?.isActive == true && traceEnabled) {
            measureCount += if (isMeasureStart) 1 else 0
            val timestampNs = System.nanoTime() - playStartNs
            val actualDeltaNs = previous?.let { p -> timestampNs - p.timestampNs }
            val expectedDeltaNs = if (previous != null && expectedDeltaTs != null) expectedDeltaTs.toNanos() else null
            val logMessage = LogMessage(ticks, timestampNs, actualDeltaNs, expectedDeltaNs,
                    expectedTimestampNs - playStartNs, midiMessage, measureCount)
            queue.put(logMessage)
            previous = logMessage
        }
    }

    fun start() {
        val outputPath = "engine-trace-${System.currentTimeMillis()}.csv"
        measureCount = 0

        if (traceEnabled) {
            flushRows(sequenceOf(RowFormatter.heading()), outputPath)

            flushJob = GlobalScope.launch {
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
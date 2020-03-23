package midituutti.engine

import midituutti.midi.Tick
import midituutti.midi.TimeSignature
import midituutti.midi.openFile
import org.junit.Test
import java.io.InputStream
import kotlin.test.assertEquals

class SongStructureTest {
    data class Expectation(val timeSignature: TimeSignature, val firstTick: Tick, val eventCount: Int)

    /**
     * NOTE: This is a somewhat brittle test. Event counts and head event ticks have been checked with a Midi debugger.
     */
    @Test
    fun songStructureMatchesMidiFileContents() {
        val midiFile = openFile(testFile("measures-44-34-58.mid"))
        val song = SongStructure.of(midiFile)

        val expectations = listOf(
                Expectation(TimeSignature(4, 4), Tick(0), 18),
                Expectation(TimeSignature(4, 4), Tick(1920), 8),
                Expectation(TimeSignature(3, 4), Tick(3840), 7),
                Expectation(TimeSignature(3, 4), Tick(5280), 6),
                Expectation(TimeSignature(5, 8), Tick(6720), 11),
                Expectation(TimeSignature(5, 8), Tick(7920), 10),
                Expectation(TimeSignature(4, 4), Tick(9120), 10)
        )

        assertEquals(expectations.size, song.measures.size, "Number of measures")

        song.measures.zip(expectations.indices.zip(expectations)).forEach { (measure, indexExpected) ->
            run {
                val (index, expected) = indexExpected
                assertEquals(expected.timeSignature, measure.timeSignature, "Time signature at measure $index")
                assertEquals(expected.firstTick, measure.events.first().ticks(), "First tick at measure $index")
                assertEquals(expected.eventCount, measure.events.size, "Number of events at measure $index")
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun testFile(file: String): InputStream = javaClass.getResourceAsStream(file)
}

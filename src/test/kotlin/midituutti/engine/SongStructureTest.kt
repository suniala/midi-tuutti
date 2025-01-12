package midituutti.engine

import io.github.suniala.javamididecoder.MidiShortM.MidiControlChangeM
import io.github.suniala.javamididecoder.MidiShortM.MidiPitchWheelChangeM
import io.github.suniala.javamididecoder.MidiShortM.MidiProgramChangeM
import midituutti.midi.ChannelAdjustmentMessage
import midituutti.midi.Tick
import midituutti.midi.TimeSignature
import midituutti.midi.openFile
import org.junit.Test
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SongStructureTest {
    data class MeasureExpectation(val timeSignature: TimeSignature, val firstTick: Tick, val eventCount: Int)

    /**
     * NOTE: This is a somewhat brittle test. Event counts and head event ticks have been checked with a Midi debugger.
     */
    @Test
    fun songMeasureTimeSignaturesAndEventsMatchMidiFileContents() {
        val midiFile = openFile(testFile("measures-44-34-58.mid"))
        val song = SongStructure.of(midiFile)

        val expectations = listOf(
                MeasureExpectation(TimeSignature(4, 4), Tick(0), 18),
                MeasureExpectation(TimeSignature(4, 4), Tick(1920), 8),
                MeasureExpectation(TimeSignature(3, 4), Tick(3840), 7),
                MeasureExpectation(TimeSignature(3, 4), Tick(5280), 6),
                MeasureExpectation(TimeSignature(5, 8), Tick(6720), 11),
                MeasureExpectation(TimeSignature(5, 8), Tick(7920), 10),
                MeasureExpectation(TimeSignature(4, 4), Tick(9120), 10)
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

    data class AdjustmentMatcher(val description: String, val f: (MessageEvent) -> Boolean)

    data class MeasureAdjustmentsExpectation(val channel: Int, val matcher: AdjustmentMatcher)

    @Test
    fun songMeasurePreviousControlsMatchMidiFileContents() {
        val midiFile = openFile(testFile("midi-ctrl-test.mid"))
        val song = SongStructure.of(midiFile)

        val expectationsPerMeasureAndChannel = listOf(
                mapOf(
                        1 to setOf(
                                matchProgramChange(1),
                                // Not sure what is the correct "human readable" value. Kmidimon shows a value that is
                                // 8192 units lower than this one, that is -165.
                                matchPitchWheel(8027),
                                matchControlChange(10, 0),
                                matchControlChange(93, 0),
                                matchControlChange(7, 100),
                                matchControlChange(91, 0)
                        ),
                        2 to setOf(
                                matchProgramChange(23),
                                matchControlChange(10, 127),
                                matchControlChange(93, 0),
                                matchControlChange(7, 100),
                                matchControlChange(91, 0)
                        )
                ),
                mapOf(
                        1 to setOf(
                                matchProgramChange(1),
                                // Here Kmidimon shows 8191.
                                matchPitchWheel(16383),
                                matchControlChange(10, 0),
                                matchControlChange(93, 0),
                                matchControlChange(7, 100),
                                matchControlChange(91, 0)
                        ),
                        2 to setOf(
                                matchProgramChange(20),
                                matchControlChange(10, 127),
                                matchControlChange(93, 0),
                                matchControlChange(7, 100),
                                matchControlChange(91, 0)
                        )
                )
        )

        val expectations: List<List<MeasureAdjustmentsExpectation>> =
                expectationsPerMeasureAndChannel.map { me -> me.entries.flatMap { e -> e.value.map { v -> MeasureAdjustmentsExpectation(e.key, v) } } }

        song.measures.zip(expectations.indices.zip(expectations)).forEach { (measure, indexExpected) ->
            run {
                val (index, expected) = indexExpected
                expected.forEach { exp ->
                    val match = measure.initialAdjustments[exp.channel]?.find { m -> exp.matcher.f(m) }
                    assertNotNull(match,
                            "Expected initial adjustment for channel ${exp.channel} for measure $index: ${exp.matcher.description}")
                }
                assertEquals(expected.size, measure.initialAdjustments.values.map { it.size }.sum(),
                        "Initial adjustments have only expected events for measure $index: ${measure.initialAdjustments}")
            }
        }
    }

    private fun matchControlChange(control: Int, value: Int) = AdjustmentMatcher(
            "Control $control change $value",
            fun(m: MessageEvent): Boolean = (m.message as ChannelAdjustmentMessage).original.let {
                return it is MidiControlChangeM && it.controlChange == control && it.value == value
            })

    private fun matchPitchWheel(value: Int) = AdjustmentMatcher(
            "Pitch wheel $value",
            fun(m: MessageEvent): Boolean = (m.message as ChannelAdjustmentMessage).original.let {
                return it is MidiPitchWheelChangeM && it.value == value
            })

    private fun matchProgramChange(program: Int) = AdjustmentMatcher(
            "Program change $program",
            fun(m: MessageEvent): Boolean = (m.message as ChannelAdjustmentMessage).original.let {
                return it is MidiProgramChangeM && it.displayValue == program
            })

    @Suppress("SameParameterValue")
    private fun testFile(file: String): InputStream = javaClass.getResourceAsStream(file)
}

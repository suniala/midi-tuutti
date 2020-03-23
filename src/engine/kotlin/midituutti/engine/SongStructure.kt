package midituutti.engine

import midituutti.midi.Accessors
import midituutti.midi.MetaType
import midituutti.midi.MidiFile
import midituutti.midi.MidiMessage
import midituutti.midi.Tick
import midituutti.midi.TimeSignature

class Measure(val start: Tick, val timeSignature: TimeSignature, val events: List<EngineEvent>)

class SongStructure(val measures: List<Measure>) {
    companion object {
        fun of(midiFile: MidiFile): SongStructure = SongStructure(measures(midiFile))

        fun withClick(midiFile: MidiFile): SongStructure =
                SongStructure(injectClick(measures(midiFile), midiFile.ticksPerBeat()))

        private fun measures(midiFile: MidiFile): List<Measure> {
            val timeSignatureMessage = midiFile.messages().find { m -> m.metaType() == MetaType.TimeSignature } as MidiMessage
            return Parser(midiFile.ticksPerBeat()).parse(timeSignatureMessage, midiFile.messages())
        }

        private fun measureTicks(ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
                Tick((ticksPerBeat * timeSignature.numerator / (timeSignature.denominator / 4).toLong()))

        private fun beatTicks(beat: Int, ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
                Tick((ticksPerBeat * (beat - 1) / (timeSignature.denominator / (2 * timeSignature.denominator / 4).toLong())))

        private fun injectClick(measures: List<Measure>, ticksPerBeat: Int): List<Measure> {
            fun clickType(eight: Int): ClickType {
                return if (eight == 1) ClickType.One
                else
                    when (eight % 2) {
                        1 -> ClickType.Quarter
                        else -> ClickType.Eight
                    }
            }

            fun measureClick(measure: Measure): Measure {
                val eightCount = measure.timeSignature.numerator * 8 / measure.timeSignature.denominator
                val clickEventTicks = (1..eightCount)
                        .map { t -> measure.start + beatTicks(t, ticksPerBeat, measure.timeSignature) }
                val clickEvents = clickEventTicks
                        .mapIndexed { i, t -> ClickEvent(t, clickType(i + 1)) }
                return Measure(measure.start, measure.timeSignature,
                        (clickEvents + measure.events).sortedBy { e -> e.ticks() })
            }

            return measures.map { m -> measureClick(m) }
        }
    }

    private class Parser(val ticksPerBeat: Int) {
        fun parse(ts: MidiMessage, messages: List<MidiMessage>): List<Measure> =
                parseRec(emptyList(), ts.get(Accessors.timeSignatureAccessor), ts.ticks(), emptyList(), messages)

        private fun withinMeasure(message: MidiMessage, timeSignature: TimeSignature, measureStart: Tick): Boolean {
            val delta = message.ticks() - measureStart
            return delta < measureTicks(ticksPerBeat, timeSignature)
        }

        private fun nextMeasureStart(start: Tick, timeSignature: TimeSignature): Tick =
                start + measureTicks(ticksPerBeat, timeSignature)

        private tailrec fun parseRec(acc: List<Measure>,
                                     currTimeSignature: TimeSignature,
                                     measureStart: Tick,
                                     measure: List<EngineEvent>,
                                     rem: List<MidiMessage>): List<Measure> {
            if (rem.isEmpty()) {
                return acc + Measure(measureStart, currTimeSignature, measure)
            } else {
                val message = rem.first()

                val nextTimeSignature =
                        if (message.metaType() == MetaType.TimeSignature) {
                            message.get(Accessors.timeSignatureAccessor)
                        } else currTimeSignature

                return if (withinMeasure(message, currTimeSignature, measureStart)) {
                    parseRec(acc, nextTimeSignature, measureStart, measure + MessageEvent(message), rem.drop(1))
                } else {
                    parseRec(
                            acc + Measure(measureStart, currTimeSignature, measure),
                            nextTimeSignature,
                            nextMeasureStart(measureStart, currTimeSignature),
                            listOf(MessageEvent(message)),
                            rem.drop(1))
                }
            }
        }
    }
}
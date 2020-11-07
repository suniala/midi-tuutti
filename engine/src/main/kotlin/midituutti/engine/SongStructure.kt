package midituutti.engine

import midituutti.midi.ChannelAdjustmentMessage
import midituutti.midi.MidiFile
import midituutti.midi.MidiMessage
import midituutti.midi.NoteMessage
import midituutti.midi.Tempo
import midituutti.midi.TempoMessage
import midituutti.midi.Tick
import midituutti.midi.TimeSignature
import midituutti.midi.TimeSignatureMessage

/**
 * @param initialTempo Tempo at the beginning of this measure
 * @param initialAdjustments Adjusting events, such as program change or pan, that should be sent when jumping to
 * this measure
 */
data class Measure(val number: Int, val start: Tick, val timeSignature: TimeSignature,
                   val initialTempo: Tempo, val initialAdjustments: Map<Int, List<MessageEvent>>, val events: List<EngineEvent>) {
    fun chunked(): Sequence<Pair<Tick, List<EngineEvent>>> = sequence {
        val adjustments = initialAdjustments.values.flatMap { it.map { v -> Pair(start, v) } }
        val otherEvents: List<Pair<Tick, EngineEvent>> = events.map { Pair(it.ticks(), it) }

        // Initial adjustments should be sent before any other events in the measure.
        val eventsToChunk: List<Pair<Tick, EngineEvent>> = adjustments + otherEvents

        var chunkEvents = arrayListOf<Pair<Tick, EngineEvent>>()
        for (event in eventsToChunk) {
            if (chunkEvents.isEmpty()) {
                chunkEvents.add(event)
            } else {
                if (chunkEvents.first().first == event.first) {
                    chunkEvents.add(event)
                } else {
                    yield(Pair(chunkEvents.first().first, chunkEvents.map { it.second }))
                    chunkEvents = arrayListOf(event)
                }
            }
        }
        if (chunkEvents.isNotEmpty()) {
            yield(Pair(chunkEvents.first().first, chunkEvents.map { it.second }))
        }
    }
}

class SongStructure(val measures: List<Measure>) {
    val tracks: Set<EngineTrack> = measures.flatMap { m ->
        m.events.mapNotNull { e ->
            when (e) {
                is MessageEvent -> when (e.message) {
                    is NoteMessage -> MidiTrack(e.message.note().channel)
                    else -> null
                }
                is ClickEvent -> ClickTrack
            }
        }
    }.toSet()

    companion object {
        fun of(midiFile: MidiFile): SongStructure = SongStructure(measures(midiFile))

        fun withClick(midiFile: MidiFile): SongStructure =
                SongStructure(injectClick(measures(midiFile), midiFile.ticksPerBeat()))

        private fun measures(midiFile: MidiFile): List<Measure> {
            val timeSignatureMessage = midiFile.messages.find { m -> m is TimeSignatureMessage } as TimeSignatureMessage
            val tempoMessage = midiFile.messages.find { m -> m is TempoMessage } as TempoMessage
            return Parser(midiFile.ticksPerBeat()).parse(timeSignatureMessage, tempoMessage, midiFile.messages)
        }

        private fun measureTicks(ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
                Tick((ticksPerBeat * timeSignature.beats / (timeSignature.unit / 4).toLong()))

        private fun beatTicks(beat: Int, ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
                Tick((ticksPerBeat * (beat - 1) / (timeSignature.unit / (2 * timeSignature.unit / 4).toLong())))

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
                val eightCount = measure.timeSignature.beats * 8 / measure.timeSignature.unit
                val clickEventTicks = (1..eightCount)
                        .map { t -> measure.start + beatTicks(t, ticksPerBeat, measure.timeSignature) }
                val clickEvents = clickEventTicks
                        .mapIndexed { i, t -> ClickEvent(t, clickType(i + 1)) }
                return measure.copy(events = (clickEvents + measure.events).sortedBy { e -> e.ticks() })
            }

            return measures.map { m -> measureClick(m) }
        }
    }

    private class Parser(val ticksPerBeat: Int) {
        fun parse(ts: TimeSignatureMessage, initialTempo: TempoMessage, messages: List<MidiMessage>): List<Measure> =
                parseRec(emptyList(), ts.timeSignature(), initialTempo.tempo(), emptyMap(), ts.ticks(), emptyList(), messages)

        private fun withinMeasure(message: MidiMessage, timeSignature: TimeSignature, measureStart: Tick): Boolean {
            val delta = message.ticks() - measureStart
            return delta < measureTicks(ticksPerBeat, timeSignature)
        }

        private fun nextMeasureStart(start: Tick, timeSignature: TimeSignature): Tick =
                start + measureTicks(ticksPerBeat, timeSignature)

        private tailrec fun parseRec(acc: List<Measure>,
                                     currTimeSignature: TimeSignature,
                                     currTempo: Tempo,
                                     prevAdjustmentsReversed: Map<Int, Map<String, List<MessageEvent>>>,
                                     measureStart: Tick,
                                     measure: List<EngineEvent>,
                                     rem: List<MidiMessage>): List<Measure> {
            if (rem.isEmpty()) {
                return acc + Measure(acc.size + 1, measureStart, currTimeSignature, currTempo,
                        collectLatestTypeSpecificAdjustments(prevAdjustmentsReversed, measureStart), measure)
            } else {
                val message = rem.first()

                val nextTimeSignature =
                        if (message is TimeSignatureMessage) {
                            message.timeSignature()
                        } else currTimeSignature
                val nextTempo = if (message is TempoMessage) {
                    message.tempo()
                } else currTempo

                val messageEvent = MessageEvent(message)
                val nextPrevAdjustmentsReversed = addPossibleAdjustmentEvent(prevAdjustmentsReversed, messageEvent)

                return if (withinMeasure(message, currTimeSignature, measureStart)) {
                    parseRec(acc, nextTimeSignature, nextTempo, nextPrevAdjustmentsReversed, measureStart,
                            measure + messageEvent, rem.drop(1))
                } else {
                    parseRec(
                            acc + Measure(acc.size + 1, measureStart, currTimeSignature, currTempo,
                                    collectLatestTypeSpecificAdjustments(prevAdjustmentsReversed, measureStart), measure),
                            nextTimeSignature,
                            nextTempo,
                            nextPrevAdjustmentsReversed,
                            nextMeasureStart(measureStart, currTimeSignature),
                            listOf(messageEvent),
                            rem.drop(1))
                }
            }
        }

        private fun addPossibleAdjustmentEvent(prevAdjustmentsReversed: Map<Int, Map<String, List<MessageEvent>>>,
                                               event: MessageEvent): Map<Int, Map<String, List<MessageEvent>>> {
            return when (event.message) {
                is ChannelAdjustmentMessage -> {
                    val channel = event.message.original.channel
                    val typeId = event.message.typeId()
                    val channelAdjustments: Map<String, List<MessageEvent>> = prevAdjustmentsReversed[channel]
                            ?: emptyMap()
                    val typeAdjustments: List<MessageEvent> = channelAdjustments[typeId] ?: emptyList()
                    prevAdjustmentsReversed.plus(Pair(
                            channel,
                            channelAdjustments.plus(Pair(
                                    typeId,
                                    listOf(event) + typeAdjustments
                            ))))
                }
                else -> prevAdjustmentsReversed
            }
        }

        private fun collectLatestTypeSpecificAdjustments(prevAdjustmentsReversed: Map<Int, Map<String, List<MessageEvent>>>,
                                                         atOrBefore: Tick): Map<Int, List<MessageEvent>> {
            return prevAdjustmentsReversed.mapValues { channelAdjustments ->
                channelAdjustments.value
                        .map { typeAdjustments ->
                            typeAdjustments.value.find { it.ticks() <= atOrBefore }
                        }
                        .filterNotNull()
            }
        }
    }
}
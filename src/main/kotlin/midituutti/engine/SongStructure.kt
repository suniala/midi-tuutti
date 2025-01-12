package midituutti.engine

import io.github.suniala.javamididecoder.MidiShortM
import midituutti.midi.*

/**
 * @param initialTempo Tempo at the beginning of this measure
 * @param initialAdjustments Adjusting events, such as program change or pan, that should be sent when jumping to
 * this measure
 */
data class Measure(val number: Int, val start: Tick, val timeSignature: TimeSignature,
                   val initialTempo: Tempo, val initialAdjustments: Map<Int, List<MessageEvent>>, val events: List<EngineEvent>) {
    fun chunked(includeAdjustments: Boolean): Sequence<Pair<Tick, List<EngineEvent>>> = sequence {
        val adjustments =
                if (includeAdjustments) initialAdjustments.values.flatMap { it.map { v -> Pair(start, v) } }
                else emptyList()
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

    val trackInstruments: Map<EngineTrack, List<Pair<Int, String>>> = measures
            .flatMap { m ->
                m.events.mapNotNull { e ->
                    if (e is MessageEvent) {
                        if (e.message is ChannelAdjustmentMessage) e.message.let { msg ->
                            if (msg.original is MidiShortM.MidiProgramChangeM) msg.original.let { pc ->
                                Pair(MidiTrack(pc.channel), Pair(pc.displayValue, pc.gmInstrumentName))
                            } else null
                        } else null
                    } else null
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct().sortedBy { p -> p.first } }

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

        private fun withinMeasure(ticks: Tick, timeSignature: TimeSignature, measureStart: Tick): Boolean {
            val delta = ticks - measureStart
            return delta < measureTicks(ticksPerBeat, timeSignature)
        }

        private fun nextMeasureStart(currMeasure: Tick, timeSignature: TimeSignature, toNextMeasure: Int): Tick =
                currMeasure + measureTicks(ticksPerBeat, timeSignature).let { Tick(it.tick * toNextMeasure) }

        private tailrec fun parseRec(acc: List<Measure>,
                                     currTimeSignature: TimeSignature,
                                     currTempo: Tempo,
                                     prevAdjustmentsReversed: Map<Int, Map<String, List<MessageEvent>>>,
                                     measureStart: Tick,
                                     measureEvents: List<EngineEvent>,
                                     rem: List<MidiMessage>): List<Measure> {
            if (rem.isEmpty()) {
                return acc + Measure(acc.size + 1, measureStart, currTimeSignature, currTempo,
                        collectLatestTypeSpecificAdjustments(prevAdjustmentsReversed, measureStart), measureEvents)
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

                return if (withinMeasure(message.ticks(), currTimeSignature, measureStart)) {
                    // We are still within the current measure.
                    parseRec(acc, nextTimeSignature, nextTempo, nextPrevAdjustmentsReversed, measureStart,
                            measureEvents + messageEvent, rem.drop(1))
                } else {
                    // This message is in the next (or later) measure. Normally there is only one measure change between
                    // messages but there may be more if there are empty measures between messages. Therefore we must
                    // generate 1..n measures until we get to the measure of the current message.
                    val firstIntermediateMeasure = acc.size + 1
                    val intermediateMeasures = generateSequence(firstIntermediateMeasure, { it + 1 })
                            .map { measureNo ->
                                Pair(
                                        measureNo,
                                        nextMeasureStart(measureStart, currTimeSignature, measureNo - firstIntermediateMeasure))
                            }
                            .takeWhile {
                                // Including the measure of the current message.
                                mt ->
                                mt.second <= message.ticks()
                            }
                            .map { mt -> Triple(mt.first, mt.second, if (mt.first == firstIntermediateMeasure) measureEvents else emptyList()) }
                            .map { mte ->
                                Measure(mte.first, mte.second, currTimeSignature, currTempo,
                                        collectLatestTypeSpecificAdjustments(prevAdjustmentsReversed, mte.second), mte.third)
                            }
                            .toList()
                            // Exclude the measure of the current message
                            .dropLast(1)

                    parseRec(
                            acc + intermediateMeasures,
                            nextTimeSignature,
                            nextTempo,
                            nextPrevAdjustmentsReversed,
                            nextMeasureStart(intermediateMeasures.last().start, currTimeSignature, 1),
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
package midituutti.engine

import midituutti.midi.MessageDecoder.{Accessors, TimeSignature}
import midituutti.midi._

import scala.annotation.tailrec
import scala.collection.immutable

class Measure(val events: Seq[MidiEvent])

class TrackStructure(val measures: Seq[Measure])

object TrackStructure {
  def of(midiFile: MidiFile): TrackStructure = {
    val track = midiFile.events
    val timeSignatureMessage = track.find(_.message.metaType.contains(MetaType.TimeSignature)).get.message
    val measures = new Parser(midiFile.ticksPerBeat).parse(timeSignatureMessage, track)
    new TrackStructure(measures)
  }

  def measureTicks(ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * timeSignature.numerator / (timeSignature.denominator / 4))

  private class Parser(ticksPerBeat: Int) {
    def parse(ts: MidiMessage, track: Seq[MidiEvent]): Seq[Measure] = {
      parseRec(Nil, ts, new MidiEvent(new Tick(0), ts) :: Nil, track)
    }

    private def withinMeasure(event: MidiEvent, timeSignatureMessage: MidiMessage, measureStart: MidiEvent): Boolean = {
      val delta = event.ticks - measureStart.ticks
      delta < measureTicks(ticksPerBeat, timeSignatureMessage.get(Accessors.timeSignatureAccessor))
    }

    private def timeSignatureEvent(eventTimeSignature: MidiMessage, prevMeasureStart: MidiEvent,
                                   prevTimeSignature: MidiMessage) = {
      new MidiEvent(
        prevMeasureStart.ticks + measureTicks(ticksPerBeat, prevTimeSignature.get(Accessors.timeSignatureAccessor)),
        eventTimeSignature)
    }

    @tailrec
    private def parseRec(acc: immutable.List[Measure],
                         currTimeSignature: MidiMessage,
                         measure: immutable.List[MidiEvent],
                         rem: Seq[MidiEvent]): Seq[Measure] = {
      // TODO: get rid of this cons + reverse silliness
      if (rem.isEmpty) {
        (new Measure(measure.reverse) :: acc).reverse
      } else {
        val event = rem.head
        val measureStart = measure.last

        val nextTimeSignature =
          if (event.message.metaType.contains(MetaType.TimeSignature)) {
            event.message
          }
          else currTimeSignature

        if (withinMeasure(event, currTimeSignature, measureStart)) {
          parseRec(acc, nextTimeSignature, event :: measure, rem.tail)
        } else {
          parseRec(new Measure(measure.reverse) :: acc, nextTimeSignature,
            event :: timeSignatureEvent(nextTimeSignature, measureStart, currTimeSignature) :: Nil,
            rem.tail)
        }
      }
    }
  }

}
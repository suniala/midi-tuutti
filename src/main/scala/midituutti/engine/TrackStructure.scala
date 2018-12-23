package midituutti.engine

import midituutti.midi.MessageDecoder.{Accessors, Note, OnOff, TimeSignature}
import midituutti.midi._

import scala.annotation.tailrec
import scala.collection.immutable

class Measure(val timeSignature: TimeSignature, val events: Seq[MidiEvent])

class TrackStructure(val measures: Seq[Measure])

object TrackStructure {

  def of(midiFile: MidiFile): TrackStructure = {
    val track = midiFile.events
    val timeSignatureMessage = track.find(_.message.metaType.contains(MetaType.TimeSignature)).get.message
    val measures = new Parser(midiFile.ticksPerBeat).parse(timeSignatureMessage, track)
    val clickMeasures = injectClick(measures, midiFile.ticksPerBeat)
    new TrackStructure(clickMeasures)
  }

  def measureTicks(ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * timeSignature.numerator / (timeSignature.denominator / 4))

  def beatTicks(beat: Int, ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * (beat - 1) / (timeSignature.denominator / 4))

  private def injectClick(measures: Seq[Measure], ticksPerBeat: Int): Seq[Measure] = {
    def measureClick(measure: Measure): Measure = {
      val clickEventTicks = (1 to measure.timeSignature.numerator)
        .map(measure.events.head.ticks + beatTicks(_, ticksPerBeat, measure.timeSignature))
      val clickEvents = clickEventTicks
        .zipWithIndex
        .map({ case (t, _) => new MidiEvent(t, NoteMessage(Note(OnOff.On, 10, 42, 100))) })
      new Measure(measure.timeSignature, (clickEvents ++ measure.events).sortWith({ case (a, b) => a.ticks < b.ticks }))
    }

    measures.map(measureClick)
  }

  private class Parser(ticksPerBeat: Int) {
    def parse(ts: MidiMessage, track: Seq[MidiEvent]): Seq[Measure] = {
      parseRec(Nil, ts, new MidiEvent(new Tick(0), ts) :: Nil, track)
    }

    private def withinMeasure(event: MidiEvent, timeSignatureMessage: MidiMessage, measureStart: MidiEvent): Boolean = {
      val delta = event.ticks - measureStart.ticks
      delta < measureTicks(ticksPerBeat, timeSignatureMessage.get(Accessors.timeSignatureAccessor))
    }

    @tailrec
    private def parseRec(acc: immutable.List[Measure],
                         currTimeSignature: MidiMessage,
                         measure: immutable.List[MidiEvent],
                         rem: Seq[MidiEvent]): Seq[Measure] = {
      // TODO: get rid of this cons + reverse silliness
      if (rem.isEmpty) {
        (new Measure(currTimeSignature.get(Accessors.timeSignatureAccessor), measure.reverse) :: acc).reverse
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
          parseRec(new Measure(currTimeSignature.get(Accessors.timeSignatureAccessor), measure.reverse) :: acc,
            nextTimeSignature,
            event :: Nil,
            rem.tail)
        }
      }
    }
  }

}
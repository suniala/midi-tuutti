package midituutti.engine

import midituutti.midi.MessageDecoder.{Accessors, Note, OnOff, TimeSignature}
import midituutti.midi._

import scala.annotation.tailrec
import scala.collection.immutable

class Measure(val start: Tick, val timeSignature: TimeSignature, val events: Seq[MidiEvent])

class TrackStructure(val measures: Seq[Measure])

object TrackStructure {

  def of(midiFile: MidiFile): TrackStructure = new TrackStructure(measures(midiFile))

  def withClick(midiFile: MidiFile): TrackStructure =
    new TrackStructure(injectClick(measures(midiFile), midiFile.ticksPerBeat))

  private def measures(midiFile: MidiFile) = {
    val track = midiFile.events
    val timeSignatureEvent = track.find(_.message.metaType.contains(MetaType.TimeSignature)).get
    new Parser(midiFile.ticksPerBeat).parse(timeSignatureEvent, track)
  }

  private def measureTicks(ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * timeSignature.numerator / (timeSignature.denominator / 4))

  private def beatTicks(beat: Int, ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * (beat - 1) / (timeSignature.denominator / 4))

  private def injectClick(measures: Seq[Measure], ticksPerBeat: Int): Seq[Measure] = {
    def measureClick(measure: Measure): Measure = {
      val clickEventTicks = (1 to measure.timeSignature.numerator)
        .map(measure.start + beatTicks(_, ticksPerBeat, measure.timeSignature))
      val clickEvents = clickEventTicks
        .zipWithIndex
        .map({ case (t, _) => new MidiEvent(t, NoteMessage(Note(OnOff.On, 10, 42, 100))) })
      new Measure(measure.start,
        measure.timeSignature,
        (clickEvents ++ measure.events).sortWith({ case (a, b) => a.ticks < b.ticks }))
    }

    measures.map(measureClick)
  }

  private class Parser(ticksPerBeat: Int) {
    def parse(ts: MidiEvent, track: Seq[MidiEvent]): Seq[Measure] = {
      parseRec(Nil, ts.message.get(Accessors.timeSignatureAccessor), ts.ticks, Nil, track)
    }

    private def withinMeasure(event: MidiEvent, timeSignature: TimeSignature, measureStart: Tick): Boolean = {
      val delta = event.ticks - measureStart
      delta < measureTicks(ticksPerBeat, timeSignature)
    }

    private def nextMeasureStart(start: Tick, timeSignature: TimeSignature): Tick =
      start + measureTicks(ticksPerBeat, timeSignature)

    @tailrec
    private def parseRec(acc: immutable.List[Measure],
                         currTimeSignature: TimeSignature,
                         measureStart: Tick,
                         measure: immutable.List[MidiEvent],
                         rem: Seq[MidiEvent]): Seq[Measure] = {
      // TODO: get rid of this cons + reverse silliness
      if (rem.isEmpty) {
        (new Measure(measureStart, currTimeSignature, measure.reverse) :: acc).reverse
      } else {
        val event = rem.head

        val nextTimeSignature =
          if (event.message.metaType.contains(MetaType.TimeSignature)) {
            event.message.get(Accessors.timeSignatureAccessor)
          }
          else currTimeSignature

        if (withinMeasure(event, currTimeSignature, measureStart)) {
          parseRec(acc, nextTimeSignature, measureStart, event :: measure, rem.tail)
        } else {
          parseRec(
            new Measure(measureStart, currTimeSignature, measure.reverse) :: acc,
            nextTimeSignature,
            nextMeasureStart(measureStart, currTimeSignature),
            event :: Nil,
            rem.tail)
        }
      }
    }
  }

}
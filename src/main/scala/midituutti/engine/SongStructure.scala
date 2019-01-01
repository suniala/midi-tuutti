package midituutti.engine

import midituutti.engine.ClickType.ClickType
import midituutti.midi.MessageDecoder.{Accessors, TimeSignature}
import midituutti.midi._

import scala.annotation.tailrec
import scala.collection.immutable

class Measure(val start: Tick, val timeSignature: TimeSignature, val events: Seq[EngineEvent])

class SongStructure(val measures: Seq[Measure])

object SongStructure {

  def of(midiFile: MidiFile): SongStructure = new SongStructure(measures(midiFile))

  def withClick(midiFile: MidiFile): SongStructure =
    new SongStructure(injectClick(measures(midiFile), midiFile.ticksPerBeat))

  private def measures(midiFile: MidiFile) = {
    val timeSignatureMessage = midiFile.messages.find(_.metaType.contains(MetaType.TimeSignature)).get
    new Parser(midiFile.ticksPerBeat).parse(timeSignatureMessage, midiFile.messages)
  }

  private def measureTicks(ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * timeSignature.numerator / (timeSignature.denominator / 4))

  private def beatTicks(beat: Int, ticksPerBeat: Int, timeSignature: TimeSignature): Tick =
    new Tick(ticksPerBeat * (beat - 1) / (timeSignature.denominator / (2 * timeSignature.denominator / 4)))

  private def injectClick(measures: Seq[Measure], ticksPerBeat: Int): Seq[Measure] = {
    def clickType(eight: Int): ClickType = {
      if (eight == 1) ClickType.One
      else
        eight % 2 match {
          case 1 => ClickType.Quarter
          case _ => ClickType.Eight
        }
    }

    def measureClick(measure: Measure): Measure = {
      val eightCount = measure.timeSignature.numerator * 8 / measure.timeSignature.denominator
      val clickEventTicks = (1 to eightCount)
        .map(measure.start + beatTicks(_, ticksPerBeat, measure.timeSignature))
      val clickEvents = clickEventTicks
        .zipWithIndex
        .map({ case (t, i) => ClickEvent(t, clickType(i + 1)) })
      new Measure(measure.start,
        measure.timeSignature,
        (clickEvents ++ measure.events).sortWith({ case (a, b) => a.ticks < b.ticks }))
    }

    measures.map(measureClick)
  }

  private class Parser(ticksPerBeat: Int) {
    def parse(ts: MidiMessage, messages: Seq[MidiMessage]): Seq[Measure] =
      parseRec(Nil, ts.get(Accessors.timeSignatureAccessor), ts.ticks, Nil, messages)

    private def withinMeasure(message: MidiMessage, timeSignature: TimeSignature, measureStart: Tick): Boolean = {
      val delta = message.ticks - measureStart
      delta < measureTicks(ticksPerBeat, timeSignature)
    }

    private def nextMeasureStart(start: Tick, timeSignature: TimeSignature): Tick =
      start + measureTicks(ticksPerBeat, timeSignature)

    @tailrec
    private def parseRec(acc: immutable.List[Measure],
                         currTimeSignature: TimeSignature,
                         measureStart: Tick,
                         measure: immutable.List[EngineEvent],
                         rem: Seq[MidiMessage]): Seq[Measure] = {
      // TODO: get rid of this cons + reverse silliness
      if (rem.isEmpty) {
        (new Measure(measureStart, currTimeSignature, measure.reverse) :: acc).reverse
      } else {
        val message = rem.head

        val nextTimeSignature =
          if (message.metaType.contains(MetaType.TimeSignature)) {
            message.get(Accessors.timeSignatureAccessor)
          }
          else currTimeSignature

        if (withinMeasure(message, currTimeSignature, measureStart)) {
          parseRec(acc, nextTimeSignature, measureStart, MessageEvent(message) :: measure, rem.tail)
        } else {
          parseRec(
            new Measure(measureStart, currTimeSignature, measure.reverse) :: acc,
            nextTimeSignature,
            nextMeasureStart(measureStart, currTimeSignature),
            MessageEvent(message) :: Nil,
            rem.tail)
        }
      }
    }
  }

}
package midituutti

import java.io.InputStream

import midituutti.midi.MessageDecoder.{Accessors, TimeSignature}
import midituutti.midi.{MetaType, MidiEvent, Tick}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{FunSpec, Matchers}

trait MyMatchers {

  class EventSetsTimeSignatureToMatcher(expectedTimeSignature: TimeSignature) extends Matcher[MidiEvent] {
    def apply(left: MidiEvent): MatchResult = {
      val message = left.message
      MatchResult(
        message.metaType.contains(MetaType.TimeSignature)
          && message.get(Accessors.timeSignatureAccessor) == expectedTimeSignature,
        s"$left does not set time signature to $expectedTimeSignature",
        s"$left does set time signature to $expectedTimeSignature"
      )
    }
  }

  def setTimeSignatureTo(expectedExtension: TimeSignature) = new EventSetsTimeSignatureToMatcher(expectedExtension)
}


//noinspection ZeroIndexToHead
class TrackStructureSpec extends FunSpec with Matchers with MyMatchers {

  case class Expectation(timeSignature: TimeSignature, firstTick: Tick, eventCount: Int)

  describe("TrackStructure parsing") {

    /**
      * NOTE: This is a very brittle test. Event counts and head event ticks have been checked with a Midi debugger.
      * Event count also depends on our implementation as events may be injected.
      */
    it("should parse varying time signatures correctly") {
      val midiFile = midi.openFile(testFile("measures-44-34-58.mid"))
      val track = TrackStructure.of(midiFile)

      val expectations = List(
        Expectation(TimeSignature(4, 4), Tick(0), 19),
        Expectation(TimeSignature(4, 4), Tick(1920), 9),
        Expectation(TimeSignature(3, 4), Tick(3840), 8),
        Expectation(TimeSignature(3, 4), Tick(5280), 7),
        Expectation(TimeSignature(5, 8), Tick(6720), 12),
        Expectation(TimeSignature(5, 8), Tick(7920), 11),
        Expectation(TimeSignature(4, 4), Tick(9120), 11),
      )

      track.measures should have length expectations.length

      track.measures.zip(expectations.indices.zip(expectations)).foreach {
        case (measure, (index, expected)) => {
          withClue(s"At measure $index, ") {
            withClue("first event ") {
              measure.events.head should setTimeSignatureTo(expected.timeSignature)
            }
            withClue("first event ") {
              measure.events.head.ticks shouldBe expected.firstTick
            }
            measure.events should have length expected.eventCount
          }
        }
      }
    }
  }

  private def testFile(file: String): InputStream = getClass.getResourceAsStream(file)

  private def measure(track: TrackStructure, index: Int)(a: Measure => Unit): Unit = {
    a(track.measures(index))
  }
}

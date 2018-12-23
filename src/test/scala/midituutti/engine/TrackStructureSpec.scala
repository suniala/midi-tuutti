package midituutti.engine

import java.io.InputStream

import midituutti.midi
import midituutti.midi.MessageDecoder.TimeSignature
import midituutti.midi.Tick
import org.scalatest.{FunSpec, Matchers}

class TrackStructureSpec extends FunSpec with Matchers {

  case class Expectation(timeSignature: TimeSignature, firstTick: Tick, eventCount: Int)

  describe("TrackStructure parsing") {

    /**
      * NOTE: This is a somewhat brittle test. Event counts and head event ticks have been checked with a Midi debugger.
      */
    it("should parse varying time signatures correctly") {
      val midiFile = midi.openFile(testFile("measures-44-34-58.mid"))
      val track = TrackStructure.of(midiFile)

      val expectations = List(
        Expectation(TimeSignature(4, 4), Tick(0), 19),
        Expectation(TimeSignature(4, 4), Tick(1920), 8),
        Expectation(TimeSignature(3, 4), Tick(3840), 7),
        Expectation(TimeSignature(3, 4), Tick(5280), 6),
        Expectation(TimeSignature(5, 8), Tick(6720), 11),
        Expectation(TimeSignature(5, 8), Tick(7920), 10),
        Expectation(TimeSignature(4, 4), Tick(9120), 10),
      )

      track.measures should have length expectations.length

      track.measures.zip(expectations.indices.zip(expectations)).foreach {
        case (measure, (index, expected)) =>
          withClue(s"At measure $index, ") {
            withClue("measure time signature ") {
              measure.timeSignature shouldBe expected.timeSignature
            }
            withClue("first event ") {
              measure.events.head.ticks shouldBe expected.firstTick
            }
            measure.events should have length expected.eventCount
          }
      }
    }
  }

  private def testFile(file: String): InputStream = getClass.getResourceAsStream(file)
}

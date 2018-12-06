package midituutti

import java.io.File

import javax.sound.midi._

import scala.io.StdIn

object MidiTuutti extends App {
  val filePath = if (args.length == 1) args(0) else throw new IllegalArgumentException("must give path to midi file")

  val synthesizer = MidiSystem.getSynthesizer
  synthesizer.open()
  println(s"using synthesizer $synthesizer")

  val receiver = synthesizer.getReceiver
  println(s"using receiver $receiver")

  println(s"opening midi file $filePath")
  val seq = MidiSystem.getSequence(new File(filePath))

  val currentTempoInBeatsPerMinute = 120
  val ticksPerSecond = seq.getResolution * (currentTempoInBeatsPerMinute / 60.0)
  val tickSize = 1.0 / ticksPerSecond

  val track: Track = seq.getTracks.apply(0)

  for (eventI <- 0 until track.size) {
    val event = track.get(eventI)
    // Send all messages with timestamps as fast as possible. The receiver will take care of playing each message
    // at the given time.
    receiver.send(event.getMessage, (event.getTick * tickSize * 1000 * 1000).toLong)
  }

  StdIn.readLine("Press enter to quit.")
}

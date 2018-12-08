package midituutti

import midituutti.midi.{OutputTimestamp, Tempo, Tick}

import scala.io.StdIn

object MidiTuutti extends App {
  val filePath = if (args.length == 1) args(0) else throw new IllegalArgumentException("must give path to midi file")

  val synthesizerPort = midi.createDefaultSynthesizerPort
  val midiFile = midi.openFile(filePath)
  val tempo = Tempo(120.0)

  var prevTicks = new Tick(0)
  for (event <- midiFile.track) {
    val ticksDelta = event.ticks - prevTicks
    val timestampDelta = OutputTimestamp.ofTickAndTempo(ticksDelta, midiFile.resolution, tempo)

    if (timestampDelta.nonNil) {
      Thread.sleep(timestampDelta.millisPart, timestampDelta.nanosPart)
    }

    synthesizerPort.send(event.message)

    prevTicks = event.ticks
  }

  StdIn.readLine("Press enter to quit.")
}

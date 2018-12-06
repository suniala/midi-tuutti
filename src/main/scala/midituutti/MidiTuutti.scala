package midituutti

import midituutti.midi.{OutputTimestamp, Tempo}

import scala.io.StdIn

object MidiTuutti extends App {
  val filePath = if (args.length == 1) args(0) else throw new IllegalArgumentException("must give path to midi file")

  val synthesizerPort = midi.createDefaultSynthesizerPort
  val midiFile = midi.openFile(filePath)
  val tempo = Tempo(120.0)

  for (event <- midiFile.track) {
    synthesizerPort.send(event.message,
      OutputTimestamp.ofTickAndTempo(event.ticks, midiFile.resolution, tempo))
  }

  StdIn.readLine("Press enter to quit.")
}

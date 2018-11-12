"""
Play MIDI file on output port.
Run with (for example):
    ./play_midi_file.py 'SH-201 MIDI 1' 'test.mid'
"""
import sys
import mido
import time
from mido import MidiFile

portname = None
dump = False
filename = sys.argv[1]
if len(sys.argv) == 3:
    dump = sys.argv[2] == 'dump'

with mido.open_output(portname) as output:
    try:
        midifile = MidiFile(filename)
        t0 = time.time()

        if dump:
            for track in midifile.tracks:
                for message in track:
                    print(message)
        else:
            for message in midifile.play(meta_messages=True):
                formatted = str(message)
                if not message.is_meta:
                    output.send(message)
                print(formatted)
            print('play time: {:.2f} s (expected {:.2f})'.format(
                    time.time() - t0, midifile.length))

    except KeyboardInterrupt:
        print()
        output.reset()

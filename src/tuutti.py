import queue
import sys
import threading
import time

import mido
from mido import MidiFile

DEFAULT_BPM = 120
DEFAULT_TEMPO_RATIO = 1.0
FILE_SINGLE_TRACK = 0
MESSAGE_TEMPO = 'set_tempo'


def open_file(filename):
    midi_file = MidiFile(filename)

    if midi_file.type != FILE_SINGLE_TRACK:
        raise Exception('Only "single track" files are supported!')

    return midi_file


def read(midi_file, pipeline):
    for message in midi_file.tracks[0]:
        pipeline.put(message)

    print('read: done')
    pipeline.put(None)


class Processor:
    def handle(self, message):
        if message is None:
            return message
        else:
            return self.handle_non_empty(message)

    def handle_non_empty(self, message):
        return message


class TempoModifier(Processor):
    def __init__(self, initial_ratio) -> None:
        super().__init__()
        self.ratio = initial_ratio

    def handle_non_empty(self, message):
        time_ticks = round(message.time * (1 / self.ratio))
        return message.copy(time=time_ticks)


class TimeHandler(Processor):
    def __init__(self, ticks_per_beat) -> None:
        super().__init__()
        self.tempo = mido.bpm2tempo(DEFAULT_BPM)
        self.ticks_per_beat = ticks_per_beat

    def handle_non_empty(self, message):
        # Calculate seconds with the current tempo value as we presumably want tempo changes
        # to take effect only after each tempo message.
        time_sec = mido.tick2second(message.time, self.ticks_per_beat, self.tempo)

        if message.is_meta and message.type == MESSAGE_TEMPO:
            print('got tempo: %d' % message.tempo)
            self.tempo = message.tempo

        return message.copy(time=time_sec)


class QueueWriter(Processor):
    def __init__(self, msg_queue) -> None:
        super().__init__()
        self.msg_queue = msg_queue

    def handle(self, message):
        self.msg_queue.put(message)
        return message


class Pipeline:
    def __init__(self, processors) -> None:
        super().__init__()
        self.processors = processors

    def put(self, message):
        for processor in self.processors:
            message = processor.handle(message)


class Player:
    def __init__(self, msg_queue, abort_flag) -> None:
        super().__init__()
        self.msg_queue = msg_queue
        self.abort_flag = abort_flag

    def run(self):
        port_name = None

        with mido.open_output(port_name) as output:
            while True:
                if self.abort_flag.is_set():
                    print('player: abort')
                    output.reset()
                    break

                message = self.msg_queue.get()
                if message is None:
                    print('player: done')
                    break

                time.sleep(message.time)

                if not message.is_meta:
                    output.send(message)

                self.msg_queue.task_done()


def main():
    filename = sys.argv[1]

    msg_queue = queue.Queue()
    abort_flag = threading.Event()
    player = Player(msg_queue, abort_flag)
    player_thread = threading.Thread(target=player.run)
    player_thread.start()

    try:
        midi_file = open_file(filename)

        tempo_modifier = TempoModifier(DEFAULT_TEMPO_RATIO)
        time_handler = TimeHandler(midi_file.ticks_per_beat)
        queue_writer = QueueWriter(msg_queue)
        pipeline = Pipeline([tempo_modifier, time_handler, queue_writer])

        read(midi_file, pipeline)

        print('main: waiting for player to finish')
        player_thread.join()
    except KeyboardInterrupt:
        print('main: interrupt')
        abort_flag.set()
        player_thread.join()

    print('main: done')


if __name__ == '__main__':
    main()

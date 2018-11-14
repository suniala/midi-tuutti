import queue
import sys
import threading
import time

import mido
from mido import MidiFile

DEFAULT_BPM = 120
FILE_SINGLE_TRACK = 0


def calc_sleep(message):
    # TODO: calculate sleep
    return 0.02


def player(msg_queue, abort_flag):
    port_name = None

    with mido.open_output(port_name) as output:
        while True:
            if abort_flag.is_set():
                print('player: abort')
                output.reset()
                break

            message = msg_queue.get()
            if message is None:
                print('player: done')
                break

            sleep = calc_sleep(message)
            print('player: sleep %.2f' % sleep)
            time.sleep(sleep)

            if not message.is_meta:
                print('player: output')
                output.send(message)

            print('player: msg done')
            msg_queue.task_done()


def reader(filename, pipeline):
    print('reader: start')
    midi_file = MidiFile(filename)

    if midi_file.type != FILE_SINGLE_TRACK:
        raise Exception('Only "single track" files are supported!')

    for message in midi_file.tracks[0]:
        pipeline.put(message)

    print('reader: done')
    pipeline.put(None)


class TimeHandler:
    def __init__(self) -> None:
        super().__init__()
        self.tempo = mido.bpm2tempo(DEFAULT_BPM)

    def handle(self, message):
        print('time handler: ...')
        return message


class QueueWriter:
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


def main():
    filename = sys.argv[1]

    msg_queue = queue.Queue()
    abort_flag = threading.Event()
    player_thread = threading.Thread(target=player, args=[msg_queue, abort_flag])
    player_thread.start()

    time_handler = TimeHandler()
    queue_writer = QueueWriter(msg_queue)
    pipeline = Pipeline([time_handler, queue_writer])

    try:
        reader(filename, pipeline)
        print('main: waiting for player to finish')
        player_thread.join()
    except KeyboardInterrupt:
        print('main: interrupt')
        abort_flag.set()
        player_thread.join()

    print('main: done')


if __name__ == '__main__':
    main()

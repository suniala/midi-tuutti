import queue
import sys
import threading
import time

import mido
from mido import MidiFile


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


def reader(filename, msg_queue):
    print('reader: start')
    midi_file = MidiFile(filename)

    # TODO: handle multi track files properly
    for message in midi_file.tracks[0]:
        msg_queue.put(message)

    print('reader: done')
    msg_queue.put(None)


def main():
    filename = sys.argv[1]

    msg_queue = queue.Queue()
    abort_flag = threading.Event()
    player_thread = threading.Thread(target=player, args=[msg_queue, abort_flag])
    player_thread.start()

    try:
        reader(filename, msg_queue)
        print('main: waiting for player to finish')
        player_thread.join()
    except KeyboardInterrupt:
        print('main: interrupt')
        abort_flag.set()
        player_thread.join()

    print('main: done')


if __name__ == '__main__':
    main()

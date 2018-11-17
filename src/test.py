import unittest

import mido

from bars import inject_bar_meta, bar_ticks, Bar, extract_bars


class TestBars(unittest.TestCase):
    def test_bar_ticks(self):
        self.assertEqual(8, bar_ticks(2, 4, 4))
        self.assertEqual(10, bar_ticks(2, 5, 4))
        # NOTE: Assume ticks_per_beat mean "ticks per whatever is the denominator" and
        # not "tick per quarter note".
        # TODO: verify this assumption
        self.assertEqual(14, bar_ticks(2, 7, 8))

    def test_inject_bar_meta(self):
        ticks_per_beat = 2
        messages = [
            mido.MetaMessage('time_signature', time=0, numerator=4, denominator=4),
            # | x
            mido.Message('note_on', note=100, time=0),
            # | x . . X
            mido.Message('note_off', note=100, time=3),
            # | x . . x . . . . | X
            mido.Message('note_on', note=100, time=5),
            # | x . . x . . . . | x . . . X
            mido.Message('note_on', note=100, time=4),
            # | x . . x . . . . | x . . . x . . . | . . X
            mido.Message('note_on', note=100, time=6),
        ]

        expected = [
            mido.MetaMessage('time_signature', time=0, numerator=4, denominator=4),
            # | x
            mido.Message('note_on', note=100, time=0),
            # | x . . X
            mido.Message('note_off', note=100, time=3),
            # | x . . x . . . . | X
            mido.MetaMessage('time_signature', time=5, numerator=4, denominator=4),
            mido.Message('note_on', note=100, time=0),
            # | x . . x . . . . | x . . . X
            mido.Message('note_on', note=100, time=4),
            # | x . . x . . . . | x . . . x . . . | X
            mido.MetaMessage('time_signature', time=4, numerator=4, denominator=4),
            # | x . . x . . . . | x . . . x . . . | x . X
            mido.Message('note_on', note=100, time=2),
        ]

        self.assertEqual(expected, inject_bar_meta(ticks_per_beat, messages))

    def test_extract_bars(self):
        ticks_per_beat = 2
        messages = [
            # | x
            mido.MetaMessage('time_signature', time=0, numerator=4, denominator=4),
            mido.Message('note_on', note=100, time=0),
            # | x . . X
            mido.Message('note_off', note=100, time=3),
            # | x . . x . . . . | X
            mido.Message('note_on', note=100, time=5),
            # | x . . x . . . . | x . . . X
            mido.Message('note_on', note=100, time=4),
            # | x . . x . . . . | x . . . x . . . | . . X
            mido.Message('note_on', note=100, time=6),
        ]

        expected = [
            Bar([
                # | x
                mido.MetaMessage('time_signature', time=0, numerator=4, denominator=4),
                mido.Message('note_on', note=100, time=0),
                # | x . . X
                mido.Message('note_off', note=100, time=3),
            ]),
            Bar([
                # | x . . x . . . . | X
                mido.MetaMessage('time_signature', time=5, numerator=4, denominator=4),
                mido.Message('note_on', note=100, time=0),
                # | x . . x . . . . | x . . . X
                mido.Message('note_on', note=100, time=4),
            ]),
            Bar([
                # | x . . x . . . . | x . . . x . . . | X
                mido.MetaMessage('time_signature', time=4, numerator=4, denominator=4),
                # | x . . x . . . . | x . . . x . . . | x . X
                mido.Message('note_on', note=100, time=2),
            ])
        ]

        self.assertEqual(expected, extract_bars(ticks_per_beat, messages))

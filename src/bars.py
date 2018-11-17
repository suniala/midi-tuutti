import mido


def extract_bars(ticks_per_beat, messages):
    messages_with_meta = inject_bar_meta(ticks_per_beat, messages)

    def extr_rec(bars, curr_bar_messages, remaining_messages):
        if not remaining_messages:
            bars.append(Bar(curr_bar_messages))
            return bars
        elif curr_bar_messages and 'time_signature' == remaining_messages[0].type:
            bars.append(Bar(curr_bar_messages))
            return extr_rec(bars, [remaining_messages[0]], remaining_messages[1:])
        else:
            curr_bar_messages.append(remaining_messages[0])
            return extr_rec(bars, curr_bar_messages, remaining_messages[1:])

    return extr_rec([], [], messages_with_meta)


def inject_bar_meta(ticks_per_beat, messages):
    output_messages = []
    time_signature = None
    bar_tick = 0
    for message in messages:
        if message.type == 'time_signature':
            time_signature = TimeSignature(ticks_per_beat, message.numerator, message.denominator)
            output_messages.append(message)
            bar_tick = 0
        elif time_signature:
            if time_signature.within_bar(bar_tick + message.time):
                output_messages.append(message)
                bar_tick += message.time
            else:
                output_messages.append(mido.MetaMessage(
                    'time_signature', time=time_signature.next_bar_start_delta(bar_tick),
                    numerator=4, denominator=4))
                output_messages.append(
                    message.copy(time=message.time - time_signature.next_bar_start_delta(bar_tick)))
                bar_tick = 0

    return output_messages


def bar_ticks(ticks_per_beat, numerator, denominator):
    return ticks_per_beat * numerator


class TimeSignature:
    def __init__(self, ticks_per_beat, numerator, denominator) -> None:
        super().__init__()
        self.numerator = numerator
        self.denominator = denominator
        self.bar_ticks = bar_ticks(ticks_per_beat, numerator, denominator)

    def within_bar(self, ticks):
        return ticks < self.bar_ticks

    def next_bar_start_delta(self, bar_tick):
        return self.bar_ticks - bar_tick


class Bar:
    def __init__(self, messages) -> None:
        super().__init__()
        self.messages = messages

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Bar):
            raise TypeError('can\'t compare message to {}'.format(type(other)))
        return self.messages == other.messages

    def __repr__(self) -> str:
        return 'Bar(%s)' % self.messages

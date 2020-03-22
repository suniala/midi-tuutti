# midi-tuutti

A midi player for playing backing tracks while practicing.

## Current features:
* Minimum effort UI (for the programmer, not the user)
* Tempo multiplier
* Jump to next/prev measure
* Mute drums (channel 10)
* Click
* Repeat infinitely
* Keyboard shortcuts

## Midi Devices
Currently only the default Java Runtime midi device is supported. You can get
better sounds by copying a sound bank to `$JAVA_HOME/jre/lib/audio`. See
[Java Sound API: Soundbanks](https://www.oracle.com/technetwork/java/soundbanks-135798.html) for details.

## Ideas
* Remember last file chooser directory
* Remember last n files
* Select range of measures to repeat
* Select click sounds
* Set a constant tempo (instead of multiplying the songs tempo)
* Mixer, maybe just a volume slider for each channel
* UI aesthetics

## Development
[Kotlin](https://kotlinlang.org/) and [TornadoFX](https://github.com/edvin/tornadofx).

Start with: `./gradlew run`.
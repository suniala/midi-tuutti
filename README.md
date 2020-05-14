# midi-tuutti

A midi player for playing backing tracks while practicing.

## Current features

![Midi-Tuutti Demo](/midi-tuutti-demo.png)

* Tempo multiplier (adjust song tempo by a multiplier)
* Constant tempo (override song's tempo)
* Jump to next/prev measure
* Mute drums (channel 10)
* Click
* Repeat infinitely
* Keyboard shortcuts
* A bit unreliable playback

## Keyboard Shortcuts

* Play/stop - space
* Prev/next measure - a, d
* First measure - home
* Tempo adjustment or fixed tempo down, up - s, w
* Switch tempo mode - t
* Disable/enable click - c
* Mute/un-mute drums - m
* Full screen - f11
* Open file - ctrl+o

## Midi Devices
Currently only the default Java Runtime midi device is supported. You can get
better sounds by copying a sound bank to `$JAVA_HOME/jre/lib/audio`. See
[Java Sound API: Soundbanks](https://www.oracle.com/technetwork/java/soundbanks-135798.html) for details.

## Ideas
* Remember last file chooser directory
* Remember last n files
* Select click sounds
* Mixer, maybe just a volume slider for each channel
* Keep track of instrument changes and reset instruments when jumping between measures

## Development
Requires Java 11 (OpenJDK works), uses [Kotlin](https://kotlinlang.org/) and 
[TornadoFX](https://github.com/edvin/tornadofx).

Start with: `./gradlew run`.

Enable engine trace logging with: `-Dmidituutti.engine.trace=true`
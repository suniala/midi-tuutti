# midi-tuutti

A MIDI player for playing backing tracks while practicing.


## How To Get It?

See [Releases](https://github.com/suniala/midi-tuutti/releases) for experimental builds. Download one for your operating system, unzip it and:

* On macOS: Run `image/bin/midi-tuutti`
* On Linux: Run `image/bin/midi-tuutti`
* On Windows: Run `image/bin/midi-tuutti.bat`

For developers: see build and run instructions below.


## Current features

![Midi-Tuutti Player](/doc/midi-tuutti-player-demo.png)

![Midi-Tuutti Mixer](/doc/midi-tuutti-mixer-demo.png)

* Mixer (controls note velocities instead of channel volume)
* For each mixer track, show instruments in a tooltip 
* Click track that can be muted in the mixer
* Tempo multiplier (adjust song tempo by a multiplier)
* Constant tempo (override song's tempo)
* Jump to next/prev measure
* Remember latest program (instrument) changes, pan, pitch and other channel specific adjustments at 
  the beginning of each measure. Meaning: even if you skip measures, these adjustments are not
  skipped.
* Repeat infinitely
* Keyboard shortcuts
* A bit unreliable playback

## Keyboard Shortcuts

* Play/stop - `space`
* Prev/next measure - `left`/`right`
* First measure - `home`
* Tempo adjustment or fixed tempo up/down -  `up`/`down`
* Switch tempo mode - `ctrl + space`
* Switch between player and mixer: `tab`
* Mixer controls:
  * Tracks 1-10 - `1`/`Q` to `0`/`P`
  * Tracks 11-16 + click - `A`/`Z` to `J`/`M`
  * Example for track 1:
    * volume up - `1`
    * volume down - `Q`
    * toggle solo - `shift + 1`
    * toggle mute - `shift + Q`
* Full screen - `f11`
* Open file - `ctrl + o`

## Midi Devices and Sounds
Currently only the default Java Runtime MIDI device is supported. You can get
better sounds by downloading a sound bank file and to:

* When running a midi-tuutti release: `image/lib/audio/`
* When running from sources: `$JAVA_HOME/jre/lib/audio`.
 
See [Java Sound API: Soundbanks](https://www.oracle.com/technetwork/java/soundbanks-135798.html) for details.

## Ideas
* Remember last n files
* Select click sounds
* Play click for one/two measures before playback
* Skip empty measures (ones without any notes) at the beginning of a song

## Development
Requires Java 11 (OpenJDK works), uses [Kotlin](https://kotlinlang.org/), 
[TornadoFX](https://github.com/edvin/tornadofx) and [java-midi-decoder](https://github.com/suniala/java-midi-decoder).

Start with: `./gradlew run`.

Enable engine trace logging with: `-Dmidituutti.engine.trace=true`

TornadoFX layout debugger: `ctrl+shift+d`

Build a jar with: `./gradlew build`

## Running the JAR
You need to have JDK 11 and OpenJFX installed. Run with:
```
# Example for Ubuntu 18.04
java \
  --module-path /usr/share/openjfx/lib \
  --add-modules=javafx.base,javafx.controls,javafx.graphics \
  -jar ./build/libs/midi-tuutti.jar
```

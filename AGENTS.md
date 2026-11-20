# AGENTS.md

MIDI player (midi-tuutti). Kotlin + vanilla JavaFX.

## Build / verify

- Requires JDK 25 (e.g. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`; the build auto-detects it as the toolchain). Gradle 9 cannot run on older JDKs.
- First build after changing dependency versions must run online (`./gradlew compileKotlin test`), afterwards `--offline` works: `./gradlew compileKotlin test --offline` — compile + unit tests
- `./gradlew run` — launch the UI (needs a display); pass a .mid file as arg to load it
- `./gradlew test` — unit tests + headless UI tests (Monocle + software pipeline, no display needed)
- `./gradlew updateScreenshots` — regenerate UI golden screenshots (src/test/resources/midituutti/ui/screenshots); run this on your machine after intentional UI changes, commit the PNGs. `./gradlew test -PexcludeUiTests` skips UI tests (used by CI matrix legs; the ui-test job runs them on ubuntu)
- `./gradlew jlink jlinkZip --offline` — modular runtime image (`build/image`) + zip (`build/image-zip`). Caveat: on the vboxsf share, jlink fails with EPERM writing legal files as non-root — build the image from a copy of the repo on local disk (e.g. /tmp) or run Gradle as root.

## UI conventions

- Views are plain classes with a `val root: <Pane>` built in `also` blocks. Configure objects inside `x.also { y -> y.foo = ... }`, never `val x = Foo(); x.foo = ...`.
- Builders live in `utils.kt`: `Pane.hbox/vbox/label/pane/spacer`, `Node.style(rootFontSize) { prop(...) }` (rem-based inline styles), `Node.shortcut`, `ToggleGroup.selectedValueProperty<T>()`.
- Components in `components/`: `nonFocusableButton`, `nonFocusableToggleButton`, `nonFocusableSlider`, `measureSlider`, `measureRangeControl` — all `Pane` extensions that add themselves to `children`.
- Styles: `Style.css` next to `Style.kt` (loaded via `Style.cssUrl`); class names are `const val`s in `object Style`. Pseudo-classes via `nodeBlinker(node, Style.blink)`.
- Toggle values are stored in `toggle.properties[TOGGLE_GROUP_VALUE_KEY]`; `selectedValueProperty()` is bidirectional (setting it selects the matching toggle).
- `Node.shortcut` installs a **scene-level event filter**, not an accelerator — accelerators lose to TabPane's arrow-key handling.
- Playback events: `PlayerController.addUiEventListener { }` (already marshalled to the FX thread via `Platform.runLater`). No event bus.

## Gotchas hit during migration

- `ObservableList.add(a, b)` is `add(index, element)` — use `addAll(a, b)` for style classes.
- JavaFX 11 `Toggle` has no `value` property; `isUseMaxWidth`/`useMaxWidth` don't resolve as Kotlin properties — use `maxWidth = Region.USE_COMPUTED_SIZE`.
- UI tests run headless (Monocle) with pixel-perfect screenshot assertions; golden PNGs are environment-specific — regenerate them with `./gradlew updateScreenshots`, don't hand-edit. For a visual check, use `./gradlew run`.

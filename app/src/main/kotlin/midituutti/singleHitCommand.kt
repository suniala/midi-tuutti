package midituutti

import midituutti.engine.createSingeNoteHitEngine
import midituutti.midi.Note
import midituutti.midi.OnOff

fun main() {
    val engine = createSingeNoteHitEngine()
    print("Press enter to hit.")
    readLine()
    engine.sendNote(Note(OnOff.On, 10, 40, 127))
    print("Press enter to hit.")
    readLine()
    engine.sendNote(Note(OnOff.On, 10, 41, 127))
    print("Press enter to hit.")
    readLine()
    engine.sendNote(Note(OnOff.On, 10, 42, 127))
    print("Press enter to quit.")
    readLine()
    engine.quit()
}

package midituutti

import javafx.event.EventHandler
import javafx.scene.input.KeyEvent
import midituutti.engine.createSingeNoteHitEngine
import midituutti.midi.MessageDecoder.{Note, OnOff}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{ObjectProperty, ReadOnlyProperty}
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label}
import scalafx.scene.input.KeyCode
import scalafx.scene.layout.{HBox, VBox}

object SingleNoteTuutti extends JFXApp {
  private val engine = createSingeNoteHitEngine()
  private val note = new ObjectProperty[Option[Int]](this, "note", Some(40))

  private val hitButton: Button = new Button {
    text = "Hit!"
    onAction = handle {
      engine.sendNote(Note(OnOff.On, 10, note.value.get, 127))
    }
    focusTraversable = false
  }

  private val nextButton: Button = new Button {
    text = "Next"
    onAction = handle {
      note.setValue(note.value.map(_ + 1))
      hitButton.fire()
    }
    focusTraversable = false
  }

  private val prevButton: Button = new Button {
    text = "Prev"
    onAction = handle {
      note.setValue(note.value.map(_ - 1))
      hitButton.fire()
    }
    focusTraversable = false
  }

  class NoteLabel(val property: ReadOnlyProperty[Option[Int], Option[Int]]) extends Label {
    private val formatted = Bindings.createStringBinding(
      () => property.value match {
        case Some(t: Int) => t.toString
        case _ => "-----"
      },
      property
    )

    minWidth = 50
    maxWidth = 50
    text <== formatted
  }

  private val noteLabel = new NoteLabel(note)

  private val keyHandler: EventHandler[_ >: KeyEvent] = k => k.code match {
    case KeyCode.Space => hitButton.fire()
    case KeyCode.A => prevButton.fire()
    case KeyCode.D => nextButton.fire()
    case _ => // ignore
  }

  stage = new PrimaryStage {
    title = "SingeNoteTuutti"
    scene = new Scene {
      onKeyPressed = keyHandler
      content = new VBox {
        padding = Insets(10)
        spacing = 10
        children = Seq(
          new HBox {
            spacing = 10
            children = Seq(
              prevButton,
              hitButton,
              nextButton
            )
          },
          new HBox {
            children = Seq(
              new Label("Note: "),
              noteLabel
            )
          }
        )
      }
    }
  }

  override def stopApp(): Unit = {
    engine.quit()
  }
}

package midituutti

import javafx.event.EventHandler
import javafx.scene.input.KeyEvent
import midituutti.engine.createEngine
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.beans.binding.Bindings
import scalafx.beans.property.DoubleProperty
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Label, ToggleButton}
import scalafx.scene.input.KeyCode
import scalafx.scene.layout.HBox

object MidiTuutti extends JFXApp {
  private val args = parameters.unnamed
  private val filePath = if (args.nonEmpty) args.head else throw new IllegalArgumentException("must give path to midi file")
  private val engine = createEngine(filePath, None, None)
  private val drumChannel = 10
  private val tempo = new DoubleProperty(this, "tempo", 999.0) {
    onChange { (_, _, newValue) => println(newValue) }
  }

  // FIXME: can't use background thread to modify scalafx thread state, we could use a scalafx Task for engine threads
  // but that makes engine depend on scalafx, maybe use queues and an intermediate scalafx Task for updating props?
  engine.addTempoListener((_, newValue) => tempo.setValue(newValue.bpm))

  private val playButton: ToggleButton = new ToggleButton {
    text = "Play"
    selected = engine.isPlaying
    onAction = handle {
      if (engine.isPlaying) engine.stop()
      else engine.play()
    }
    focusTraversable = false
  }

  private val muteButton: ToggleButton = new ToggleButton {
    text = "Mute drums"
    selected = engine.isMuted(drumChannel)
    onAction = handle {
      if (engine.isMuted(drumChannel)) engine.unMute(drumChannel)
      else engine.mute(drumChannel)
    }
    focusTraversable = false
  }

  private val tempoDisplay: Label = new Label {
    private val formatted = Bindings.createStringBinding(
      () => Option(tempo.value).getOrElse("").toString,
      tempo
    )
    text <== formatted
  }

  private val keyHandler: EventHandler[_ >: KeyEvent] = k => k.code match {
    case KeyCode.Space => playButton.fire()
    case KeyCode.M => muteButton.fire()
    case _ => // ignore
  }

  stage = new PrimaryStage {
    title = "MidiTuutti"
    scene = new Scene {
      onKeyPressed = keyHandler
      content = new HBox {
        padding = Insets(20)
        children = Seq(
          playButton,
          muteButton,
          tempoDisplay
        )
      }
    }
  }

  override def stopApp(): Unit = engine.quit()
}

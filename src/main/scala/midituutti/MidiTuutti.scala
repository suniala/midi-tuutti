package midituutti

import midituutti.engine.createEngine
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Button, ToggleButton}
import scalafx.scene.input.KeyCode
import scalafx.scene.layout.HBox

object MidiTuutti extends JFXApp {
  private val args = parameters.unnamed
  private val filePath = if (args.nonEmpty) args.head else throw new IllegalArgumentException("must give path to midi file")
  private val engine = createEngine(filePath, None, None)
  private val drumChannel = 10

  stage = new PrimaryStage {
    title = "MidiTuutti"
    scene = new Scene {
      onKeyPressed = k => k.code match {
        case KeyCode.Space if !engine.isPlaying =>
          engine.play()
        case KeyCode.Space if engine.isPlaying =>
          engine.stop()
        case _ => // ignore
      }
      content = new HBox {
        padding = Insets(20)
        children = Seq(
          new Button {
            text = "Play"
            onAction = handle {
              engine.play()
            }
            focusTraversable = false
          },
          new Button {
            text = "Stop"
            onAction = handle {
              engine.stop()
            }
            focusTraversable = false
          },
          new ToggleButton {
            text = "Mute drums"
            selected = engine.isMuted(drumChannel)
            onAction = handle {
              if (engine.isMuted(drumChannel)) engine.unMute(drumChannel)
              else engine.mute(drumChannel)
            }
            focusTraversable = false
          }
        )
      }
    }
  }

  override def stopApp(): Unit = engine.quit()
}

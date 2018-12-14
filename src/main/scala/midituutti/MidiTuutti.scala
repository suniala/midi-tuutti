package midituutti

import midituutti.engine.createEngine
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.layout.HBox

object MidiTuutti extends JFXApp {
  private val args = parameters.unnamed
  private val filePath = if (args.nonEmpty) args.head else throw new IllegalArgumentException("must give path to midi file")
  private val engine = createEngine(filePath, None, None)

  stage = new PrimaryStage {
    title = "MidiTuutti"
    scene = new Scene {
      content = new HBox {
        padding = Insets(20)
        children = Seq(
          new Button {
            text = "Play"
            onAction = handle {
              engine.play()
            }
          },
          new Button {
            text = "Stop"
            onAction = handle {
              engine.stop()
            }
          }
        )
      }
    }
  }

  override def stopApp(): Unit = engine.quit()
}

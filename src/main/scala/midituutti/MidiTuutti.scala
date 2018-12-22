package midituutti

import java.util.concurrent.ConcurrentLinkedQueue

import javafx.event.EventHandler
import javafx.scene.input.KeyEvent
import javafx.{concurrent => jfxc}
import midituutti.engine.createEngine
import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.binding.Bindings
import scalafx.beans.property.ObjectProperty
import scalafx.concurrent.Task
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label, ToggleButton}
import scalafx.scene.input.KeyCode
import scalafx.scene.layout.HBox

object MidiTuutti extends JFXApp {
  private val args = parameters.unnamed
  private val filePath = if (args.nonEmpty) args.head else throw new IllegalArgumentException("must give path to midi file")
  private val engine = createEngine(filePath, None, None)
  private val drumChannel = 10
  private val tempo = new ObjectProperty[Option[Double]](this, "tempo", None)

  type EngineEvent = () => Unit
  private val engineEventQueue = new ConcurrentLinkedQueue[EngineEvent]()

  /**
    * Reads engine events from a queue and applies them in the UI thread.
    */
  //noinspection ConvertExpressionToSAM
  object EngineEventBridge extends Task(new jfxc.Task[Unit] {
    override def call(): Unit = {
      while (!isCancelled) {
        var getMore = true
        while (getMore) {
          Option(engineEventQueue.poll()) match {
            case Some(e: EngineEvent) => Platform.runLater(() => e.apply)
            case _ => getMore = false
          }
        }
        Thread.sleep(10)
      }
    }
  })

  val engineEventBridge = new Thread(EngineEventBridge)
  engineEventBridge.start()

  engine.addTempoListener((_, newValue) => engineEventQueue.add(() => tempo.setValue(Some(newValue.bpm))))

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

  private val tempoMulUp: Button = new Button {
    text = "+"
    onAction = handle {
      engine.updateTempoMultiplier(_ + 0.01)
    }
    focusTraversable = false
  }

  private val tempoMulDown: Button = new Button {
    text = "-"
    onAction = handle {
      engine.updateTempoMultiplier(_ - 0.01)
    }
    focusTraversable = false
  }

  private val tempoDisplay: Label = new Label {
    private val formatted = Bindings.createStringBinding(
      () => tempo.value match {
        case Some(t: Double) => t.formatted("%.2f")
        case _ => "-----"
      },
      tempo
    )

    minWidth = 50
    maxWidth = 50
    text <== formatted
  }

  private val keyHandler: EventHandler[_ >: KeyEvent] = k => k.code match {
    case KeyCode.Space => playButton.fire()
    case KeyCode.M => muteButton.fire()
    case KeyCode.W => tempoMulUp.fire()
    case KeyCode.S => tempoMulDown.fire()
    case _ => // ignore
  }

  stage = new PrimaryStage {
    title = "MidiTuutti"
    scene = new Scene {
      onKeyPressed = keyHandler
      content = new HBox {
        padding = Insets(10)
        spacing = 10
        children = Seq(
          playButton,
          muteButton,
          tempoDisplay,
          tempoMulUp,
          tempoMulDown
        )
      }
    }
  }

  override def stopApp(): Unit = {
    EngineEventBridge.cancel
    engine.quit()
  }
}

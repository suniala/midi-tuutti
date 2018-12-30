package midituutti

import java.util.concurrent.ConcurrentLinkedQueue

import javafx.event.EventHandler
import javafx.scene.input.KeyEvent
import javafx.{concurrent => jfxc}
import midituutti.engine.{ClickChannel, MidiChannel, createEngine}
import scalafx.Includes._
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{ObjectProperty, ReadOnlyProperty}
import scalafx.concurrent.Task
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label, ToggleButton}
import scalafx.scene.input.KeyCode
import scalafx.scene.layout.{HBox, VBox}

object MidiTuutti extends JFXApp {
  private val args = parameters.unnamed
  private val filePath = if (args.nonEmpty) args.head else throw new IllegalArgumentException("must give path to midi file")
  private val engine = createEngine(filePath, None, None)
  private val drumChannel = MidiChannel(10)
  private val songTempo = new ObjectProperty[Option[Double]](this, "songTempo", None)
  private val tempoMultiplier = new ObjectProperty[Option[Double]](this, "tempoMultiplier", Some(1.0))
  private val adjustedTempo = new ObjectProperty[Option[Double]](this, "adjustedTempo", None)

  engine.mute(ClickChannel)

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

  engine.addTempoListener(event => engineEventQueue.add(() => {
    songTempo.setValue(event.tempo.map(_.bpm))
    tempoMultiplier.setValue(Some(event.multiplier))
    adjustedTempo.setValue(event.adjustedTempo.map(_.bpm))
  }))

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

  private val clickButton: ToggleButton = new ToggleButton {
    text = "Click"
    selected = !engine.isMuted(ClickChannel)
    onAction = handle {
      if (engine.isMuted(ClickChannel)) engine.unMute(ClickChannel)
      else engine.mute(ClickChannel)
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

  private val tempoMulReset: Button = new Button {
    text = "o"
    onAction = handle {
      engine.updateTempoMultiplier(_ => 1.0)
    }
    focusTraversable = false
  }

  private val prevBarButton: Button = new Button {
    text = "<"
    onAction = handle {
      engine.jumpToBar(_ - 1)
    }
    focusTraversable = false
  }

  private val nextBarButton: Button = new Button {
    text = ">"
    onAction = handle {
      engine.jumpToBar(_ + 1)
    }
    focusTraversable = false
  }

  class TempoLabel(val property: ReadOnlyProperty[Option[Double], Option[Double]]) extends Label {
    private val formatted = Bindings.createStringBinding(
      () => property.value match {
        case Some(t: Double) => t.formatted("%.2f")
        case _ => "-----"
      },
      property
    )

    minWidth = 50
    maxWidth = 50
    text <== formatted
  }

  private val songTempoLabel = new TempoLabel(songTempo)
  private val tempoMultiplierLabel = new TempoLabel(tempoMultiplier)
  private val adjustedTempoLabel = new TempoLabel(adjustedTempo)

  private val keyHandler: EventHandler[_ >: KeyEvent] = k => k.code match {
    case KeyCode.Space => playButton.fire()
    case KeyCode.M => muteButton.fire()
    case KeyCode.W => tempoMulUp.fire()
    case KeyCode.X => tempoMulReset.fire()
    case KeyCode.S => tempoMulDown.fire()
    case KeyCode.A => prevBarButton.fire()
    case KeyCode.D => nextBarButton.fire()
    case KeyCode.C => clickButton.fire()
    case _ => // ignore
  }

  stage = new PrimaryStage {
    title = "MidiTuutti"
    scene = new Scene {
      onKeyPressed = keyHandler
      content = new VBox {
        padding = Insets(10)
        spacing = 10
        children = Seq(
          new HBox {
            spacing = 10
            children = Seq(
              playButton,
              muteButton,
              clickButton,
              tempoMulUp,
              tempoMulDown,
              prevBarButton,
              nextBarButton
            )
          },
          new HBox {
            children = Seq(
              new Label("Song tempo: "),
              songTempoLabel
            )
          },
          new HBox {
            children = Seq(
              new Label("Tempo multiplier: "),
              tempoMultiplierLabel
            )
          },
          new HBox {
            children = Seq(
              new Label("Adjusted tempo: "),
              adjustedTempoLabel
            )
          }
        )
      }
    }
  }

  override def stopApp(): Unit = {
    EngineEventBridge.cancel
    engine.quit()
  }
}

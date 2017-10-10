package com.gilt.gfc.aws.s3.akka

import akka.stream.stage._
import akka.stream._
import akka.stream.scaladsl.Sink

import scala.concurrent.{Future, Promise}
import scala.util.Try

class FoldResourceSink[TState, TItem, Mat](
  open: () => TState,
  onEach: (TState, TItem) => (TState),
  close: TState => Mat,
  onFailure: (Throwable, TState) => Unit
) extends GraphStageWithMaterializedValue[SinkShape[TItem], Future[Mat]] {

  private val in = Inlet[TItem]("Resource.Sink")
  override val shape: Shape = SinkShape(in)

  class FoldResourceSinkLogic(materializedPromise: Promise[Mat]) extends GraphStageLogic(shape) with InHandler {
    var state: TState = _

    override def preStart(): Unit = {
      state = open()
      pull(in)
    }

    def onPush(): Unit = {
      val value = grab(in)
      try {
        state = onEach(state, value)
        pull(in)
      } catch {
        case ex: Throwable => fail(ex)
      }
    }

    override def onUpstreamFinish(): Unit = {
      val materializedValue = Try(close(state))
      materializedPromise.complete(materializedValue)
    }

    override def onUpstreamFailure(ex: Throwable): Unit = {
      fail(ex)
    }

    private def fail(ex: Throwable) = {
      onFailure(ex, state)
      materializedPromise.tryFailure(ex)
      failStage(ex)
    }

    setHandler(in, this)
  }

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Mat]) = {

    val completePromise = Promise[Mat]()

    val stageLogic = new FoldResourceSinkLogic(completePromise)

    stageLogic -> completePromise.future
  }
}

object FoldResourceSink {
  implicit class SinkExtension(val sink: Sink.type) extends AnyVal {

    def foldResource[TState, TItem, Mat](
      open: () => TState,
      onEach: (TState, TItem) => (TState),
      close: TState => Mat,
      onFailure: (Throwable, TState) => Unit = (ex: Throwable, f: TState) => ()
    ): FoldResourceSink[TState, TItem, Mat] = {

      new FoldResourceSink(open, onEach, close, onFailure)

    }
  }
}
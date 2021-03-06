/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
 *
 * PowerAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PowerAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PowerAPI.
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.core

import akka.actor.SupervisorStrategy.{Directive, Resume}
import akka.actor.{Actor, Cancellable, PoisonPill, Props}
import akka.event.LoggingReceive
import org.powerapi.core.ClockChannel.{ClockStart, ClockStop, ClockStopAll, formatClockChildName, subscribeClockChannel, publishClockTick}
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * One child clock is created per frequency.
 * Allows to publish a message in the right topics for a given frequency.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class ClockChild(eventBus: MessageBus, frequency: FiniteDuration) extends ActorComponent {

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case ClockStart(_, freq) if frequency == freq => start()
  } orElse default

  /**
   * Running state, only one timer per ClockChild.
   * An accumulator is used to know how many monitors are using this frequency.
   *
   * @param acc: Accumulator used to know the number of monitors which run at this frequency.
   * @param timer: Timer created for producing ticks.
   */
  def running(acc: Int, timer: Cancellable): Actor.Receive = LoggingReceive {
    case ClockStart(_, freq) if frequency == freq => {
      log.info("clock is already started, reference: {}", frequency.toNanos)
      context.become(running(acc + 1, timer))
    }
    case ClockStop(_, freq) if frequency == freq => stop(acc, timer)
    case _: ClockStopAll => stop(1, timer)
  } orElse default

  /**
   * Start the clock and the associated scheduler for publishing a Tick on the required topic at a given frequency.
   */
  def start(): Unit = {
    val timer = context.system.scheduler.schedule(Duration.Zero, frequency) {
      publishClockTick(frequency)(eventBus)
    } (context.system.dispatcher)

    log.info("clock started, reference: {}", frequency.toNanos)
    context.become(running(1, timer))
  }

  /**
   * Stop the clock and the scheduler.
   *
   * @param acc: Accumulator used to know the number of monitors which run at this frequency.
   * @param timer: Timer created for producing ticks.
   */
  def stop(acc: Int, timer: Cancellable): Unit = {
    if(acc > 1) {
      log.info("this frequency is still used, clock is still running, reference: {}", frequency.toNanos)
      context.become(running(acc - 1, timer))
    }
    else {
      timer.cancel()
      log.info("clock will be stopped, reference: {}", frequency.toNanos)
      self ! PoisonPill
    }
  }
}

/**
 * This clock listens the bus on a given topic and reacts on the received message.
 * It is responsible to handle a pool of clocks for the monitored frequencies.
 *
 * @author <a href="mailto:maxime.colmant@gmail.com">Maxime Colmant</a>
 */
class Clocks(eventBus: MessageBus) extends Supervisor {

  override def preStart(): Unit = {
    subscribeClockChannel(eventBus)(self)
    super.preStart()
  }

  /**
   * ClockChild actors can only launch exception if the message received is not handled.
   */
  def handleFailure: PartialFunction[Throwable, Directive] = {
    case _: UnsupportedOperationException => Resume 
  }

  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case msg: ClockStart => {
      start(msg)
      context.become(running)
    }
  } orElse default

  /**
   * Running state.
   */
  def running: Actor.Receive = LoggingReceive {
    case msg: ClockStart => start(msg)
    case msg: ClockStop => stop(msg)
    case msg: ClockStopAll => stopAll(msg)
  } orElse default

  /**
   * Start a new clock at a given frequency whether is needed.
   * 
   * @param msg: Message received for starting a clock at a given frequency.
   */
  def start(msg: ClockStart): Unit = {
    val name = formatClockChildName(msg.frequency)

    val child = context.child(name) match {
      case Some(actorRef) => actorRef
      case None => context.actorOf(Props(classOf[ClockChild], eventBus, msg.frequency), name)
    }

    child ! msg
  }

  /**
   * Stop a clock for a given frequency if it exists.
   * 
   * @param msg: Message received for stopping a clock at a given frequency.
   */
  def stop(msg: ClockStop): Unit = {
    val name = formatClockChildName(msg.frequency)
    context.actorSelection(name) ! msg
  }

  /**
   * Stop all clocks for all frequencies.
   *
   * @param msg: Message received for stopping all clocks.
   */
  def stopAll(msg: ClockStopAll): Unit = {
    context.actorSelection("*") ! msg
    context.become(receive)
  }
}

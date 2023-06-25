/*
 * Copyright 2022 Computer Systems Department, Jozef Stefan Insitute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package interfaces.amba.axis

import chisel3._
import chisel3.util._
import chiseltest._


class AXIStreamDriver[T <: Data](x: AXIStreamIO[T]) {
  def getBusWidth(): Int = {
    x.bits.getWidth
  }

  def initSource(): this.type = {
    x.valid.poke(false.B)
    this
  }

  def enqueue(data: T, last: Boolean, clock: Clock): Unit = timescope {
    x.bits.poke(data)
    x.valid.poke(true.B)
	x.last.poke(last.B)
    fork.withRegion(Monitor) {
    while (!x.ready.peek().litToBoolean) {
      clock.step()
    }
    }.joinAndStep(clock)
	x.last.poke(false.B)
  }

  def enqueuePacket(data: Seq[T], clock: Clock): Unit = timescope {
    for (elt <- data) {
      enqueue(elt, elt == data.last, clock)
    }
  }
  
  def dequeuePacket(clock: Clock): Seq[BigInt] = {
    var result: Seq[BigInt] = Seq()
    var value: BigInt = 0
    x.ready.poke(true.B)
    fork {
    while(!x.last.peek().litToBoolean) {
        waitForValid(clock)
        x.valid.expect(true.B)
        result = result :+ x.bits.peek().litValue
        if (!x.last.peek().litToBoolean) {
            clock.step()
        }
    }
    }.joinAndStep(clock)
    result
  }

  def initSink(): this.type = {
    x.ready.poke(false.B)
    this
  }

  def waitForValid(clock: Clock): Unit = {
    while (x.valid.peek().litToBoolean == false) {
      clock.step(1)
    }
  }

  def expectDequeue(data: T, clock: Clock): Unit = timescope {
    x.ready.poke(true.B)
    fork
      .withRegion(Monitor) {
        waitForValid(clock)
        x.valid.expect(true.B)
        x.bits.expect(data)
      }
      .joinAndStep(clock)
  }

  def expectDequeueSeq(data: Seq[T], clock: Clock): Unit = timescope {
    for (elt <- data) {
      expectDequeue(elt, clock)
    }
  }
}


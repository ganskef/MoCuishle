/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ganskef.okproxy

import java.net.Socket

/** Handler for server requests, the base class to implement by a server application. */
abstract class Dispatcher {
  /**
   * Returns a response to satisfy `request`. This method may block (for instance, to wait on
   * upstream answers).
   */
  @Throws(InterruptedException::class) abstract fun dispatch(request: Request): Response

  /** TODO */
  open fun connect(socket: Socket, host: String, port: Int): Socket {
    throw UnsupportedOperationException("TLS interception is not implemented.")
  }

  /**
   * TODO Release any resources held by this dispatcher. Any requests that are currently being
   * dispatched should return immediately. Responses returned after shutdown will not be
   * transmitted: their socket connections have already been closed.
   */
  open fun shutdown() {}
}

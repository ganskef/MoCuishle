package de.ganskef.okproxy;

import java.io.IOException;
import java.net.Socket;

/** Handler for server requests. */
public abstract class Dispatcher {

  /** Returns a connected socket or null. */
  public Socket connect(Socket socket, String address, int port) throws IOException {
    return null;
  }

  /** Returns a response to satisfy {@code request}. This method may block. */
  public abstract Response dispatch(Request request) throws InterruptedException;

  /**
   * Release any resources held by this dispatcher. Any requests that are currently being dispatched
   * should return immediately. Responses returned after shutdown will not be transmitted: their
   * socket connections have already been closed.
   */
  public void shutdown() {}
}

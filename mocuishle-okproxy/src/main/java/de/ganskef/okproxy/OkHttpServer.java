/*
 * Copyright (C) 2011 Google Inc.
 * Copyright (C) 2013 Square, Inc.
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
package de.ganskef.okproxy;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Timeout;

/**
 * HTTP/1.1 Server based on <a href=
 * "https://square.github.io/okhttp/3.x/mockwebserver/okhttp3/mockwebserver/MockWebServer.html">MockWebServer</a>
 * version 3.14.9. It's intended to use with a {@link Dispatcher} implementation.
 *
 * <p>This copy removes the dependency to JUnit of MockWebServer. Also it's stripped down, withdraws
 * the HTTP 2.0 server features and extends <code>
 * de.ganskef.okhttp3.OkHttpServer.serveConnection(...).new NamedRunnable(){...}.processConnection()
 * </code> to handle CONNECT in the first requests of a connection.
 *
 * <p>Since MockWebServer is a full featured and modern HTTP implementation, the structure is
 * modified least possible to allow future comparability. All essential classes of the server
 * implementation are inlined and should not be modified. For usage examples see {@link
 * SimpleServer}, {@link SecuredServer}, {@link SimpleProxy}, {@link InterceptionProxy}.
 */
public class OkHttpServer {

  private static final Logger LOG = Logger.getLogger(OkHttpServer.class.getName());

  private static final Pattern LOOPBACK_ADRESSES_PATTERN =
      Pattern.compile("(?:localhost)?/127\\.0\\.0\\.1:", Pattern.CASE_INSENSITIVE);

  private int port;

  private InetSocketAddress inetSocketAddress;

  private boolean started;

  private final String serverName;

  private ExecutorService executor;

  private ServerSocket serverSocket;

  private final Set<Socket> openClientSockets;

  private Dispatcher dispatcher;

  private SSLSocketFactory sslSocketFactory;

  public OkHttpServer() {
    this(OkHttpServer.class.getSimpleName());
  }

  public OkHttpServer(String serverName) {
    Objects.requireNonNull(serverName, "serverName");
    this.serverName = serverName;
    this.port = -1;
    this.openClientSockets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.dispatcher =
        new Dispatcher() {
          /** Default Dispatcher responds to every request with HTTP 501. */
          @Override
          public Response dispatch(Request request) throws InterruptedException {
            return new Response()
                .setStatus("HTTP/1.1 501")
                .setBody("Server Error: 501 Not Implemented")
                .addHeader("Content-Type: text/plain; charset=utf-8");
          }
        };
  }

  /** Serve requests with HTTPS rather than otherwise. */
  public void useHttps(SSLSocketFactory sslSocketFactory) {
    this.sslSocketFactory = sslSocketFactory;
  }

  public void setDispatcher(Dispatcher dispatcher) {
    Objects.requireNonNull(dispatcher);
    this.dispatcher = dispatcher;
  }

  /** Equivalent to {@code start(0)}. */
  public void start() throws IOException {
    start(0);
  }

  /**
   * Starts the server on the loopback interface for the given port.
   *
   * @param port the port to listen to, or 0 for any available port. Automated tests should always
   *     use port 0 to avoid flakiness when a specific port is unavailable.
   */
  public void start(int port) throws IOException {
    start(InetAddress.getByName("localhost"), port);
  }

  /**
   * Starts the server on the given address and port.
   *
   * @param inetAddress the address to create the server socket on
   * @param port the port to listen to, or 0 for any available port. Automated tests should always
   *     use port 0 to avoid flakiness when a specific port is unavailable.
   */
  public void start(InetAddress inetAddress, int port) throws IOException {
    start(new InetSocketAddress(inetAddress, port));
  }

  /**
   * Starts the server and binds to the given socket address.
   *
   * @param inetSocketAddress the socket address to bind the server on
   */
  private synchronized void start(InetSocketAddress inetSocketAddress) throws IOException {
    if (started) {
      throw new IllegalStateException("start() already called");
    }
    this.started = true;
    this.executor = Executors.newCachedThreadPool(threadFactory(serverName, false));
    this.inetSocketAddress = inetSocketAddress;

    this.serverSocket = ServerSocketFactory.getDefault().createServerSocket();
    // Reuse if the user specified a port
    this.serverSocket.setReuseAddress(inetSocketAddress.getPort() != 0);
    this.serverSocket.bind(inetSocketAddress, 50);

    this.port = serverSocket.getLocalPort();

    this.executor.execute(
        new NamedRunnable(
            "Listening: %s",
            LOOPBACK_ADRESSES_PATTERN
                .matcher(serverSocket.getLocalSocketAddress().toString())
                .replaceAll("")) {

          @Override
          protected void execute() {
            try {
              LOG.info("Starting to accept connections");
              acceptConnections();
            } catch (Throwable e) {
              LOG.log(Level.WARNING, "Failed unexpectedly", e);
            }

            // Release all sockets and all threads, even if any close fails.
            closeQuietly(serverSocket);
            for (Iterator<Socket> s = openClientSockets.iterator(); s.hasNext(); ) {
              closeQuietly(s.next());
              s.remove();
            }
            dispatcher.shutdown();
            executor.shutdown();
          }

          private void acceptConnections() throws Exception {
            while (true) {
              try {
                Socket socket = serverSocket.accept();
                openClientSockets.add(socket);
                serveConnection(socket);
              } catch (SocketException e) {
                LOG.info(OkHttpServer.this + " done accepting connections: " + e.getMessage());
                return;
              }
            }
          }
        });
  }

  /**
   * Returns a URL for connecting to this server.
   *
   * @param path the request path, such as "/".
   */
  public HttpUrl url(String path) {
    return new HttpUrl.Builder()
        .scheme(sslSocketFactory != null ? "https" : "http")
        .host(getHostName())
        .port(getPort())
        .build()
        .resolve(path);
  }

  /**
   * Closes {@code closeable}, ignoring any checked exceptions. Does nothing if {@code closeable} is
   * null.
   */
  public static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (RuntimeException rethrown) {
        throw rethrown;
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Closes {@code socket}, ignoring any checked exceptions. Does nothing if {@code socket} is null.
   */
  public static void closeQuietly(Socket socket) {
    if (socket != null) {
      try {
        socket.close();
      } catch (AssertionError e) {
        if (!isAndroidGetsocknameError(e)) throw e;
      } catch (RuntimeException rethrown) {
        throw rethrown;
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Returns true if {@code e} is due to a firmware bug fixed after Android 4.2.2.
   * https://code.google.com/p/android/issues/detail?id=54072
   */
  public static boolean isAndroidGetsocknameError(AssertionError e) {
    return (e.getCause() != null
        && e.getMessage() != null
        && e.getMessage().contains("getsockname failed"));
  }

  private void serveConnection(final Socket raw) {
    executor.execute(
        new NamedRunnable(
            "Read: %s",
            LOOPBACK_ADRESSES_PATTERN
                .matcher(String.valueOf(raw.getRemoteSocketAddress()))
                .replaceAll("")) {
          private String connectedHost = null;
          private int connectedPort = 80;
          private int sequenceNumber = 0;

          @Override
          protected void execute() {
            try {
              processConnection();
            } catch (IOException e) {
              LOG.info(
                  OkHttpServer.this + " connection from " + raw.getInetAddress() + " failed: " + e);
            } catch (Exception e) {
              LOG.log(
                  Level.SEVERE,
                  OkHttpServer.this + " connection from " + raw.getInetAddress() + " crashed",
                  e);
            }
          }

          public void processConnection() throws Exception {
            Socket socket = raw;

            if (sslSocketFactory != null) {
              SSLSocket sslSocket =
                  (SSLSocket)
                      sslSocketFactory.createSocket(
                          socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
              sslSocket.setUseClientMode(false);
              openClientSockets.add(sslSocket);
              sslSocket.startHandshake();
              openClientSockets.remove(socket);
              socket = sslSocket;
            }

            BufferedSource source = Okio.buffer(Okio.source(socket));
            BufferedSink sink = Okio.buffer(Okio.sink(socket));

            Request request = readRequest(socket, source, sink, sequenceNumber);
            if (request != null) {
              if (request.getMethod().equals("CONNECT")) {
                Response response = new Response();
                writeHttpResponse(socket, sink, response);

                String hostPort = request.getHeader("host");
                if (hostPort == null) {
                  throw new ProtocolException("Header host missed in request: " + request);
                }
                int colonIndex = hostPort.indexOf(':');
                if (colonIndex < 0) {
                  throw new ProtocolException("Invalid header host: " + hostPort);
                }
                connectedHost = hostPort.substring(0, colonIndex);
                connectedPort = Integer.parseInt(hostPort.substring(colonIndex + 1));
                Socket connected = dispatcher.connect(socket, connectedHost, connectedPort);
                if (connected != null && connected != raw) {
                  openClientSockets.add(connected);
                  openClientSockets.remove(socket);
                  socket = connected;
                  source = Okio.buffer(Okio.source(socket));
                  sink = Okio.buffer(Okio.sink(socket));
                  while (processOneRequest(socket, source, sink)) {}
                }
              } else {
                processGivenRequest(socket, sink, request);
                while (processOneRequest(socket, source, sink)) {}
              }
            }

            if (sequenceNumber == 0) {
              LOG.warning(
                  OkHttpServer.this
                      + " connection from "
                      + raw.getInetAddress()
                      + " didn't make a request");
            }

            socket.close();
            openClientSockets.remove(socket);
          }

          /**
           * Reads a request and writes its response. Returns true if further calls should be
           * attempted on the socket.
           */
          private boolean processOneRequest(Socket socket, BufferedSource source, BufferedSink sink)
              throws IOException, InterruptedException {
            Request request = readRequest(socket, source, sink, sequenceNumber);
            if (request == null) {
              return false;
            }
            processGivenRequest(socket, sink, request);
            return true;
          }

          private void processGivenRequest(Socket socket, BufferedSink sink, Request request)
              throws IOException {
            try {
              Response response = dispatcher.dispatch(request);
              writeHttpResponse(socket, sink, response);
              sequenceNumber++;

              if (LOG.isLoggable(Level.INFO)) {
                LOG.info(
                    OkHttpServer.this
                        + " received request: "
                        + request
                        + " and responded: "
                        + response);
              }
            } catch (InterruptedException e) {
              throw new IOException(request + " caused by: " + e, e);
            }
          }

          /**
           * @param sequenceNumber the index of this request on this connection.
           */
          private Request readRequest(
              Socket socket, BufferedSource source, BufferedSink sink, int sequenceNumber)
              throws IOException {
            String request;
            try {
              request = source.readUtf8LineStrict();
            } catch (IOException streamIsClosed) {
              return null; // no request because we closed the stream
            }
            if (request.length() == 0) {
              return null; // no request because the stream is exhausted
            }

            Headers.Builder headers = new Headers.Builder();
            long contentLength = -1;
            boolean chunked = false;
            String header;
            while ((header = source.readUtf8LineStrict()).length() != 0) {
              try {
                headers.add(header);
              } catch (IllegalArgumentException e) {
                LOG.warning(OkHttpServer.this + " invalid header ignored " + header);
              }
              String lowercaseHeader = header.toLowerCase(Locale.US);
              if (contentLength == -1 && lowercaseHeader.startsWith("content-length:")) {
                contentLength = Long.parseLong(header.substring(15).trim());
              }
              if (lowercaseHeader.startsWith("transfer-encoding:")
                  && lowercaseHeader.substring(18).trim().equals("chunked")) {
                chunked = true;
              }
            }

            boolean hasBody = false;
            CountingBuffer requestBody = new CountingBuffer();
            List<Integer> chunkSizes = new ArrayList<>();
            if (contentLength != -1) {
              hasBody = contentLength > 0;
              transfer(socket, source, Okio.buffer(requestBody), contentLength, true);
            } else if (chunked) {
              hasBody = true;
              while (true) {
                int chunkSize = Integer.parseInt(source.readUtf8LineStrict().trim(), 16);
                if (chunkSize == 0) {
                  readEmptyLine(source);
                  break;
                }
                chunkSizes.add(chunkSize);
                transfer(socket, source, Okio.buffer(requestBody), chunkSize, true);
                readEmptyLine(source);
              }
            }

            String method = request.substring(0, request.indexOf(' '));
            if (hasBody && !permitsRequestBody(method)) {
              throw new IllegalArgumentException("Request must not have a body: " + request);
            }

            if (connectedHost != null) {
              int indexSlash = request.indexOf('/');
              StringBuilder b = new StringBuilder();
              b.append(request.substring(0, indexSlash));
              b.append("https://");
              b.append(connectedHost);
              if (connectedPort != 443) {
                b.append(':').append(connectedPort);
              }
              b.append(request.substring(indexSlash));
              request = b.toString();
            }

            return new Request(
                request,
                headers.build(),
                chunkSizes,
                requestBody.receivedByteCount,
                requestBody.buffer,
                sequenceNumber,
                socket);
          }
        });
  }

  private boolean permitsRequestBody(String method) {
    return !method.equals("GET") && !method.equals("HEAD");
  }

  private static class CountingBuffer implements Sink {
    private final Buffer buffer = new Buffer();
    private long receivedByteCount;

    @Override
    public void write(Buffer source, long byteCount) throws IOException {
      source.read(buffer, byteCount);
      receivedByteCount += byteCount;
    }

    @Override
    public void flush() throws IOException {}

    @Override
    public Timeout timeout() {
      return Timeout.NONE;
    }

    @Override
    public void close() throws IOException {}
  }

  /**
   * Transfer bytes from {@code source} to {@code sink} until either {@code byteCount} bytes have
   * been transferred or {@code source} is exhausted. The transfer is throttled according to {@code
   * policy}.
   */
  private void transfer(
      Socket socket, BufferedSource source, BufferedSink sink, long byteCount, boolean isRequest)
      throws IOException {
    if (byteCount == 0) {
      return;
    }
    Buffer buffer = new Buffer();
    while (!socket.isClosed()) {
      long toRead = byteCount;

      long read = source.read(buffer, toRead);
      if (read == -1) {
        return;
      }

      sink.write(buffer, read);
      sink.flush();
      byteCount -= read;

      if (byteCount == 0) {
        return;
      }
    }
  }

  private void readEmptyLine(BufferedSource source) throws IOException {
    String line = source.readUtf8LineStrict();
    if (line.length() != 0) {
      throw new IllegalStateException("Expected empty but was: " + line);
    }
  }

  private void writeHttpResponse(Socket socket, BufferedSink sink, Response response)
      throws IOException {
    sink.writeUtf8(response.getStatus());
    sink.writeUtf8("\r\n");

    writeHeaders(sink, response.getHeaders());

    Buffer body = response.getBody();
    if (body == null) {
      return;
    }
    transfer(socket, body, sink, body.size(), false);

    if ("chunked".equalsIgnoreCase(response.getHeaders().get("Transfer-Encoding"))) {
      writeHeaders(sink, response.getTrailers());
    }
  }

  private void writeHeaders(BufferedSink sink, Headers headers) throws IOException {
    for (int i = 0, size = headers.size(); i < size; i++) {
      sink.writeUtf8(headers.name(i));
      sink.writeUtf8(": ");
      sink.writeUtf8(headers.value(i));
      sink.writeUtf8("\r\n");
    }
    sink.writeUtf8("\r\n");
    sink.flush();
  }

  private static String format(String format, Object... args) {
    return String.format(Locale.US, format, args);
  }

  @Override
  public String toString() {
    return LOOPBACK_ADRESSES_PATTERN
        .matcher(serverName + "[" + serverSocket.getLocalSocketAddress() + "]")
        .replaceAll("");
  }

  /** Runnable implementation which always sets its thread name. */
  public abstract static class NamedRunnable implements Runnable {
    protected final String name;

    public NamedRunnable(String format, Object... args) {
      this.name = format(format, args);
    }

    @Override
    public final void run() {
      String oldName = Thread.currentThread().getName();
      Thread.currentThread().setName(name);
      try {
        execute();
      } finally {
        Thread.currentThread().setName(oldName);
      }
    }

    protected abstract void execute();
  }

  private ThreadFactory threadFactory(final String name, final boolean daemon) {
    return runnable -> {
      Thread result = new Thread(runnable, name);
      result.setDaemon(daemon);
      return result;
    };
  }

  protected synchronized void before() {
    if (started) {
      return;
    }
    try {
      start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getPort() {
    before();
    return port;
  }

  public String getHostName() {
    before();
    return inetSocketAddress.getAddress().getCanonicalHostName();
  }

  public Proxy toProxyAddress() {
    before();
    InetSocketAddress address = new InetSocketAddress(getHostName(), getPort());
    return new Proxy(Proxy.Type.HTTP, address);
  }

  public synchronized void shutdown() throws IOException {
    if (!started) {
      return;
    }
    if (serverSocket == null) {
      throw new IllegalStateException("shutdown() before start()");
    }

    // Cause acceptConnections() to break out.
    serverSocket.close();

    // Await shutdown.
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        throw new IOException("Gave up waiting for executor to shut down");
      }
    } catch (InterruptedException e) {
      throw new AssertionError();
    }
  }
}

/*
 * Copyright (C) 2011 Google Inc.
 * Copyright (C) 2012 The Android Open Source Project
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

package de.ganskef.okproxy

import java.io.Closeable
import java.io.IOException
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ServerSocketFactory
import javax.net.ssl.*
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.internal.addHeaderLenient
import okhttp3.internal.closeQuietly
import okhttp3.internal.concurrent.TaskRunner
import okio.*

/** A web server. TODO description */
class OkHttpServer(val serverName: String = OkHttpServer::class.java.simpleName) : Closeable {
  private val taskRunnerBackend =
    TaskRunner.RealBackend(threadFactory("$serverName TaskRunner", daemon = false))

  private fun threadFactory(name: String, daemon: Boolean): ThreadFactory =
    ThreadFactory { runnable ->
      Thread(runnable, name).apply { isDaemon = daemon }
    }

  private val taskRunner = TaskRunner(taskRunnerBackend)
  private val openClientSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

  private val atomicRequestCount = AtomicInteger()

  /**
   * The number of HTTP requests received thus far by this server. This may exceed the number of
   * HTTP connections when connection reuse is in practice.
   */
  val requestCount: Int
    get() = atomicRequestCount.get()

  var serverSocketFactory: ServerSocketFactory? = null
    get() {
      if (field == null && started) {
        field = ServerSocketFactory.getDefault() // Build the default value lazily.
      }
      return field
    }
    set(value) {
      check(!started) { "serverSocketFactory must not be set after start()" }
      field = value
    }

  private var serverSocket: ServerSocket? = null
  private var sslSocketFactory: SSLSocketFactory? = null
  private var clientAuth = CLIENT_AUTH_NONE

  /**
   * The dispatcher used to respond to HTTP requests. The default dispatcher responds to every
   * request with HTTP 501.
   *
   * Other dispatchers can be configured, containing the application logic of the server.
   */
  var dispatcher: Dispatcher =
    object : Dispatcher() {
      override fun dispatch(request: Request): Response {
        return Response()
          .setStatus("HTTP/1.1 501")
          .setBody("Server Error: 501 Not Implemented")
          .addHeader("Content-Type: text/plain; charset=utf-8")
      }
    }

  private var portField: Int = -1
  val port: Int
    get() {
      before()
      return portField
    }

  val hostName: String
    get() {
      before()
      return inetSocketAddress!!.address.canonicalHostName
    }

  private var inetSocketAddress: InetSocketAddress? = null

  private var started: Boolean = false

  @Synchronized
  fun before() {
    if (started) return
    try {
      start()
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  fun toProxyAddress(): Proxy {
    before()
    val address = InetSocketAddress(inetSocketAddress!!.address.canonicalHostName, port)
    return Proxy(Proxy.Type.HTTP, address)
  }

  /**
   * Returns a URL for connecting to this server.
   *
   * @param path the request path, such as "/".
   */
  fun url(path: String): HttpUrl {
    return HttpUrl.Builder()
      .scheme(if (sslSocketFactory != null) "https" else "http")
      .host(hostName)
      .port(port)
      .build()
      .resolve(path)!!
  }

  /** Serve requests with HTTPS rather than otherwise. */
  fun useHttps(sslSocketFactory: SSLSocketFactory) {
    this.sslSocketFactory = sslSocketFactory
  }

  /**
   * Configure the server to not perform SSL authentication of the client. This leaves
   * authentication to another layer such as in an HTTP cookie or header. This is the default and
   * most common configuration.
   */
  fun noClientAuth() {
    this.clientAuth = CLIENT_AUTH_NONE
  }

  /**
   * Configure the server to [want client auth] [SSLSocket.setWantClientAuth]. If the client
   * presents a certificate that is [trusted] [TrustManager] the handshake will proceed normally.
   * The connection will also proceed normally if the client presents no certificate at all! But if
   * the client presents an untrusted certificate the handshake will fail and no connection will be
   * established.
   */
  fun requestClientAuth() {
    this.clientAuth = CLIENT_AUTH_REQUESTED
  }

  /**
   * Configure the server to [need client auth] [SSLSocket.setNeedClientAuth]. If the client
   * presents a certificate that is [trusted] [TrustManager] the handshake will proceed normally. If
   * the client presents an untrusted certificate or no certificate at all the handshake will fail
   * and no connection will be established.
   */
  fun requireClientAuth() {
    this.clientAuth = CLIENT_AUTH_REQUIRED
  }

  /**
   * Starts the server on the loopback interface for the given port.
   *
   * @param port the port to listen to, or 0 for any available port. Automated tests should always
   * use port 0 to avoid flakiness when a specific port is unavailable.
   */
  @Throws(IOException::class)
  @JvmOverloads
  fun start(port: Int = 0) = start(InetAddress.getByName("localhost"), port)

  /**
   * Starts the server on the given address and port.
   *
   * @param inetAddress the address to create the server socket on
   * @param port the port to listen to, or 0 for any available port. Automated tests should always
   * use port 0 to avoid flakiness when a specific port is unavailable.
   */
  @Throws(IOException::class)
  fun start(inetAddress: InetAddress, port: Int) = start(InetSocketAddress(inetAddress, port))

  /**
   * Starts the server and binds to the given socket address.
   *
   * @param inetSocketAddress the socket address to bind the server on
   */
  @Synchronized
  @Throws(IOException::class)
  private fun start(inetSocketAddress: InetSocketAddress) {
    require(!started) { "start() already called" }
    started = true

    this.inetSocketAddress = inetSocketAddress

    serverSocket = serverSocketFactory!!.createServerSocket()

    // Reuse if the user specified a port
    serverSocket!!.reuseAddress = inetSocketAddress.port != 0
    serverSocket!!.bind(inetSocketAddress, 50)

    portField = serverSocket!!.localPort

    taskRunner.newQueue().execute("Listening: $portField", cancelable = false) {
      try {
        LOG.info { "Starting to accept connections" }
        acceptConnections()
      } catch (e: Throwable) {
        LOG.log(Level.WARNING, "$this failed unexpectedly", e)
      }

      // Release all sockets and all threads, even if any close fails.
      serverSocket?.closeQuietly()

      val openClientSocket = openClientSockets.iterator()
      while (openClientSocket.hasNext()) {
        openClientSocket.next().closeQuietly()
        openClientSocket.remove()
      }

      dispatcher.shutdown()
    }
  }

  @Throws(Exception::class)
  private fun acceptConnections() {
    while (true) {
      val socket: Socket
      try {
        socket = serverSocket!!.accept()
      } catch (e: SocketException) {
        LOG.info { "${this@OkHttpServer} done accepting connections: ${e.message}" }
        return
      }

      openClientSockets.add(socket)
      serveConnection(socket)
    }
  }

  @Synchronized
  @Throws(IOException::class)
  fun shutdown() {
    if (!started) return
    require(serverSocket != null) { "shutdown() before start()" }

    // Cause acceptConnections() to break out.
    serverSocket!!.close()

    // Await shutdown.
    for (queue in taskRunner.activeQueues()) {
      if (!queue.idleLatch().await(5, TimeUnit.SECONDS)) {
        throw IOException("Gave up waiting for queue to shut down")
      }
    }
    taskRunnerBackend.shutdown()
  }

  private fun serveConnection(raw: Socket) {
    taskRunner.newQueue().execute("Listening: ${raw.remoteSocketAddress}", cancelable = false) {
      try {
        SocketHandler(raw).handle()
      } catch (e: IOException) {
        LOG.info { "$this connection from ${raw.inetAddress} failed: $e" }
      } catch (e: Exception) {
        LOG.log(Level.SEVERE, "$this connection from ${raw.inetAddress} crashed", e)
      }
    }
  }

  internal inner class SocketHandler(private val raw: Socket) {
    private var sequenceNumber = 0
    private var connectedHost = UNDEFINED_CONNECTED_HOST
    private var connectedPort = UNDEFINED_CONNECTED_PORT

    @Throws(Exception::class)
    fun handle() {
      var socket: Socket
      when {
        sslSocketFactory != null -> {
          socket = sslSocketFactory!!.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true)
          val sslSocket = socket as SSLSocket
          sslSocket.useClientMode = false
          if (clientAuth == CLIENT_AUTH_REQUIRED) {
            sslSocket.needClientAuth = true
          } else if (clientAuth == CLIENT_AUTH_REQUESTED) {
            sslSocket.wantClientAuth = true
          }
          openClientSockets.add(socket)

          sslSocket.startHandshake()

          openClientSockets.remove(raw)
        }
        else -> socket = raw
      }

      var source = socket.source().buffer()
      var sink = socket.sink().buffer()

      val request = readRequest(socket, source, sink, sequenceNumber)
      if (request.method == "CONNECT") {
        val response = Response()
        writeHttpResponse(socket, sink, response)
        val hostPort: String =
          request.getHeader("host")
            ?: throw ProtocolException("Header host missed in request: $request")
        val colonIndex = hostPort.indexOf(':')
        if (colonIndex < 0) {
          throw ProtocolException("Invalid header host: $hostPort")
        }
        connectedHost = hostPort.substring(0, colonIndex)
        connectedPort = hostPort.substring(colonIndex + 1).toInt()
        val connected: Socket
        connected = dispatcher.connect(socket, connectedHost, connectedPort)
        if (connected !== raw) {
          openClientSockets.add(connected)
          openClientSockets.remove(socket)
          socket = connected
          source = socket.source().buffer()
          sink = socket.sink().buffer()
          while (processOneRequest(socket, source, sink)) {}
        }
      } else if (processGivenRequest(socket, sink, request)) {
        while (processOneRequest(socket, source, sink)) {}
      }

      if (sequenceNumber == 0) {
        LOG.warning {
          "${this@OkHttpServer} connection from ${raw.inetAddress} didn't make a request"
        }
      }

      socket.close()
      openClientSockets.remove(socket)
    }

    /**
     * Reads a request and writes its response. Returns true if further calls should be attempted on
     * the socket.
     */
    @Throws(IOException::class, InterruptedException::class)
    private fun processOneRequest(
      socket: Socket,
      source: BufferedSource,
      sink: BufferedSink
    ): Boolean {
      if (source.exhausted()) {
        return false // No more requests on this socket.
      }

      val request = readRequest(socket, source, sink, sequenceNumber)
      return processGivenRequest(socket, sink, request) // expectContinue
    }

    /**
     * Writes the response for the given request. Returns true if further calls should be attempted
     * on the socket.
     */
    @Throws(IOException::class, InterruptedException::class)
    private fun processGivenRequest(socket: Socket, sink: BufferedSink, request: Request): Boolean {
      atomicRequestCount.incrementAndGet()
      if (request.failure != null) {
        return false // Nothing to respond to.
      }

      val response = dispatcher.dispatch(request)
      writeHttpResponse(socket, sink, response)

      LOG.info { "${this@OkHttpServer} received request: $request and responded: $response" }

      sequenceNumber++
      return true // expectContinue
    }

    /** @param sequenceNumber the index of this request on this connection. */
    @Throws(IOException::class)
    private fun readRequest(
      socket: Socket,
      source: BufferedSource,
      sink: BufferedSink,
      sequenceNumber: Int
    ): Request {
      var request = ""
      val headers = Headers.Builder()
      var contentLength = -1L
      var chunked = false
      var expectContinue = false
      val requestBody = Buffer()
      val chunkSizes = mutableListOf<Int>()
      var failure: IOException? = null

      try {
        request = source.readUtf8LineStrict()
        if (request.isEmpty()) {
          throw ProtocolException("no request because the stream is exhausted")
        }

        while (true) {
          val header = source.readUtf8LineStrict()
          if (header.isEmpty()) {
            break
          }
          addHeaderLenient(headers, header)
          val lowercaseHeader = header.toLowerCase(Locale.US)
          if (contentLength == -1L && lowercaseHeader.startsWith("content-length:")) {
            contentLength = header.substring(15).trim().toLong()
          }
          if (lowercaseHeader.startsWith("transfer-encoding:") &&
              lowercaseHeader.substring(18).trim() == "chunked"
          ) {
            chunked = true
          }
          if (lowercaseHeader.startsWith("expect:") &&
              lowercaseHeader.substring(7).trim().equals("100-continue", ignoreCase = true)
          ) {
            expectContinue = true
          }
        }

        var hasBody = false
        if (contentLength != -1L) {
          hasBody = contentLength > 0L
          transfer(socket, source, requestBody.buffer, contentLength, true)
        } else if (chunked) {
          hasBody = true
          while (true) {
            val chunkSize = source.readUtf8LineStrict().trim().toInt(16)
            if (chunkSize == 0) {
              readEmptyLine(source)
              break
            }
            chunkSizes.add(chunkSize)
            transfer(socket, source, requestBody.buffer, chunkSize.toLong(), true)
            readEmptyLine(source)
          }
        }

        if (connectedHost !== UNDEFINED_CONNECTED_HOST) {
          val indexSlash = request.indexOf('/')
          val b = StringBuilder()
          b.append(request.substring(0, indexSlash))
          b.append("https://")
          b.append(connectedHost)
          if (connectedPort != 443) {
            b.append(':').append(connectedPort)
          }
          b.append(request.substring(indexSlash))
          request = b.toString()
        }

        val method = request.substringBefore(' ')
        require(!hasBody || permitsRequestBody(method)) { "Request must not have a body: $request" }
      } catch (e: IOException) {
        failure = e
      }

      return Request(
        request,
        headers.build(),
        chunkSizes,
        requestBody.size,
        requestBody.buffer,
        sequenceNumber,
        socket,
        failure
      )
    }
  }

  private fun permitsRequestBody(method: String): Boolean {
    return method != "GET" && method != "HEAD"
  }

  @Throws(IOException::class)
  private fun writeHttpResponse(socket: Socket, sink: BufferedSink, response: Response) {
    sink.writeUtf8(response.status)
    sink.writeUtf8("\r\n")

    writeHeaders(sink, response.headers)

    val body = response.getBody() ?: return
    transfer(socket, body, sink, body.size, false)

    if ("chunked".equals(response.headers["Transfer-Encoding"], ignoreCase = true)) {
      writeHeaders(sink, response.trailers)
    }
  }

  @Throws(IOException::class)
  private fun writeHeaders(sink: BufferedSink, headers: Headers) {
    for ((name, value) in headers) {
      sink.writeUtf8(name)
      sink.writeUtf8(": ")
      sink.writeUtf8(value)
      sink.writeUtf8("\r\n")
    }
    sink.writeUtf8("\r\n")
    sink.flush()
  }

  /**
   * Transfer bytes from [source] to [sink] until either [byteCount] bytes have been transferred or
   * [source] is exhausted.
   */
  @Throws(IOException::class)
  private fun transfer(
    socket: Socket,
    source: BufferedSource,
    sink: BufferedSink,
    byteCount: Long,
    isRequest: Boolean
  ) {
    var byteCountNum = byteCount
    if (byteCountNum == 0L) return

    val buffer = Buffer()
    val bytesPerPeriod = Long.MAX_VALUE

    while (!socket.isClosed) {
      var b = 0L
      while (b < bytesPerPeriod) {
        // Ensure we do not read past the allotted bytes in this period.
        var toRead = minOf(byteCountNum, bytesPerPeriod - b)

        val read = source.read(buffer, toRead)
        if (read == -1L) return

        sink.write(buffer, read)
        sink.flush()
        b += read
        byteCountNum -= read

        if (byteCountNum == 0L) return
      }
    }
  }

  @Throws(IOException::class)
  private fun readEmptyLine(source: BufferedSource) {
    val line = source.readUtf8LineStrict()
    check(line.isEmpty()) { "Expected empty but was: $line" }
  }

  override fun toString(): String = "${serverName}[$portField]"

  @Throws(IOException::class) override fun close() = shutdown()

  companion object {
    private const val UNDEFINED_CONNECTED_HOST = "undefined_connected_host"
    private const val UNDEFINED_CONNECTED_PORT = -1

    private const val CLIENT_AUTH_NONE = 0
    private const val CLIENT_AUTH_REQUESTED = 1
    private const val CLIENT_AUTH_REQUIRED = 2

    private val LOG = Logger.getLogger(OkHttpServer::class.java.name)
  }
}

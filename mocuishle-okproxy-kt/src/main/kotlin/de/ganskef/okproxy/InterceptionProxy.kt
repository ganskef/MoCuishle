package de.ganskef.okproxy

import java.io.IOException
import java.net.Socket
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import okhttp3.OkHttpClient

/**
 * Base implementation of a Man In The Middle proxy server to intercept HTTPS connections. See
 * [OkHttpClient] for interceptors, events, caching and other upstream features.
 */
open class InterceptionProxy
@JvmOverloads
constructor(
  port: Int = DEFAULT_PORT,
  impersonationBuilder: Impersonation.Builder = Impersonation.Builder(),
  clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
) : SimpleProxy(port, clientBuilder) {
  private val impersonator: Impersonation = impersonationBuilder.build()
  private val hosts: LinkedHashMap<String, SSLContext> = LinkedHashMap(0, 0.75f, true)
  private val lock = ReentrantLock()

  @Throws(IOException::class)
  override fun connect(socket: Socket, host: String, port: Int): Socket {
    val sslContext: SSLContext?
    if (hosts.containsKey(host)) {
      sslContext = hosts[host]
    } else {
      lock.lock()
      try {
        sslContext = impersonator.createSSLContext(host)
        hosts[host] = sslContext
      } finally {
        lock.unlock()
      }
    }
    val sslSocketFactory = sslContext!!.socketFactory
    val sslSocket = sslSocketFactory.createSocket(socket, host, port, true) as SSLSocket
    sslSocket.useClientMode = false
    sslSocket.startHandshake()
    return sslSocket
  }

  override fun isImplemented(request: Request): Boolean {
    val m = REQUEST_PATTERN.matcher(request.requestLine)
    return m.matches()
  }

  /**
   * Workaround to handle race conditions on macOS (openjdk version "11.0.10" 2021-01-19), for
   * example:
   *
   * * javax.net.ssl.SSLPeerUnverifiedException: Hostname www.ndr.de not verified (no certificates)
   * * java.net.ConnectException: Failed to connect to www.apple.com/2a02:26f0:d5:493:0:0:0:1aca:443
   *
   * The second try always succeed, never seen a second fail. Linux works well, Windows is not
   * tested yet.
   */
  override fun tryUpstreamRequest(request: Request): Response {
    val throwables: MutableList<Throwable> = LinkedList()
    while (true) {
      try {
        return callUpstreamRequest(request)
      } catch (e: IOException) {
        LOG.warning { "retry request: $request caused by: $e" }
        throwables.add(e)
        if (throwables.size >= MAX_TRY_COUNT) {
          return answerUpstreamError(request, throwables.toString())
        }
      }
    }
  }

  companion object {
    private const val DEFAULT_PORT = 9090
    private val LOG = Logger.getLogger(InterceptionProxy::class.toString())
    private val REQUEST_PATTERN = Pattern.compile("[A-Z]{3,8} (https?://.{1,2048}) HTTP/1\\.[01]")
    private const val MAX_TRY_COUNT = 2

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        val port = if (args.isNotEmpty()) args[0].toInt() else DEFAULT_PORT
        val server = InterceptionProxy(port)
        server.run()
      } catch (e: Exception) {
        println(
          String.format(
            "Usage: %s <port|%s>",
            InterceptionProxy::class.java.simpleName,
            DEFAULT_PORT
          )
        )
        throw e
      }
    }
  }
}

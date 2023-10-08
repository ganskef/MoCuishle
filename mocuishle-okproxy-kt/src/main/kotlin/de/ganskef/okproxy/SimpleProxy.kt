package de.ganskef.okproxy

import java.io.IOException
import java.net.Proxy
import java.util.*
import java.util.logging.Logger
import java.util.regex.Pattern
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.internal.http.HttpMethod.requiresRequestBody
import okio.Buffer

/**
 * Base implementation of a HTTP proxy server. See [OkHttpClient] for interceptors, events, caching
 * and other upstream features.
 */
open class SimpleProxy
@JvmOverloads
constructor(
  private val initialPort: Int,
  clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
) : Dispatcher() {
  private val client: OkHttpClient
  private var server: OkHttpServer? = null

  init {
    client =
      clientBuilder // Avoid to many redirects exception.
        .followRedirects(false)
        .followSslRedirects(false) // Don't try to use HTTP 2.0 stuff
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
  }

  @Throws(IOException::class)
  fun run() {
    server = OkHttpServer()
    server!!.dispatcher = this
    server!!.start(initialPort)
  }

  fun toProxyAddress(): Proxy {
    Objects.requireNonNull(server, "server")
    return server!!.toProxyAddress()
  }

  protected open fun isImplemented(request: Request): Boolean {
    val m = REQUEST_PATTERN.matcher(request.requestLine)
    return m.matches()
  }

  override fun dispatch(request: Request): Response {
    val connection = request.getHeader("Connection")
    if (connection != null && connection.contains("Upgrade")) {
      return answerError(BLOCKED_STATUS, request, "header connection: $connection")
    }
    return if (!isImplemented(request)) {
      answerError(BLOCKED_STATUS, request, "NOT IMPLEMENTED")
    } else tryUpstreamRequest(request)
  }

  /** May be overriden to implement a specialized exception handling. */
  protected open fun tryUpstreamRequest(request: Request): Response {
    return try {
      callUpstreamRequest(request)
    } catch (e: IOException) {
      LOG.warning { "failed request: $request caused by: $e" }
      answerUpstreamError(request, e.toString())
    }
  }

  /**
   * Handling of the upstream request, shouldn't be overridden. See [OkHttpClient.Builder] to modify
   * the client.
   */
  @Throws(IOException::class)
  protected fun callUpstreamRequest(request: Request): Response {
    val rb = okhttp3.Request.Builder()
    rb.url(request.requestUrl!!)
    rb.headers(request.headers)
    val method = request.method
    val requestBody: RequestBody?
    if (request.bodySize > 0 || requiresRequestBody(method!!)) {
      val type = request.getHeader("Content-Type")?.toMediaTypeOrNull()
      requestBody = RequestBody.create(type, request.body.readByteArray())
    } else {
      requestBody = null
    }
    rb.method(method!!, requestBody)
    client.newCall(rb.build()).execute().use { response ->
      val body = Buffer()
      body.writeAll(response.body!!.source())
      return Response()
        .setResponseCode(response.code)
        .setHeaders(response.headers)
        .removeHeader("connection")
        .removeHeader("proxy-authenticate")
        .removeHeader("proxy-authorization")
        .removeHeader("te")
        .removeHeader("trailer")
        .removeHeader("upgrade")
        .removeHeader("transfer-encoding")
        .removeHeader("content-length")
        .setBody(body)
    }
  }

  private fun answer(status: String, message: String): Response {
    return Response()
      .setStatus(status)
      .addHeader("Content-Type: text/plain; charset=utf-8")
      .setBody(message)
  }

  private fun answerError(status: String, request: Request, reason: String): Response {
    return answer(
      status,
      "SERVER ERROR: " +
        javaClass.simpleName +
        " couldn't fulfil " +
        request +
        " caused by: " +
        reason
    )
  }

  protected fun answerUpstreamError(request: Request, reason: String): Response {
    return answerError(ERROR_STATUS, request, reason)
  }

  companion object {
    private const val DEFAULT_PORT = 9090

    private val LOG = Logger.getLogger(SimpleProxy::class.toString())
    private val REQUEST_PATTERN = Pattern.compile("[A-Z]{3,8} (http://.{1,2048}) HTTP/1\\.[01]")
    private const val ERROR_STATUS = "HTTP/1.1 500"
    private const val BLOCKED_STATUS = "HTTP/1.1 501"

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        val port = if (args.size > 0) args[0].toInt() else DEFAULT_PORT
        val server = SimpleProxy(port)
        server.run()
      } catch (e: Exception) {
        println(
          String.format("Usage: %s <port|%s>", SimpleProxy::class.java.simpleName, DEFAULT_PORT)
        )
        throw e
      }
    }
  }
}

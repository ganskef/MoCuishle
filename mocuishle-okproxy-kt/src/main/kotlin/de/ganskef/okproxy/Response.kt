/*
 * Copyright (C) 2011 Google Inc.
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

import okhttp3.Headers
import okio.Buffer

/** TODO A scripted response to be replayed by the mock web server. */
class Response : Cloneable {
  /** Returns the HTTP response line, such as "HTTP/1.1 200 OK". */
  @set:JvmName("status") var status: String = ""

  private var headersBuilder = Headers.Builder()
  private var trailersBuilder = Headers.Builder()

  /** The HTTP headers, such as "Content-Length: 0". */
  @set:JvmName("headers")
  var headers: Headers
    get() = headersBuilder.build()
    set(value) {
      this.headersBuilder = value.newBuilder()
    }

  @set:JvmName("trailers")
  var trailers: Headers
    get() = trailersBuilder.build()
    set(value) {
      this.trailersBuilder = value.newBuilder()
    }

  private var body: Buffer? = null

  /** Creates a new mock response with an empty body. */
  init {
    setResponseCode(200)
    setHeader("Content-Length", 0L)
  }

  public override fun clone(): Response {
    val result = super.clone() as Response
    result.headersBuilder = headersBuilder.build().newBuilder()
    return result
  }

  @JvmName("-deprecated_getStatus")
  @Deprecated(
    message = "moved to var",
    replaceWith = ReplaceWith(expression = "status"),
    level = DeprecationLevel.ERROR
  )
  fun getStatus(): String = status

  /**
   * Sets the status and returns this.
   *
   * This was deprecated in OkHttp 4.0 in favor of the [status] val. In OkHttp 4.3 it is
   * un-deprecated because Java callers can't chain when assigning Kotlin vals. (The getter remains
   * deprecated).
   */
  fun setStatus(status: String) = apply { this.status = status }

  fun setResponseCode(code: Int): Response {
    val reason =
      when (code) {
        in 100..199 -> "Informational"
        in 200..299 -> "OK"
        in 300..399 -> "Redirection"
        in 400..499 -> "Client Error"
        in 500..599 -> "Server Error"
        else -> "${OkHttpServer::class.java.simpleName} Response"
      }
    return apply { status = "HTTP/1.1 $code $reason" }
  }

  /**
   * Removes all HTTP headers including any "Content-Length" and "Transfer-encoding" headers that
   * were added by default.
   */
  fun clearHeaders() = apply { headersBuilder = Headers.Builder() }

  /**
   * Adds [header] as an HTTP header. For well-formed HTTP [header] should contain a name followed
   * by a colon and a value.
   */
  fun addHeader(header: String) = apply { headersBuilder.add(header) }

  /**
   * Adds a new header with the name and value. This may be used to add multiple headers with the
   * same name.
   */
  fun addHeader(name: String, value: Any) = apply { headersBuilder.add(name, value.toString()) }

  /** Removes all headers named [name], then adds a new header with the name and value. */
  fun setHeader(name: String, value: Any) = apply {
    removeHeader(name)
    addHeader(name, value)
  }

  /** Removes all headers named [name]. */
  fun removeHeader(name: String) = apply { headersBuilder.removeAll(name) }

  /** Returns a copy of the raw HTTP payload. */
  fun getBody(): Buffer? = body?.clone()

  fun setBody(body: Buffer) = apply {
    setHeader("Content-Length", body.size)
    this.body = body.clone() // Defensive copy.
  }

  /** Sets the response body to the UTF-8 encoded bytes of [body]. */
  fun setBody(body: String): Response = setBody(Buffer().writeUtf8(body))

  /** Sets the response body to [body], chunked every [maxChunkSize] bytes. */
  fun setChunkedBody(body: Buffer, maxChunkSize: Int) = apply {
    removeHeader("Content-Length")
    headersBuilder.add(CHUNKED_BODY_HEADER)

    val bytesOut = Buffer()
    while (!body.exhausted()) {
      val chunkSize = minOf(body.size, maxChunkSize.toLong())
      bytesOut.writeHexadecimalUnsignedLong(chunkSize)
      bytesOut.writeUtf8("\r\n")
      bytesOut.write(body, chunkSize)
      bytesOut.writeUtf8("\r\n")
    }
    bytesOut.writeUtf8("0\r\n") // Last chunk. Trailers follow!
    this.body = bytesOut
  }

  /**
   * Sets the response body to the UTF-8 encoded bytes of [body], chunked every [maxChunkSize]
   * bytes.
   */
  fun setChunkedBody(body: String, maxChunkSize: Int): Response =
    setChunkedBody(Buffer().writeUtf8(body), maxChunkSize)

  @JvmName("-deprecated_getHeaders")
  @Deprecated(
    message = "moved to var",
    replaceWith = ReplaceWith(expression = "headers"),
    level = DeprecationLevel.ERROR
  )
  fun getHeaders(): Headers = headers

  /**
   * Sets the headers and returns this.
   *
   * This was deprecated in OkHttp 4.0 in favor of the [headers] val. In OkHttp 4.3 it is
   * un-deprecated because Java callers can't chain when assigning Kotlin vals. (The getter remains
   * deprecated).
   */
  fun setHeaders(headers: Headers) = apply { this.headers = headers }

  @JvmName("-deprecated_getTrailers")
  @Deprecated(
    message = "moved to var",
    replaceWith = ReplaceWith(expression = "trailers"),
    level = DeprecationLevel.ERROR
  )
  fun getTrailers(): Headers = trailers

  /**
   * Sets the trailers and returns this.
   *
   * This was deprecated in OkHttp 4.0 in favor of the [trailers] val. In OkHttp 4.3 it is
   * un-deprecated because Java callers can't chain when assigning Kotlin vals. (The getter remains
   * deprecated).
   */
  fun setTrailers(trailers: Headers) = apply { this.trailers = trailers }

  override fun toString() = status

  companion object {
    private const val CHUNKED_BODY_HEADER = "Transfer-encoding: chunked"
  }
}

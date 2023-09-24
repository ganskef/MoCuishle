package de.ganskef.okproxy;

import okhttp3.Headers;
import okio.Buffer;

public class Response {
  private static final String CHUNKED_BODY_HEADER = "Transfer-encoding: chunked";

  private String status;
  private Headers.Builder headers = new Headers.Builder();
  private Headers.Builder trailers = new Headers.Builder();

  private Buffer body;

  /** Creates a new response with an empty body. */
  public Response() {
    setResponseCode(200);
    setHeader("Content-Length", 0);
  }

  @Override
  public Response clone() {
    try {
      Response result = (Response) super.clone();
      result.headers = headers.build().newBuilder();
      return result;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  /** Returns the HTTP response line, such as "HTTP/1.1 200 OK". */
  public String getStatus() {
    return status;
  }

  public Response setResponseCode(int code) {
    String reason = OkHttpServer.class.getSimpleName() + " Response";
    if (code >= 100 && code < 200) {
      reason = "Informational";
    } else if (code >= 200 && code < 300) {
      reason = "OK";
    } else if (code >= 300 && code < 400) {
      reason = "Redirection";
    } else if (code >= 400 && code < 500) {
      reason = "Client Error";
    } else if (code >= 500 && code < 600) {
      reason = "Server Error";
    }
    return setStatus("HTTP/1.1 " + code + " " + reason);
  }

  public Response setStatus(String status) {
    this.status = status;
    return this;
  }

  /** Returns the HTTP headers, such as "Content-Length: 0". */
  public Headers getHeaders() {
    return headers.build();
  }

  public Headers getTrailers() {
    return trailers.build();
  }

  /**
   * Removes all HTTP headers including any "Content-Length" and "Transfer-encoding" headers that
   * were added by default.
   */
  public Response clearHeaders() {
    headers = new Headers.Builder();
    return this;
  }

  /**
   * Adds {@code header} as an HTTP header. For well-formed HTTP {@code header} should contain a
   * name followed by a colon and a value.
   */
  public Response addHeader(String header) {
    headers.add(header);
    return this;
  }

  /**
   * Adds a new header with the name and value. This may be used to add multiple headers with the
   * same name.
   */
  public Response addHeader(String name, Object value) {
    headers.add(name, String.valueOf(value));
    return this;
  }

  /** Removes all headers named {@code name}, then adds a new header with the name and value. */
  public Response setHeader(String name, Object value) {
    removeHeader(name);
    return addHeader(name, value);
  }

  /** Replaces all headers with those specified. */
  public Response setHeaders(Headers headers) {
    this.headers = headers.newBuilder();
    return this;
  }

  /** Replaces all trailers with those specified. */
  public Response setTrailers(Headers trailers) {
    this.trailers = trailers.newBuilder();
    return this;
  }

  /** Removes all headers named {@code name}. */
  public Response removeHeader(String name) {
    headers.removeAll(name);
    return this;
  }

  /** Returns a copy of the raw HTTP payload. */
  public Buffer getBody() {
    return body != null ? body.clone() : null;
  }

  public Response setBody(Buffer body) {
    setHeader("Content-Length", body.size());
    this.body = body.clone(); // Defensive copy.
    return this;
  }

  /** Sets the response body to the UTF-8 encoded bytes of {@code body}. */
  public Response setBody(String body) {
    try (Buffer b = new Buffer()) {
      return setBody(b.writeUtf8(body));
    }
  }

  /** Sets the response body to {@code body}, chunked every {@code maxChunkSize} bytes. */
  public Response setChunkedBody(Buffer body, int maxChunkSize) {
    removeHeader("Content-Length");
    headers.add(CHUNKED_BODY_HEADER);

    Buffer bytesOut = new Buffer();
    while (!body.exhausted()) {
      long chunkSize = Math.min(body.size(), maxChunkSize);
      bytesOut.writeHexadecimalUnsignedLong(chunkSize);
      bytesOut.writeUtf8("\r\n");
      bytesOut.write(body, chunkSize);
      bytesOut.writeUtf8("\r\n");
    }
    bytesOut.writeUtf8("0\r\n"); // Last chunk. Trailers follow!

    this.body = bytesOut;
    return this;
  }

  /**
   * Sets the response body to the UTF-8 encoded bytes of {@code body}, chunked every {@code
   * maxChunkSize} bytes.
   */
  public Response setChunkedBody(String body, int maxChunkSize) {
    try (Buffer b = new Buffer()) {
      return setChunkedBody(b.writeUtf8(body), maxChunkSize);
    }
  }

  @Override
  public String toString() {
    return status;
  }
}

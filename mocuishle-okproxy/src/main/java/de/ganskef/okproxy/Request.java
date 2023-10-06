package de.ganskef.okproxy;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import javax.net.ssl.SSLSocket;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;

public class Request {
  private final String requestLine;
  private final String method;
  private final String path;
  private final Headers headers;
  private final List<Integer> chunkSizes;
  private final long bodySize;
  private final Buffer body;
  private final HttpUrl requestUrl;

  public Request(
      String requestLine,
      Headers headers,
      List<Integer> chunkSizes,
      long bodySize,
      Buffer body,
      int sequenceNumber,
      Socket socket) {
    this.requestLine = requestLine;
    this.headers = headers;
    this.chunkSizes = chunkSizes;
    this.bodySize = bodySize;
    this.body = body;

    if (requestLine != null) {
      int methodEnd = requestLine.indexOf(' ');
      int pathEnd = requestLine.indexOf(' ', methodEnd + 1);
      this.method = requestLine.substring(0, methodEnd);
      String middleString = requestLine.substring(methodEnd + 1, pathEnd);
      if (method.equals("CONNECT")) {
        this.requestUrl = HttpUrl.parse("https://" + middleString);
        this.path = null;
      } else if (middleString.length() > 4
          && middleString.substring(0, 4).equalsIgnoreCase("http")) {
        this.requestUrl = HttpUrl.parse(middleString);
        int pathBegin = middleString.indexOf('/', 8);
        this.path = pathBegin == -1 ? "/" : middleString.substring(pathBegin);
      } else {
        String scheme = socket instanceof SSLSocket ? "https" : "http";
        InetAddress inetAddress = socket.getLocalAddress();

        String hostname = inetAddress.getHostName();
        if (inetAddress instanceof Inet6Address) {
          hostname = "[" + hostname + "]";
        }

        int localPort = socket.getLocalPort();
        // Allow null in failure case to allow for testing bad requests
        this.requestUrl =
            HttpUrl.parse(String.format("%s://%s:%s%s", scheme, hostname, localPort, middleString));

        int pathBegin = middleString.indexOf('/');
        this.path = pathBegin == -1 ? "/" : middleString.substring(pathBegin);
      }
    } else {
      this.requestUrl = null;
      this.method = null;
      this.path = null;
    }
  }

  public Request(Request request, Headers headers) {
    this.requestLine = request.requestLine;
    this.method = request.method;
    this.path = request.path;
    this.headers = headers;
    this.chunkSizes = request.chunkSizes;
    this.bodySize = request.bodySize;
    this.body = request.body;
    this.requestUrl = request.requestUrl;
  }

  public HttpUrl getRequestUrl() {
    return requestUrl;
  }

  public String getRequestLine() {
    return requestLine;
  }

  public String getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  /** Returns all headers. */
  public Headers getHeaders() {
    return headers;
  }

  /** Returns the first header named {@code name}, or null if no such header exists. */
  public String getHeader(String name) {
    List<String> values = headers.values(name);
    return values.isEmpty() ? null : values.get(0);
  }

  /**
   * Returns the sizes of the chunks of this request's body, or an empty list if the request's body
   * was empty or unchunked.
   */
  public List<Integer> getChunkSizes() {
    return chunkSizes;
  }

  /** Returns the total size of the body of this POST request (before truncation). */
  public long getBodySize() {
    return bodySize;
  }

  /** Returns the body of this POST request. This may be truncated. */
  public Buffer getBody() {
    return body;
  }

  @Override
  public String toString() {
    return requestLine;
  }
}

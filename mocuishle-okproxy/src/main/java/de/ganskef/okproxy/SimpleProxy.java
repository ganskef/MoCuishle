package de.ganskef.okproxy;

import java.io.IOException;
import java.net.Proxy;
import java.util.Collections;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.internal.http.HttpMethod;
import okio.Buffer;

/**
 * Base implementation of an HTTP proxy server. See {@link OkHttpClient} for interceptors, events,
 * caching and other upstream features.
 */
public class SimpleProxy extends Dispatcher {

  private static final Logger LOG = Logger.getLogger(SimpleProxy.class.toString());

  private static final int DEFAULT_PORT = 9090;

  private static final Pattern REQUEST_PATTERN =
      Pattern.compile("[A-Z]{3,8} (http://.{1,2048}) HTTP/1\\.[01]");

  private static final String ERROR_STATUS = "HTTP/1.1 500", BLOCKED_STATUS = "HTTP/1.1 501";

  protected final int portArgument;

  private final OkHttpClient client;

  private OkHttpServer server;

  public SimpleProxy(int port) {
    this(port, new OkHttpClient.Builder());
  }

  public SimpleProxy(int port, OkHttpClient.Builder clientBuilder) {
    this.portArgument = port;
    this.client =
        clientBuilder
            // Avoid to many redirects exception.
            .followRedirects(false)
            .followSslRedirects(false)
            // Don't try to use HTTP 2.0 stuff
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .build();
  }

  public void run() throws IOException {
    server = new OkHttpServer(getClass().getSimpleName());
    server.setDispatcher(this);
    server.start(portArgument);
  }

  public Proxy toProxyAddress() {
    Objects.requireNonNull(server, "server");
    return server.toProxyAddress();
  }

  protected boolean isImplemented(Request request) {
    Matcher m = REQUEST_PATTERN.matcher(request.getRequestLine());
    return m.matches();
  }

  @Override
  public Response dispatch(Request request) {
    String connection = request.getHeader("Connection");
    if (connection != null && connection.contains("Upgrade")) {
      return answerError(BLOCKED_STATUS, request, "header connection: " + connection);
    }
    if (!isImplemented(request)) {
      return answerError(BLOCKED_STATUS, request, "NOT IMPLEMENTED");
    }
    return tryUpstreamRequest(request);
  }

  /** May be overriden to implement a specialized exception handling. */
  protected Response tryUpstreamRequest(Request request) {
    try {
      return callUpstreamRequest(request);
    } catch (IOException e) {
      LOG.warning("failed request: " + request + " caused by: " + e);
      return answerUpstreamError(request, String.valueOf(e));
    }
  }

  /**
   * Handling of the upstream request, shouldn't be overridden. See {@link OkHttpClient.Builder} to
   * modify the client.
   */
  protected Response callUpstreamRequest(Request request) throws IOException {
    okhttp3.Request.Builder rb = new okhttp3.Request.Builder();
    rb.url(request.getRequestUrl());
    rb.headers(request.getHeaders());
    String method = request.getMethod();
    RequestBody requestBody;
    if (request.getBodySize() > 0 || HttpMethod.requiresRequestBody(method)) {
      String header = request.getHeader("Content-Type");
      MediaType type = header == null ? null : MediaType.parse(header);
      requestBody = RequestBody.create(type, request.getBody().readByteArray());
    } else {
      requestBody = null;
    }
    rb.method(method, requestBody);
    try (okhttp3.Response response = client.newCall(rb.build()).execute()) {
      Buffer body = new Buffer();
      body.writeAll(response.body().source());
      return new Response()
          .setResponseCode(response.code())
          .setHeaders(response.headers())
          .removeHeader("connection")
          .removeHeader("proxy-authenticate")
          .removeHeader("proxy-authorization")
          .removeHeader("te")
          .removeHeader("trailer")
          .removeHeader("upgrade")
          .removeHeader("transfer-encoding")
          .removeHeader("content-length")
          .setBody(body);
    }
  }

  private Response answer(String status, String message) {
    return new Response()
        .setStatus(status)
        .addHeader("Content-Type: text/plain; charset=utf-8")
        .setBody(message);
  }

  private Response answerError(String status, Request request, String reason) {
    return answer(
        status,
        "SERVER ERROR: "
            + getClass().getSimpleName()
            + " couldn't fulfil "
            + request
            + " caused by: "
            + reason);
  }

  protected Response answerUpstreamError(Request request, String reason) {
    return answerError(ERROR_STATUS, request, reason);
  }

  public static void main(String[] args) throws Exception {
    try {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

      SimpleProxy server = new SimpleProxy(port);
      server.run();

    } catch (Exception e) {
      System.out.printf("Usage: %s <port|%s>%n", SimpleProxy.class.getSimpleName(), DEFAULT_PORT);
      throw e;
    }
  }
}

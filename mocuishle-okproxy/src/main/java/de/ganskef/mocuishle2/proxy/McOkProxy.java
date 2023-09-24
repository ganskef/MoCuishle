package de.ganskef.mocuishle2.proxy;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.IActionExport;
import de.ganskef.mocuishle.IActionPath;
import de.ganskef.mocuishle.IActionRedirect;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICacheable;
import de.ganskef.mocuishle.ICacheableProxy;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.ui.Requested;
import de.ganskef.mocuishle.ui.SpecialUrl;
import de.ganskef.mocuishle2.proxy.internal.HttpDate;
import de.ganskef.okproxy.Impersonation;
import de.ganskef.okproxy.InterceptionProxy;
import de.ganskef.okproxy.Request;
import de.ganskef.okproxy.Response;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.Buffer;
import okio.Okio;
import okio.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McOkProxy extends InterceptionProxy {

  private static final Logger LOG = LoggerFactory.getLogger(McOkProxy.class);

  private static final String READY_MESSAGE = "Startup done";

  private final int MAX_MODIFY_LENGTH = 1024 * 1024;

  private static final Pattern STATE_PATTERN =
      Pattern.compile("HTTP/1\\.[01] (\\d{3}).*", Pattern.DOTALL);

  private static final Pattern REQUEST_PATTERN =
      Pattern.compile("[A-Z]{3,8} (https?://.{1,2048}|/.{0,2048}) HTTP/1\\.[01]");

  private static final Pattern FILE_TYPE_PATTERN = Pattern.compile(".*\\.(.*?)(:?[#?].*)?");

  private static final Pattern ROUTING_PATTERN = Pattern.compile("GET (/.{0,2048}) HTTP/1\\.[01]");

  private final ICache cache;
  private final Requested requested;

  public McOkProxy(IPlatform platform, ICache cache) {
    super(
        platform.getProxyPort(),
        new Impersonation.Builder().basedir(cache.getWritableDir().getPath()),
        new OkHttpClient.Builder()
            .addInterceptor(new BrotliInterceptor())
            .addInterceptor(
                new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE)));
    this.cache = cache;
    this.requested = new Requested();
  }

  public void start() throws IOException {
    super.run();
    LOG.info(READY_MESSAGE);
  }

  @Override
  protected boolean isImplemented(Request request) {
    Matcher m = REQUEST_PATTERN.matcher(request.getRequestLine());
    return m.matches();
  }

  private String contentType(String uri) {
    String type = "text/html; charset=utf-8";
    Matcher m = FILE_TYPE_PATTERN.matcher(uri);
    if (m.matches()) {
      String fileExtension = m.group(1);
      if (fileExtension.equalsIgnoreCase("css")) {
        type = "text/css";
      } else if (fileExtension.equalsIgnoreCase("png")) {
        type = "image/png";
      } else if (fileExtension.equalsIgnoreCase("ico")) {
        type = "image/x-icon";
      } else if (fileExtension.equalsIgnoreCase("woff2")) {
        type = "application/octet-stream";
      } else if (fileExtension.equalsIgnoreCase("woff")) {
        type = "application/octet-stream";
      } else if (fileExtension.equalsIgnoreCase("ttf")) {
        type = "application/octet-stream";
      } else if (fileExtension.equalsIgnoreCase("zip")) {
        type = "application/octet-stream";
      } else if (fileExtension.equalsIgnoreCase("js")) {
        type = "text/javascript";
      }
    }
    return type;
  }

  /** Create outgoing, handle headers and respond cached if needed. */
  // see McHttpFiltersSource#filterRequest
  // see McHttpFilters#clientToProxyRequest
  // see McHttpFilters#serverToProxyResponse
  //
  // DONE decode deflate!, gzip and br before writing in the cache
  // DONE SimpleProxy returns full Body instead of chunks
  // DONE modify html response
  // DONE store into cache
  // DONE handle block and pass
  // DONE handle special URIs of dynamic HTML (routing)
  // DONE handle fonts and markup as static server
  // DONE handle cache export as static server
  // TODO replace nio with okio to write file (or migrate to okhttp cache)
  // TODO introduce a builder (to set logger optionally in main)
  // TODO download huge files for export of archives asynchronous
  @Override
  public Response dispatch(Request request) {
    String requestLine = request.getRequestLine();
    Matcher m = ROUTING_PATTERN.matcher(requestLine);
    if (m.matches()) {
      String uri = m.group(1);

      IAction action = SpecialUrl.createAnswer(cache, requested, uri);
      String markup = action.prepareAnswer();
      if (action instanceof IActionRedirect) {
        return new Response().setResponseCode(302).setHeader("Location", markup);
      }
      if (action instanceof IActionExport) {
        Path path = Paths.get(markup);
        return answerPath(uri, action, path);
      }
      int status = action.status().code();
      if (status != 200) {
        LOG.info("{} {}", status, uri);
      }
      if (action instanceof IActionPath) {
        Path path = ((IActionPath) action).getPath();
        return answerPath(uri, action, path);
      }
      return new Response()
          .setResponseCode(action.status().code())
          .setBody(markup)
          .setHeader("Content-Type", contentType(uri));
    }
    HttpUrl url = request.getRequestUrl();
    String hostName = url.host();
    requested.add(hostName);
    if (cache.isBlocked(hostName)) {
      LOG.info("Blocked {}", url);
      return new Response().setResponseCode(501);
    }
    if (cache.isPassed(url.toString())) {
      LOG.info("Passed {}", url);
      return createForwarder(request);
    }

    String methodName = request.getMethod();
    String protocolVersion = requestLine.substring(requestLine.lastIndexOf(' ') + 1);
    Map<String, List<String>> multimap = request.getHeaders().toMultimap();
    boolean refresh = isRefresh(multimap);
    ICacheable element = cache.createElement(request.getRequestUrl().toString());
    if (element.isDocumentExtension()
        || (!element.isNocontentExtension() && isAcceptTextHtml(request))) {
      element.recordForDownload(methodName, protocolVersion, toHeaders(multimap));
    }
    if (element.needBadGateway()) {
      return createBadGatewayResponse(element);
    }
    // FIXME der Cache verhält sich irgendwie falsch herum: Bei F5 wird eine Antwort aus dem Cache
    // mit Modify erzwungen, statt einer Antwort ohne Modify aus dem Internet.
    if (element.needCached(refresh)) {
      Response response = createCachedResponse(element, false);
      if (!isContentTypeTextHtml(response.getHeaders().get("content-type"))) {
        element.deleteOutgoing();
      }
      return response;
    }
    Headers.Builder hb = request.getHeaders().newBuilder();
    if (refresh) {
      hb.removeAll("if-modified-since");
      hb.removeAll("if-none-match");
    } else {
      long lastModified = element.lastModified();
      if (lastModified != 0L) {
        hb.set("If-Modified-Since", new Date(lastModified));
      }
    }
    Request headersFixed = new Request(request, hb.build());
    Response response = createForwarder(headersFixed);
    if (element.isIgnored()) {
      LOG.info("IGNORED host null for {}", request);
      return response; // ignored
    }
    element.deleteOutgoing();
    int status = toStatusCode(response);
    if (status == 304) {
      return createCachedResponse(element, true);
    }
    if (isSpooledStatus(status)) {
      spool(element, response);
      if (isContentTypeTextHtml(response.getHeaders().get("Content-Type"))) {
        return modify(element, response);
      }
    } else {
      LOG.info("State {} for {}", status, request.getRequestUrl());
    }
    LOG.debug("origin headers {}", response.getHeaders());
    return response;
  }

  private Response answerPath(String uri, IAction action, Path path) {
    Path markupDir = cache.getWritableDir().toPath().resolve("markup");
    try (Source source = Okio.source(markupDir.resolve(path))) {
      Buffer body = new Buffer();
      body.writeAll(source);
      return new Response()
          .setResponseCode(action.status().code())
          .setHeader("Content-Type", contentType(uri))
          .setBody(body);
    } catch (IOException e) {
      LOG.error("Couldn't read file to response static content.", e);
      return new Response().setResponseCode(500);
    }
  }

  private Response createForwarder(Request request) {
    if (cache.isOffline()) {
      return new Response().setBody("Offline").setResponseCode(502); // BAD_GATEWAY
    }
    return super.dispatch(request);
  }

  private int toStatusCode(Response response) {
    Matcher m = STATE_PATTERN.matcher(response.getStatus());
    if (m.matches()) {
      return Integer.parseInt(m.group(1));
    }
    throw new IllegalArgumentException("Status: " + response);
  }

  private void spool(ICacheable element, Response response) {
    Buffer input = response.getBody();

    ByteBuffer spoolBuffer = ByteBuffer.allocate((int) input.size());
    try {
      while (!input.exhausted()) {
        input.read(spoolBuffer);
      }
      spoolBuffer.flip();

      Iterable<Map.Entry<String, String>> headers = toHeaders(response.getHeaders().toMultimap());
      String stateLine = response.getStatus() + "\r\n";
      Matcher m = STATE_PATTERN.matcher(stateLine);
      if (m.matches()) {
        element.store(Integer.parseInt(m.group(1)), stateLine, headers, spoolBuffer);
      } else {
        LOG.warn("Can't store response {} {} {}", response, input, spoolBuffer);
      }

    } catch (Exception e) {
      LOG.error("Can't store response " + response, e);
      element.addAlert(ICacheableProxy.Alerts.NOT_STORED);
    }
  }

  private boolean isSpooledStatus(int status) {
    return status == 200 // OK
        || status == 301 // MOVED_PERMANENTLY
        || status == 302; // FOUND
  }

  private Response modify(ICacheable element, Response response) {
    Buffer input = response.getBody();
    if (input.size() > MAX_MODIFY_LENGTH) {
      LOG.info("modify skipped {}bytes > {}", input.size(), MAX_MODIFY_LENGTH);
      return response;
    }

    ByteBuffer buffer = ByteBuffer.allocate((int) input.size());
    try {
      while (!input.exhausted()) {
        input.read(buffer);
      }
      buffer.flip();
    } catch (IOException e) {
      return response;
    }

    ByteBuffer modified = element.getModifiedTextHtml(buffer, true);
    Buffer body = new Buffer();
    try {
      body.write(modified);
    } catch (IOException e) {
      return response;
    }
    LOG.debug(
        "MODIFY {} buffer={} modified={} body={}",
        input,
        buffer,
        modified,
        body // .clone().readString(StandardCharsets.ISO_8859_1)
        );
    response.setBody(body);
    return response;
  }

  private Response createCachedResponse(ICacheable element, boolean online) {
    Response response = new Response();
    ICacheableProxy.CachedResponse cachedResponse = element.readCachedResponseToView();
    Headers.Builder hb = new Headers.Builder();
    for (Map.Entry<String, String> each : cachedResponse.getHeaders()) {
      hb.add(each.getKey(), each.getValue());
    }
    updateHeaderDates(element, hb);
    response.setHeaders(hb.build());
    response.setResponseCode(cachedResponse.getStatusCode());
    ByteBuffer buffer = cachedResponse.getContent();
    if (isContentTypeTextHtml(response.getHeaders().get("Content-Type"))) {
      buffer = element.getModifiedTextHtml(buffer, online);
    }
    Buffer body = new Buffer();
    try {
      body.write(buffer);
    } catch (IOException e) {
      return createBadGatewayResponse(element);
    }
    response.setBody(body);
    return response;
  }

  private void updateHeaderDates(ICacheable element, Headers.Builder builder) {
    long loadDistance = System.currentTimeMillis() - element.lastModified();
    for (String each : new String[] {"Date", "Expires", "Last-Modified"}) {
      String httpDate = builder.get(each);
      if (httpDate != null) {
        Date date = null;
        try {
          date = HttpDate.parse(httpDate);
        } catch (Exception e) {
          // ignore parse exception
        }
        if (date != null) {
          Date updated = new Date(date.getTime() + loadDistance);
          builder.set(each, updated);
        }
      }
    }
  }

  private Response createBadGatewayResponse(ICacheable element) {
    Response result = new Response();
    result.setResponseCode(302); // FOUND
    result.addHeader("Location", element.getProxyHome() + "/browse-outgoing");
    result.addHeader("Connection", "close");
    return result;
  }

  private boolean isAcceptTextHtml(Request request) {
    return isContentTypeTextHtml(request.getHeader("accept"));
  }

  private boolean isContentTypeTextHtml(String contentType) {
    return contentType != null
        && (contentType.contains("text/html") || contentType.contains("application/xhtml"));
  }

  private Iterable<Map.Entry<String, String>> toHeaders(Map<String, List<String>> multimap) {
    Map<String, String> results = new HashMap<>();
    for (String each : multimap.keySet()) {
      results.put(each, multimap.get(each).get(0));
    }
    return results.entrySet();
  }

  private boolean isRefresh(Map<String, List<String>> multimap) {
    if (multimap.containsKey("cache-control")) {
      for (String each : multimap.get("cache-control")) {
        if ("no-cache".equals(each)) {
          LOG.info("Header cache control is no-cache, refresh is requested.");
          return true;
        }
        if ("max-age=0".equals(each)) {
          LOG.info("Header cache control is max-age=0, refresh is requested.");
          return true;
        }
      }
    }
    if (multimap.containsKey("pragma")) {
      for (String each : multimap.get("pragma")) {
        if ("no-cache".equals(each)) {
          LOG.info("Header pragma is no-cache, refresh is requested.");
          return true;
        }
      }
    }
    LOG.debug("No header found to force a refresh.");
    return false;
  }

  public void stop() {
    shutdown();
  }
}

package de.ganskef.okproxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okio.Buffer;
import okio.Okio;

/**
 * Base implementation of an HTTP server. See {@link OkHttpClient} for interceptors, events, caching
 * and other upstream features.
 */
public class SimpleServer extends Dispatcher {

  private static final int DEFAULT_PORT = 9090;

  private static OkHttpServer server;

  protected final String rootArgument;

  protected final int portArgument;

  public SimpleServer(String root, int port) {
    this.rootArgument = root;
    this.portArgument = port;
  }

  public void run() throws IOException {
    server = new OkHttpServer();
    server.setDispatcher(this);
    configure(server);
    server.start(portArgument);
  }

  public HttpUrl url(String path) {
    Objects.requireNonNull(server, "server");
    return server.url(path);
  }

  protected void configure(OkHttpServer server) {}

  @Override
  public Response dispatch(Request request) {
    String path = request.getPath();
    try {
      if (!path.startsWith("/") || path.contains("..")) throw new FileNotFoundException();

      File file = new File(rootArgument + path);
      return file.isDirectory() ? directoryToResponse(path, file) : fileToResponse(path, file);
    } catch (FileNotFoundException e) {
      return new Response()
          .setStatus("HTTP/1.1 404")
          .addHeader("content-type: text/plain; charset=utf-8")
          .setBody("NOT FOUND: " + path);
    } catch (IOException e) {
      return new Response()
          .setStatus("HTTP/1.1 500")
          .addHeader("content-type: text/plain; charset=utf-8")
          .setBody("SERVER ERROR: " + e);
    }
  }

  private Response directoryToResponse(String basePath, File directory) {
    if (!basePath.endsWith("/")) basePath += "/";

    StringBuilder response = new StringBuilder();
    response.append(String.format("<html><head><title>%s</title></head><body>", basePath));
    response.append(String.format("<h1>%s</h1>", basePath));
    for (String file : Objects.requireNonNull(directory.list())) {
      response.append(
          String.format("<div class='file'><a href='%s'>%s</a></div>", basePath + file, file));
    }
    response.append("</body></html>");

    return new Response()
        .setStatus("HTTP/1.1 200")
        .addHeader("content-type: text/html; charset=utf-8")
        .setBody(response.toString());
  }

  private Response fileToResponse(String path, File file) throws IOException {
    return new Response()
        .setStatus("HTTP/1.1 200")
        .setBody(fileToBytes(file))
        .addHeader("content-type: " + contentType(path));
  }

  private Buffer fileToBytes(File file) throws IOException {
    Buffer result = new Buffer();
    result.writeAll(Okio.source(file));
    return result;
  }

  private String contentType(String path) {
    if (path.endsWith(".png")) return "image/png";
    if (path.endsWith(".jpg")) return "image/jpeg";
    if (path.endsWith(".jpeg")) return "image/jpeg";
    if (path.endsWith(".gif")) return "image/gif";
    if (path.endsWith(".html")) return "text/html; charset=utf-8";
    if (path.endsWith(".txt")) return "text/plain; charset=utf-8";
    return "application/octet-stream";
  }

  public static void main(String[] args) throws Exception {
    try {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
      String root = args.length > 1 ? args[1] : "target";

      SimpleServer server = new SimpleServer(root, port);
      server.run();

    } catch (Exception e) {
      System.out.printf(
          "Usage: %s <port|%s> <root directory|.>%n",
          SimpleServer.class.getSimpleName(), DEFAULT_PORT);
      throw e;
    }
  }
}

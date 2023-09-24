package de.ganskef.okproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.OkHttpClient;

/**
 * Base implementation of a Man In The Middle proxy server to intercept HTTPS connections. See
 * {@link OkHttpClient} for interceptors, events, caching and other upstream features.
 */
public class InterceptionProxy extends SimpleProxy {

  private static final Logger LOG = Logger.getLogger(InterceptionProxy.class.toString());

  private static final int DEFAULT_PORT = 9090;

  private static final Pattern REQUEST_PATTERN =
      Pattern.compile("[A-Z]{3,8} (https?://.{1,2048}) HTTP/1\\.[01]");

  private static final int MAX_TRY_COUNT = 2;

  private final Impersonation impersonator;

  private final LinkedHashMap<String, SSLContext> hosts;

  private final ReentrantLock lock = new ReentrantLock();

  public InterceptionProxy() {
    this(DEFAULT_PORT, new Impersonation.Builder(), new OkHttpClient.Builder());
  }

  public InterceptionProxy(
      int port, Impersonation.Builder impersonationBuilder, OkHttpClient.Builder clientBuilder) {
    super(port, clientBuilder);
    this.impersonator = impersonationBuilder.build();
    this.hosts = new LinkedHashMap<>(0, 0.75f, true);
  }

  @Override
  public Socket connect(Socket socket, String host, int port) throws IOException {
    SSLContext sslContext;
    if (hosts.containsKey(host)) {
      sslContext = hosts.get(host);
    } else {
      lock.lock();
      try {
        sslContext = impersonator.createSSLContext(host);
        hosts.put(host, sslContext);
      } finally {
        lock.unlock();
      }
    }
    SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
    sslSocket.setUseClientMode(false);
    sslSocket.startHandshake();
    return sslSocket;
  }

  protected boolean isImplemented(Request request) {
    Matcher m = REQUEST_PATTERN.matcher(request.getRequestLine());
    return m.matches();
  }

  /**
   * Workaround to handle race conditions on macOS (openjdk version "11.0.10" 2021-01-19), for
   * example:
   *
   * <ul>
   *   <li>javax.net.ssl.SSLPeerUnverifiedException: Hostname www.ndr.de not verified (no
   *       certificates)
   *   <li>java.net.ConnectException: Failed to connect to
   *       www.apple.com/2a02:26f0:d5:493:0:0:0:1aca:443
   * </ul>
   *
   * <p>The second try always succeed, never seen a second fail. Linux works well, Windows is not
   * tested yet.
   */
  @Override
  protected Response tryUpstreamRequest(Request request) {
    List<Throwable> throwables = new LinkedList<>();
    while (true) {
      try {
        return callUpstreamRequest(request);
      } catch (IOException e) {
        LOG.warning("retry request: " + request + " caused by: " + e);
        throwables.add(e);
        if (throwables.size() >= MAX_TRY_COUNT) {
          return answerUpstreamError(request, throwables.toString());
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    try {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

      InterceptionProxy server =
          new InterceptionProxy(port, new Impersonation.Builder(), new OkHttpClient.Builder());
      server.run();

    } catch (Exception e) {
      System.out.printf(
          "Usage: %s <port|%s>%n", InterceptionProxy.class.getSimpleName(), DEFAULT_PORT);
      throw e;
    }
  }
}

package de.ganskef.mocuishle;

import de.ganskef.okproxy.Impersonation;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Address implements Closeable {

  private static final Logger LOG = Logger.getLogger(Address.class.toString());

  private final String name;

  private final OkHttpClient client;

  private final String basedir;

  private final Impersonation impersonator;

  private SSLContext sslContext;

  public Address(
      OkHttpClient.Builder clientBuilder, String name, String basedir, Impersonation impersonator) {
    Objects.requireNonNull(clientBuilder, "Given clientBuilder must not be null.");
    Objects.requireNonNull(name, "Given name must not be null.");
    Objects.requireNonNull(basedir, "Given basedir must not be null.");
    Cache cache = openCache(name);
    this.client = clientBuilder.cache(cache).build();
    this.name = name;
    this.basedir = basedir;
    this.impersonator = impersonator;
  }

  private Cache openCache(String name) {
    File directory = new File(basedir, "cache/" + name);
    directory.mkdirs();
    return new Cache(directory, Long.MAX_VALUE);
  }

  /**
   * Connect a SSLSocket.
   *
   * <p>Throws a NullPointerException if HTTPS interception is disabled.
   */
  public SSLSocket impersonate(Socket fromSocket, int securedPort) throws IOException {
    Objects.requireNonNull(fromSocket);
    if (sslContext == null) {
      sslContext = impersonator.createSSLContext(name);
    }
    SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    SSLSocket sslSocket =
        (SSLSocket) sslSocketFactory.createSocket(fromSocket, name, securedPort, true);
    sslSocket.setUseClientMode(false);
    return sslSocket;
  }

  public Response getResponse(Request request) throws IOException {
    Objects.requireNonNull(request);
    return client.newCall(request).execute();
  }

  @Override
  public void close() throws IOException {
    long nano = System.nanoTime();
    Cache cache = client.cache();
    if (cache != null) {
      cache.close();
    }
    LOG.fine(() -> String.format("Close: %sms %s", System.nanoTime() - nano / 1000000, cache));
  }
}

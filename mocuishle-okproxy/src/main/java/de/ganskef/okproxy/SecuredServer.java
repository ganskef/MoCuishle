package de.ganskef.okproxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import okhttp3.OkHttpClient;

/**
 * Base implementation of a HTTP server using TLS. See {@link OkHttpClient} for interceptors,
 * events, caching and other upstream features.
 */
public class SecuredServer extends SimpleServer {

  private static final int DEFAULT_PORT = 9090;

  private final SSLContext sslContext;

  public SecuredServer(SSLContext sslContext, String root, int port) {
    super(root, port);
    this.sslContext = sslContext;
  }

  @Override
  protected void configure(OkHttpServer server) {
    server.useHttps(sslContext.getSocketFactory());
  }

  public static void main(String[] args) throws Exception {
    try {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
      String root = args.length > 1 ? args[1] : "target";
      SSLContext sslContext;
      if (args.length > 3) {
        String keystoreFile = args[2];
        String password = args[3];
        sslContext = sslContext(keystoreFile, password);
      } else {
        Impersonation impersonator = new Impersonation.Builder().build();
        sslContext = impersonator.createSSLContext("localhost");
      }

      SecuredServer server = new SecuredServer(sslContext, root, port);
      server.run();
    } catch (Exception e) {
      System.out.printf(
          "Usage: %s <port|%s> <root directory|target> <keystore> <password>%n",
          SecuredServer.class.getSimpleName(), DEFAULT_PORT);
      throw e;
    }
  }

  private static SSLContext sslContext(String keystoreFile, String password)
      throws GeneralSecurityException, IOException {
    KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
    try (InputStream in = Files.newInputStream(Paths.get(keystoreFile))) {
      keystore.load(in, password.toCharArray());
    }
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keystore, password.toCharArray());

    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keystore);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
        keyManagerFactory.getKeyManagers(),
        trustManagerFactory.getTrustManagers(),
        new SecureRandom());

    return sslContext;
  }
}

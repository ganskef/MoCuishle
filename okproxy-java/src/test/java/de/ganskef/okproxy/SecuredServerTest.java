package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;
import okio.BufferedSink;
import okio.Okio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecuredServerTest {

  private SecuredServer server;

  private OkHttpClient client;

  @AfterEach
  public void tearDown() throws IOException {
    server.shutdown();
  }

  @BeforeEach
  public void setUp() throws Exception {

    // The server needs a certificate to serve secured content.
    Impersonation impersonator = new Impersonation.Builder().basedir("target").build();
    SSLContext sslContext = impersonator.createSSLContext("localhost");
    server = new SecuredServer(sslContext, "target", 0);
    server.run();

    // The client needs the certificate of the server.
    HandshakeCertificates hc =
        new HandshakeCertificates.Builder()
            .addTrustedCertificate(impersonator.intermediateCertificate())
            .build();
    client =
        new OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .sslSocketFactory(hc.sslSocketFactory(), hc.trustManager())
            .build();
    // Java 9: Files.writeString(Path.of("target/hello-world.txt"), "hello world",
    // StandardOpenOption.CREATE);
    try (BufferedSink sink = Okio.buffer(Okio.sink(Paths.get("target/hello-world.txt")))) {
      sink.writeUtf8("hello world");
    }
  }

  @Test
  void testSimpleRequest() throws Exception {
    Response response =
        client.newCall(new Request.Builder().url(server.url("/hello-world.txt")).build()).execute();
    assertThat(response.body().string()).describedAs("response body").isEqualTo("hello world");
    assertThat(response.code()).describedAs("response code").isEqualTo(HttpURLConnection.HTTP_OK);
  }
}

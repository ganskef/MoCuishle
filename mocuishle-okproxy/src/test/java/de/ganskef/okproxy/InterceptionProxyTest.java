package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterceptionProxyTest {

  private static String OS = System.getProperty("os.name").toLowerCase();

  private static final String BASE_DIR = "target";

  private MockWebServer server;
  private OkHttpClient directClient;
  private OkHttpClient proxyClient;

  private InterceptionProxy proxy;

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @BeforeEach
  void setUp() throws Exception {

    // The MockWebServer needs a certificate to serve secured content.
    Impersonation direct =
        new Impersonation.Builder().basedir(BASE_DIR).alias("MockWebServer").build();
    server = new MockWebServer();
    String securedAddress = OS.contains("win") ? "127.0.0.1" : "localhost";
    server.useHttps(direct.createSSLContext(securedAddress).getSocketFactory(), false);
    server.start();

    // The client needs the certificate of the MockWebServer.
    HandshakeCertificates hcDirect =
        new HandshakeCertificates.Builder()
            .addTrustedCertificate(direct.intermediateCertificate())
            .build();
    OkHttpClient.Builder testClientBuilder =
        new OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .sslSocketFactory(hcDirect.sslSocketFactory(), hcDirect.trustManager());
    directClient = testClientBuilder.build();

    // This creates okproxy.p12 and okproxy.pem in target
    Impersonation.Builder testImpersonationBuilder = new Impersonation.Builder().basedir(BASE_DIR);
    proxy = new InterceptionProxy(0, testImpersonationBuilder, testClientBuilder);
    proxy.run();

    // Browsers have to import the generated PEM file of the proxy server.
    X509Certificate trusted;
    try (InputStream inStream = new FileInputStream("target/okproxy.pem")) {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      trusted = (X509Certificate) cf.generateCertificate(inStream);
    }
    HandshakeCertificates hcTrusted =
        new HandshakeCertificates.Builder().addTrustedCertificate(trusted).build();
    proxyClient =
        directClient
            .newBuilder()
            .proxy(proxy.toProxyAddress())
            .sslSocketFactory(hcTrusted.sslSocketFactory(), hcTrusted.trustManager())
            .build();
  }

  @Test
  void testSetup() throws Exception {
    server.enqueue(new MockResponse().setBody("hello world"));

    Response response =
        directClient
            .newCall(
                new Request.Builder()
                    .url(server.url("/"))
                    .addHeader("Accept-Language", "en-US")
                    .build())
            .execute();
    assertThat(response.body().string()).describedAs("response body").isEqualTo("hello world");
    assertThat(response.code()).describedAs("response code").isEqualTo(HttpURLConnection.HTTP_OK);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestLine()).describedAs("request line").isEqualTo("GET / HTTP/1.1");
    assertThat(request.getHeader("Accept-Language"))
        .describedAs("Request header")
        .isEqualTo("en-US");

    assertThat(proxyClient.proxy()).describedAs("client via proxy").isNotNull();
  }

  @Test
  void testViaProxyClient() throws IOException, InterruptedException {
    server.enqueue(new MockResponse().setBody("hello world"));

    Response response =
        proxyClient
            .newCall(
                new Request.Builder()
                    .url(server.url("/"))
                    .addHeader("Accept-Language", "en-US")
                    .build())
            .execute();
    assertThat(response.body().string()).describedAs("response body").isEqualTo("hello world");
    assertThat(response.code()).describedAs("Response code").isEqualTo(HttpURLConnection.HTTP_OK);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestLine()).describedAs("request line").isEqualTo("GET / HTTP/1.1");
    assertThat(request.getHeader("Accept-Language"))
        .describedAs("request header")
        .isEqualTo("en-US");
  }
}

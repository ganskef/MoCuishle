package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleProxyTest {

  private MockWebServer server;
  private SimpleProxy proxy;
  private OkHttpClient proxyClient;

  @AfterEach
  public void tearDown() throws IOException {
    server.shutdown();
  }

  @BeforeEach
  public void setUp() throws IOException {
    proxy = new SimpleProxy(0);
    server = new MockWebServer();
    server.start();
    proxy.run();
    proxyClient =
        new OkHttpClient()
            .newBuilder() //
            .proxy(proxy.toProxyAddress()) //
            .callTimeout(0, TimeUnit.SECONDS) //
            .readTimeout(0, TimeUnit.SECONDS) //
            .build();
  }

  @Test
  void testSetup() throws Exception {
    server.enqueue(new MockResponse().setBody("hello world"));

    OkHttpClient directClient = new OkHttpClient();
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
        .describedAs("request header")
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
    assertThat(response.code()).describedAs("response code").isEqualTo(HttpURLConnection.HTTP_OK);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestLine()).describedAs("request line").isEqualTo("GET / HTTP/1.1");
    assertThat(request.getHeader("Accept-Language"))
        .describedAs("request header")
        .isEqualTo("en-US");
  }

  @Test
  void testServerRequestReturns501() throws IOException, InterruptedException {
    Response response =
        proxyClient
            .newCall(
                new Request.Builder()
                    .url(
                        "http://127.0.0.1:"
                            + ((InetSocketAddress) proxy.toProxyAddress().address()).getPort())
                    .build())
            .execute();
    assertThat(response.code())
        .describedAs("response code")
        .isEqualTo(HttpURLConnection.HTTP_NOT_IMPLEMENTED);
  }

  @Test
  void testMain() throws Exception {
    Set<Thread> currentThreads = Thread.getAllStackTraces().keySet();
    SimpleProxy.main(new String[] {"0"});
    Set<Thread> createdThreads = Thread.getAllStackTraces().keySet();
    createdThreads.removeAll(currentThreads);

    assertThat(createdThreads.size()).isEqualTo(1);
    assertThat(createdThreads.iterator().next().getName()).startsWith("Listening: ");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              SimpleProxy.main(new String[] {"-1"});
            });
  }
}

package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleServerTest {

  private SimpleServer server;
  private OkHttpClient client;

  @AfterEach
  public void tearDown() throws IOException {
    server.shutdown();
  }

  @BeforeEach
  public void setUp() throws IOException {
    server = new SimpleServer("target", 0);
    server.run();
    client =
        new OkHttpClient()
            .newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build();
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

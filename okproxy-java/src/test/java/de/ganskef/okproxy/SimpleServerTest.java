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
            .callTimeout(0, TimeUnit.SECONDS) // <- for debug
            .readTimeout(0, TimeUnit.SECONDS) // <- for debug
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

  @Test
  void testDirectoryRequest() throws Exception {
    Response response =
        client.newCall(new Request.Builder().url(server.url("/")).build()).execute();
    assertThat(response.body().string()).as("response body").contains(">hello-world.txt</a>");
    // TODO type is text/html
    assertThat(response.code()).describedAs("response code").isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void testNotFound() throws Exception {
    Response response =
        client.newCall(new Request.Builder().url(server.url("/not-found")).build()).execute();
    // TODO type is text/plain (-> no mask of XML characters needed)
    assertThat(response.code()).as("response code 404").isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void testSecurityPathBasedAuthorization() throws Exception {
    Response response =
        client
            .newCall(new Request.Builder().url(server.url("/./hello-world.txt")).build())
            .execute();
    // TODO assertThat(response.code()).as("disallow different paths for the same
    // resource").isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void testSecurityPathTraversal() throws Exception {
    Response fileResponse =
        client
            .newCall(new Request.Builder().url(server.url("/../target/hello-world.txt")).build())
            .execute();
    // TODO assertThat(fileResponse.code())
    //     .as("allow files in web root only")
    //     .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
    Response dirResponse =
        client.newCall(new Request.Builder().url(server.url("/../")).build()).execute();
    // TODO assertThat(dirResponse.code())
    //     .as("must deny upper directory")
    //     .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void testMainExceptions() throws Exception {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .as("alphanumeric port parameter")
        .isThrownBy(
            () -> {
              SimpleServer.main(new String[] {"a"});
            });
    assertThatExceptionOfType(IllegalArgumentException.class)
        .as("negative port number")
        .isThrownBy(
            () -> {
              SimpleServer.main(new String[] {"-1"});
            });
    assertThatExceptionOfType(IOException.class)
        .as("protected port number")
        .isThrownBy(
            () -> {
              SimpleServer.main(new String[] {"80"});
            });
  }
}

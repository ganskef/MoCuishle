package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OkHttpServerTest {

  @Test
  void testThreadNameContainsPortInsteadOfZero() throws Exception {
    OkHttpServer server = new OkHttpServer(OkHttpServerTest.class.getSimpleName());
    String threadName = "THREAD NAME NOT FOUND";
    try {
      server.start(0);
      final String expected = String.format("Listening: %s", server.getPort());
      for (Thread each : Thread.getAllStackTraces().keySet()) {
        if (each.getName().equals(expected)) {
          threadName = each.getName();
        }
      }
      assertThat(expected).isEqualTo(threadName);
    } finally {
      server.shutdown();
    }
    assertThat(String.format("%s[%s]", OkHttpServerTest.class.getSimpleName(), server.getPort()))
        .isEqualTo(server.toString());
  }
}

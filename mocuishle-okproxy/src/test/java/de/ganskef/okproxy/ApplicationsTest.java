package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApplicationsTest {

  private static final String CLASSPATH = System.getProperty("java.class.path");

  private static final String BASE_DIR = "target";

  Process process;

  @AfterEach
  void after() {
    if (process != null) {
      process.destroy();
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  @Test
  void testSimpleServer() throws Exception {
    process =
        new ProcessBuilder(
                "java",
                "-cp",
                CLASSPATH,
                "de.ganskef.okproxy.SimpleServer",
                "0", // port number, 0 means try to find an unused port
                ".") // web directory, default is target
            .directory(new File(BASE_DIR))
            .start();

    assertThat(process.isAlive()).as("Server is running").isTrue();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      assertThat(br.readLine())
          .as("syserr with java util logging line 1")
          .contains("de.ganskef.okproxy.OkHttpServer");
      assertThat(br.readLine())
          .as("syserr with java util logging line 2")
          .endsWith(": Starting to accept connections");
    }
  }

  @Test
  void testSecuredServer() throws Exception {
    new Impersonation.Builder().basedir(BASE_DIR).alias("testSecuredServer").build();
    process =
        new ProcessBuilder(
                "java",
                "-cp",
                CLASSPATH,
                "de.ganskef.okproxy.SecuredServer",
                "0", // port number, 0 means try to find an unused port
                ".", // web directory, default is target
                "testSecuredServer.p12", // key store file name
                "") // key store password
            .directory(new File(BASE_DIR))
            .start();

    assertThat(process.isAlive()).as("Server is running").isTrue();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      assertThat(br.readLine())
          .as("syserr with java util logging line 1")
          .contains("de.ganskef.okproxy.OkHttpServer");
      assertThat(br.readLine())
          .as("syserr with java util logging line 2")
          .endsWith(": Starting to accept connections");
    }
  }

  @Test
  void testSimpleProxy() throws Exception {
    process =
        new ProcessBuilder(
                "java",
                "-cp",
                CLASSPATH,
                "de.ganskef.okproxy.SimpleProxy",
                "0") // port number, 0 means try to find an unused port
            .directory(new File(BASE_DIR))
            .start();

    assertThat(process.isAlive()).as("Server is running").isTrue();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      assertThat(br.readLine())
          .as("syserr with java util logging line 1")
          .contains("de.ganskef.okproxy.OkHttpServer");
      assertThat(br.readLine())
          .as("syserr with java util logging line 2")
          .endsWith(": Starting to accept connections");
    }
  }

  @Test
  void testInterceptionProxy() throws Exception {
    new Impersonation.Builder().basedir(BASE_DIR).build();
    process =
        new ProcessBuilder(
                "java",
                "-cp",
                CLASSPATH,
                "de.ganskef.okproxy.InterceptionProxy",
                "0") // port number, 0 means try to find an unused port
            .directory(new File(BASE_DIR))
            .start();

    assertThat(process.isAlive()).as("Server is running").isTrue();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      assertThat(br.readLine())
          .as("syserr with java util logging line 1")
          .endsWith(" de.ganskef.okproxy.Impersonation <init>");
      assertThat(br.readLine())
          .as("syserr with java util logging line 2")
          .contains(" _OkProxy (Man In The Middle) from ");
      assertThat(br.readLine())
          .as("syserr with java util logging line 3")
          .contains("de.ganskef.okproxy.OkHttpServer");
      assertThat(br.readLine())
          .as("syserr with java util logging line 4")
          .endsWith(": Starting to accept connections");
    }
  }
}

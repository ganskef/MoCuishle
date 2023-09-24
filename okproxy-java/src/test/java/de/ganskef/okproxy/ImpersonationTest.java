package de.ganskef.okproxy;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ImpersonationTest {

  private static final String BASE_DIR = "target";
  private static final String BASE_NAME = Impersonation.class.getSimpleName();

  @BeforeAll
  static void setUpBeforeClass() throws Exception {
    new Impersonation.Builder().basedir(BASE_DIR).build();
  }

  @Test
  void testInitWithNullMissused() throws Exception {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().name(null);
            })
        .withMessageContaining("name");
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().basedir(null);
            })
        .withMessageContaining("basedir");
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().alias(null);
            })
        .withMessageContaining("alias");
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().password(null);
            })
        .withMessageContaining("password");
  }

  @Test
  void testInitWithMissingNameMissconfigured() throws Exception {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().name("");
            });
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().name(" ");
            });
  }

  @Test
  void testInitWithMissingDirectoryMissconfigured() throws Exception {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().basedir("MISSING_DIRECTORY");
            });
  }

  @Test
  void testInitWithMissingAliasMissconfigured() throws Exception {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().alias("");
            });
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().alias(" ");
            });
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().alias("/");
            });
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().alias(".");
            });
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder().alias("-");
            });
  }

  @Test
  void testWithExistingKeystore() throws Exception {
    final Impersonation impersonator = new Impersonation.Builder().basedir(BASE_DIR).build();
    assertThat(impersonator.intermediateCertificate()).isNotNull();
  }

  @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
  @Test
  void testInitCertificateAgency() throws Exception {
    final File dir = new File(BASE_DIR);
    dir.mkdirs();
    final File tempFile = File.createTempFile(BASE_NAME, "", dir);
    final String id = tempFile.getName();
    Impersonation initialized =
        new Impersonation.Builder()
            .name(id)
            .basedir(BASE_DIR)
            .alias(id)
            .password(id.toCharArray())
            .build();
    assertThat(initialized.intermediateCertificate()).isNotNull();
    assertThat(new File(tempFile.getPath() + ".pem")).exists();
    assertThat(new File(tempFile.getPath() + ".p12")).exists();
    Impersonation loaded =
        new Impersonation.Builder()
            .name(id)
            .basedir(BASE_DIR)
            .alias(id)
            .password(id.toCharArray())
            .build();
    assertThat(initialized.intermediateCertificate()).isEqualTo(loaded.intermediateCertificate());
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () -> {
              new Impersonation.Builder()
                  .name(id)
                  .basedir(BASE_DIR)
                  .alias(id)
                  .password("wrong password".toCharArray())
                  .build();
            });
  }

  @Test
  void testServerCertificate() throws Exception {
    final Impersonation impersonator = new Impersonation.Builder().basedir(BASE_DIR).build();
    assertThat(impersonator.createSSLContext("localhost")).isNotNull();
  }
}

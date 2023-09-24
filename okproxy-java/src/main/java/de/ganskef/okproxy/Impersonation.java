package de.ganskef.okproxy;

import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

public class Impersonation {

  private static final Logger logger = Logger.getLogger(Impersonation.class.getName());

  private static final long ROOT_VALIDITY_DAYS = 100 * 365;

  private static final long SERVER_VALIDITY_DAYS = 365;

  private final HeldCertificate intermediate;

  private Impersonation(String name, String basedir, String alias, char[] password)
      throws GeneralSecurityException, IOException {
    File keystoreFile = new File(basedir + "/" + alias + ".p12").getCanonicalFile();
    if (keystoreFile.exists()) {
      KeyStore loaded = KeyStore.getInstance("PKCS12");
      try (InputStream input = Files.newInputStream(keystoreFile.toPath())) {
        loaded.load(input, password);
      }
      X509Certificate cert = (X509Certificate) loaded.getCertificate(alias);
      PrivateKey privateKey = (PrivateKey) loaded.getKey(alias, password);
      PublicKey publicKey = cert.getPublicKey();
      KeyPair keyPair = new KeyPair(publicKey, privateKey);
      this.intermediate = new HeldCertificate(keyPair, cert);
      logger.info(() -> String.format(Locale.US, "Load: %s from %s", name, keystoreFile));
    } else {
      long ms = System.currentTimeMillis();
      HeldCertificate root =
          new HeldCertificate.Builder()
              .certificateAuthority(1)
              .duration(ROOT_VALIDITY_DAYS, TimeUnit.DAYS)
              .commonName(name)
              .organizationalUnit(name + " Root CA")
              .build();
      this.intermediate =
          new HeldCertificate.Builder()
              .certificateAuthority(0)
              .duration(ROOT_VALIDITY_DAYS, TimeUnit.DAYS)
              .commonName(name)
              .organizationalUnit(name + " CA")
              .signedBy(root)
              .build();

      KeyStore keystore = KeyStore.getInstance("PKCS12");
      keystore.load(null, null);
      Certificate[] chain = {intermediate.certificate()};
      keystore.setKeyEntry(alias, intermediate.keyPair().getPrivate(), password, chain);
      try (OutputStream outputStream = Files.newOutputStream(keystoreFile.toPath())) {
        keystore.store(outputStream, password);
      }

      try (Writer writer = new FileWriter(new File(basedir, alias + ".pem"))) {
        writer.write(intermediate.certificatePem());
      }

      logger.info(
          () ->
              String.format(
                  Locale.US,
                  "Init: %sms %s into %s and %s.pem",
                  System.currentTimeMillis() - ms,
                  name,
                  keystoreFile,
                  alias));
    }
  }

  public SSLContext createSSLContext(String securedAddress) {
    Objects.requireNonNull(securedAddress);
    long ms = System.currentTimeMillis();
    HeldCertificate server =
        new HeldCertificate.Builder()
            .commonName(securedAddress)
            .addSubjectAlternativeName(securedAddress)
            .duration(SERVER_VALIDITY_DAYS, TimeUnit.DAYS)
            .signedBy(intermediate)
            .serialNumber(initRandomSerial())
            .build();

    HandshakeCertificates hc =
        new HandshakeCertificates.Builder()
            .heldCertificate(server, intermediate.certificate())
            .build();
    SSLContext sslContext = hc.sslContext();
    logger.fine(
        () ->
            String.format(
                "Sign: %sms %s #%s",
                System.currentTimeMillis() - ms,
                securedAddress,
                server.certificate().getSerialNumber()));
    return sslContext;
  }

  private long initRandomSerial() {
    Random rnd = new Random();
    rnd.setSeed(System.currentTimeMillis());
    // prevent browser certificate caches, cause of doubled serial numbers
    // using 48bit random number
    long sl = ((long) rnd.nextInt()) << 32 | (rnd.nextInt() & 0xFFFFFFFFL);
    // let reserve of 16 bit for increasing, serials have to be positive
    sl = sl & 0x0000FFFFFFFFFFFFL;
    return sl;
  }

  /** Certificate to sign server certificates while impersonation. */
  public X509Certificate intermediateCertificate() {
    return intermediate.certificate();
  }

  public static class Builder {
    private String alias = "okproxy";
    private String basedir = System.getProperty("user.dir");
    private String name = "_OkProxy (offline cache)";
    private char[] password = "".toCharArray();

    /**
     * Formal name to use in the certificates. Default starts with an underscore to order at the
     * top.
     */
    public Builder name(String name) {
      Objects.requireNonNull(name, "Given name must not be null.");
      if (name.trim().length() == 0) {
        throw new IllegalArgumentException("Given name must not be empty: '" + name + "'");
      }
      this.name = name;
      return this;
    }

    /** Technical name used in threads, certificate alias and files. */
    public Builder alias(String alias) {
      Objects.requireNonNull(alias, "Given alias must not be null.");
      if (!Pattern.matches("\\w+", alias)) {
        throw new IllegalArgumentException("Given alias must be an identifier: '" + alias + "'");
      }
      this.alias = alias;
      return this;
    }

    public Builder basedir(String basedir) {
      Objects.requireNonNull(basedir, "Given basedir must not be null.");
      File dir = new File(basedir);
      if (!dir.isDirectory()) {
        throw new IllegalArgumentException("Given basedir must be a directory: '" + basedir + "'");
      }
      this.basedir = basedir;
      return this;
    }

    public Builder password(char[] password) {
      Objects.requireNonNull(password, "Given password must not be null.");
      this.password = password;
      return this;
    }

    public Impersonation build() {
      try {
        return new Impersonation(name, basedir, alias, password);
      } catch (GeneralSecurityException | IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}

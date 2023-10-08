package de.ganskef.okproxy

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

class Impersonation
private constructor(name: String, basedir: String, alias: String, password: CharArray) {
  private val intermediate: HeldCertificate

  init {
    val keystoreFile = File("$basedir/$alias.p12").canonicalFile
    if (keystoreFile.exists()) {
      val loaded = KeyStore.getInstance("PKCS12")
      FileInputStream(keystoreFile).use { input -> loaded.load(input, password) }
      val cert = loaded.getCertificate(alias) as X509Certificate
      val privateKey = loaded.getKey(alias, password) as PrivateKey
      val publicKey = cert.publicKey
      val keyPair = KeyPair(publicKey, privateKey)
      intermediate = HeldCertificate(keyPair, cert)
      logger.info { "Load: $name from $keystoreFile" }
    } else {
      val ms = System.currentTimeMillis()
      val root: HeldCertificate =
        HeldCertificate.Builder()
          .certificateAuthority(1)
          .duration(ROOT_VALIDITY_DAYS, TimeUnit.DAYS)
          .commonName(name)
          .organizationalUnit("$name Root CA")
          .build()
      intermediate =
        HeldCertificate.Builder()
          .certificateAuthority(0)
          .duration(ROOT_VALIDITY_DAYS, TimeUnit.DAYS)
          .commonName(name)
          .organizationalUnit("$name CA")
          .signedBy(root)
          .build()
      val keystore = KeyStore.getInstance("PKCS12")
      keystore.load(null, null)
      val chain = arrayOf<Certificate>(intermediate.certificate)
      keystore.setKeyEntry(alias, intermediate.keyPair.private, password, chain)
      FileOutputStream(keystoreFile).use { outputStream -> keystore.store(outputStream, password) }
      FileWriter(File(basedir, "$alias.pem")).use { writer ->
        writer.write(intermediate.certificatePem())
      }
      logger.info {
        "Init: ${System.currentTimeMillis() - ms}ms $name into $keystoreFile and $alias.pem"
      }
    }
  }

  @Throws(IOException::class)
  fun createSSLContext(securedAddress: String): SSLContext {
    Objects.requireNonNull(securedAddress)
    val ms = System.currentTimeMillis()
    val server: HeldCertificate =
      HeldCertificate.Builder()
        .commonName(securedAddress)
        .addSubjectAlternativeName(securedAddress)
        .duration(SERVER_VALIDITY_DAYS, TimeUnit.DAYS)
        .signedBy(intermediate)
        .serialNumber(initRandomSerial())
        .build()
    val hc: HandshakeCertificates =
      HandshakeCertificates.Builder().heldCertificate(server, intermediate.certificate).build()
    val sslContext = hc.sslContext()
    logger.fine {
      "Sign: ${System.currentTimeMillis() - ms}ms #$securedAddress $server.certificate.serialNumber"
    }
    return sslContext
  }

  private fun initRandomSerial(): Long {
    val rnd = Random()
    rnd.setSeed(System.currentTimeMillis())
    // prevent browser certificate caches, cause of doubled serial numbers
    // using 48bit random number
    var sl = rnd.nextInt().toLong() shl 32 or (rnd.nextInt().toLong() and 0xFFFFFFFFL)
    // let reserve of 16 bit for increasing, serials have to be positive
    sl = sl and 0x0000FFFFFFFFFFFFL
    return sl
  }

  /** Certificate to sign server certificates while impersonation. */
  fun intermediateCertificate(): X509Certificate {
    return intermediate.certificate
  }

  class Builder {
    private var basedir = System.getProperty("user.dir")
    // TODO read properties
    private var alias = "okproxy"
    private var name = "_OkProxy (Man In The Middle)"
    private var password = "".toCharArray()

    /**
     * Formal name to use in the certificates. Default starts with an underscore to order at the
     * top.
     */
    fun name(name: String): Builder {
      Objects.requireNonNull(name, "Given name must not be null.")
      require(name.trim { it <= ' ' }.isNotEmpty()) { "Given name must not be empty: '$name'" }
      this.name = name
      return this
    }

    /** Technical name used in threads, certificate alias and files. */
    fun alias(alias: String): Builder {
      Objects.requireNonNull(alias, "Given alias must not be null.")
      require(Pattern.matches("\\w+", alias)) { "Given alias must be an identifier: '$alias'" }
      this.alias = alias
      return this
    }

    fun basedir(basedir: String): Builder {
      Objects.requireNonNull(basedir, "Given basedir must not be null.")
      val dir = File(basedir)
      require(dir.isDirectory) { "Given basedir must be a directory: '$basedir'" }
      this.basedir = basedir
      return this
    }

    fun password(password: CharArray): Builder {
      Objects.requireNonNull(password, "Given password must not be null.")
      this.password = password
      return this
    }

    fun build(): Impersonation {
      return try {
        Impersonation(name, basedir, alias, password)
      } catch (e: GeneralSecurityException) {
        throw IllegalStateException(e)
      } catch (e: IOException) {
        throw IllegalStateException(e)
      }
    }
  }

  companion object {
    private val logger = Logger.getLogger(Impersonation::class.java.name)
    private const val ROOT_VALIDITY_DAYS = (100 * 365).toLong()
    private const val SERVER_VALIDITY_DAYS: Long = 365
  }
}

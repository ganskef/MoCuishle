package de.ganskef.okproxy

import java.io.FileInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Base implementation of an HTTP server using TLS. See [okhttp3.OkHttpClient] for interceptors,
 * events, caching and other upstream features.
 */
class SecuredServer(private val sslContext: SSLContext, root: String?, port: Int) :
  SimpleServer(root!!, port) {
  override fun configure(server: OkHttpServer?) {
    server!!.useHttps(sslContext.socketFactory)
  }

  companion object {
    private const val DEFAULT_PORT = 9090

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        val port = if (args.isNotEmpty()) args[0].toInt() else DEFAULT_PORT
        val root = if (args.size > 1) args[1] else "target"
        val sslContext: SSLContext =
          if (args.size > 3) {
            val keystoreFile = args[2]
            val password = args[3]
            sslContext(keystoreFile, password)
          } else {
            val impersonator = Impersonation.Builder().build()
            impersonator.createSSLContext("localhost")
          }
        val server = SecuredServer(sslContext, root, port)
        server.run()
      } catch (e: Exception) {
        println(
          String.format(
            "Usage: %s <port|%s> <root directory|target> <keystore> <password>",
            SecuredServer::class.java.simpleName,
            DEFAULT_PORT
          )
        )
        throw e
      }
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun sslContext(keystoreFile: String, password: String): SSLContext {
      val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
      FileInputStream(keystoreFile).use { `in` -> keystore.load(`in`, password.toCharArray()) }
      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
      keyManagerFactory.init(keystore, password.toCharArray())
      val trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      trustManagerFactory.init(keystore)
      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(
        keyManagerFactory.keyManagers,
        trustManagerFactory.trustManagers,
        SecureRandom()
      )
      return sslContext
    }
  }
}

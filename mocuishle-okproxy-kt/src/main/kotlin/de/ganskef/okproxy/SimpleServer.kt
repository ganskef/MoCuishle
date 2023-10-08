package de.ganskef.okproxy

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import okhttp3.HttpUrl
import okio.Buffer
import okio.source

/**
 * Base implementation of a HTTP server. See [okhttp3.OkHttpClient] for interceptors, events,
 * caching and other upstream features.
 */
open class SimpleServer(private val root: String, private val port: Int) : Dispatcher() {
  @Throws(IOException::class)
  fun run() {
    server = OkHttpServer()
    server!!.dispatcher = this
    configure(server)
    server!!.start(port)
  }

  fun url(path: String?): HttpUrl {
    Objects.requireNonNull(server, "server")
    return server!!.url(path!!)
  }

  protected open fun configure(server: OkHttpServer?) {}

  override fun dispatch(request: Request): Response {
    val path = request.path
    return try {
      if (!path!!.startsWith("/") || path.contains("..")) throw FileNotFoundException()
      val file = File(root + path)
      if (file.isDirectory) directoryToResponse(path, file) else fileToResponse(path, file)
    } catch (e: FileNotFoundException) {
      Response()
        .setStatus("HTTP/1.1 404")
        .addHeader("content-type: text/plain; charset=utf-8")
        .setBody("NOT FOUND: $path")
    } catch (e: IOException) {
      Response()
        .setStatus("HTTP/1.1 500")
        .addHeader("content-type: text/plain; charset=utf-8")
        .setBody("SERVER ERROR: $e")
    }
  }

  private fun directoryToResponse(basePath: String?, directory: File): Response {
    var rootPath = basePath
    if (!rootPath!!.endsWith("/")) rootPath += "/"
    val response = StringBuilder()
    response.append(String.format("<html><head><title>%s</title></head><body>", rootPath))
    response.append(String.format("<h1>%s</h1>", rootPath))
    for (file in directory.list()) {
      response.append(
        String.format("<div class='file'><a href='%s'>%s</a></div>", rootPath + file, file)
      )
    }
    response.append("</body></html>")
    return Response()
      .setStatus("HTTP/1.1 200")
      .addHeader("content-type: text/html; charset=utf-8")
      .setBody(response.toString())
  }

  @Throws(IOException::class)
  private fun fileToResponse(path: String?, file: File): Response {
    return Response()
      .setStatus("HTTP/1.1 200")
      .setBody(fileToBytes(file))
      .addHeader("content-type: " + contentType(path))
  }

  @Throws(IOException::class)
  private fun fileToBytes(file: File): Buffer {
    val result = Buffer()
    result.writeAll(file.source())
    return result
  }

  private fun contentType(path: String?): String {
    if (path!!.endsWith(".png")) return "image/png"
    if (path.endsWith(".jpg")) return "image/jpeg"
    if (path.endsWith(".jpeg")) return "image/jpeg"
    if (path.endsWith(".gif")) return "image/gif"
    if (path.endsWith(".html")) return "text/html; charset=utf-8"
    return if (path.endsWith(".txt")) "text/plain; charset=utf-8" else "application/octet-stream"
  }

  companion object {
    private const val DEFAULT_PORT = 9090
    private var server: OkHttpServer? = null

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        val port = if (args.size > 0) args[0].toInt() else DEFAULT_PORT
        val root = if (args.size > 1) args[1] else "target"
        val server = SimpleServer(root, port)
        server.run()
      } catch (e: Exception) {
        println(
          String.format(
            "Usage: %s <port|%s> <root directory|.>",
            SimpleServer::class.java.simpleName,
            DEFAULT_PORT
          )
        )
        throw e
      }
    }
  }
}

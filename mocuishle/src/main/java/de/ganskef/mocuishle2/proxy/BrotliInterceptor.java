package de.ganskef.mocuishle2.proxy;

import java.io.IOException;
import java.util.zip.Inflater;
import okhttp3.*;
import okio.*;
import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrotliInterceptor implements Interceptor {
  private static final Logger LOG = LoggerFactory.getLogger(BrotliInterceptor.class);
  private static final int HTTP_CONTINUE = 100, HTTP_NO_CONTENT = 204, HTTP_NOT_MODIFIED = 304;

  @Override
  public okhttp3.Response intercept(Interceptor.Chain chain) throws IOException {
    okhttp3.Request request =
        chain
            .request()
            .newBuilder()
            .removeHeader("Accept-Encoding")
            .addHeader("Accept-Encoding", "br, gzip, deflate")
            .build();
    okhttp3.Response response = chain.proceed(request);
    return uncompress(request, response);
  }

  private okhttp3.Response uncompress(Request request, final Response response) {
    // HEAD requests never yield a body regardless of the response headers.
    if (request.method().equals("HEAD")) {
      return response;
    }

    int responseCode = response.code();
    if (!((responseCode < HTTP_CONTINUE || responseCode >= 200)
        && responseCode != HTTP_NO_CONTENT
        && responseCode != HTTP_NOT_MODIFIED)) {
      return response;
    }

    ResponseBody body = response.body();
    if (body == null) {
      return response;
    }
    Headers headers = response.headers();
    String contentEncoding = headers.get("Content-Encoding");
    if (contentEncoding == null) {
      return response;
    }

    LOG.debug("Decode {}", contentEncoding);
    if (contentEncoding.equalsIgnoreCase("br")) {
      try (Source source = Okio.source(new BrotliInputStream(body.source().inputStream()))) {
        return write(source, response, body);
      } catch (Exception e) {
        LOG.info("Decode br failed {}", String.valueOf(e));
      }
    } else if (contentEncoding.equalsIgnoreCase("gzip")) {
      try (GzipSource source = new GzipSource(body.source())) {
        return write(source, response, body);
      } catch (Exception e) {
        LOG.info("Decode gzip failed {}", String.valueOf(e));
      }
    } else if (contentEncoding.equalsIgnoreCase("deflate")) {
      try (InflaterSource source = new InflaterSource(body.source(), new Inflater())) {
        return write(source, response, body);
      } catch (Exception e) {
        LOG.info("Decode deflate failed {}", String.valueOf(e));
      }
    }
    return response;
  }

  private okhttp3.Response write(Source source, okhttp3.Response response, ResponseBody body)
      throws IOException {
    Buffer buffer = new Buffer();
    buffer.writeAll(source);
    long contentLength = buffer.size();
    return response
        .newBuilder()
        .removeHeader("Content-Encoding")
        .removeHeader("Content-Length")
        .body(ResponseBody.create(body.contentType(), contentLength, buffer))
        .build();
  }
}

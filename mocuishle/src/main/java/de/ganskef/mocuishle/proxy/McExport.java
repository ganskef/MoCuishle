package de.ganskef.mocuishle.proxy;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.IActionExport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedInput;
import io.netty.util.CharsetUtil;

/**
 * Implementation of {@link HttpFilters}. Filters requests on their way from the
 * client to the proxy server. Answers a file download request. Introduced to
 * export cached entries of a host in a ZIP archive.
 *
 * @see <code>io.netty.example.http.file.HttpStaticFileServerHandler</code>
 */
public class McExport extends HttpFiltersAdapter {

	private static final Logger log = LoggerFactory.getLogger(McExport.class);

	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
	public static final int HTTP_CACHE_SECONDS = 60;

	private final String mAbsoluteFile;

	private HttpRequest mRequest;

	public McExport(HttpRequest originalRequest, ChannelHandlerContext ctx, IActionExport action) {
		super(originalRequest, ctx);
		mAbsoluteFile = action.prepareAnswer();
	}

	@Override
	public HttpResponse clientToProxyRequest(HttpObject httpObject) {
		// TODO verify and remove obsolete stuff from example code
		if (!(httpObject instanceof HttpRequest)) {
			log.warn("Expect HttpRequest but receive {}", httpObject);
			return null;
		}
		// TODO evaluate to use originalRequest here
		HttpRequest request = (HttpRequest) httpObject;
		mRequest = request;

		if (!request.decoderResult().isSuccess()) {
			sendError(ctx, BAD_REQUEST);
			return null;
		}

		if (!GET.equals(request.method())) {
			sendError(ctx, METHOD_NOT_ALLOWED);
			return null;
		}

		final boolean keepAlive = HttpUtil.isKeepAlive(request);

		File file = new File(mAbsoluteFile);
		if (file.isHidden() || !file.exists()) {
			sendError(ctx, NOT_FOUND);
			return null;
		}

		if (!file.isFile()) {
			sendError(ctx, FORBIDDEN);
			return null;
		}

		long fileLength = -1L;
		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(file, "r");
			fileLength = raf.length();
		} catch (FileNotFoundException e) {
			log.error("Could not find formerly existing file.", e);
			sendError(ctx, INTERNAL_SERVER_ERROR);
			return null;
		} catch (IOException e) {
			log.error("Could not get length of download file.", e);
			sendError(ctx, INTERNAL_SERVER_ERROR);
			return null;
		}

		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		HttpUtil.setContentLength(response, fileLength);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
		setDateAndCacheHeaders(response, file);

		if (!keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (request.protocolVersion().equals(HTTP_1_0)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		// Write the initial line and the header.
		ctx.write(response);

		// Write the content.
		ChannelFuture sendFileFuture;
		ChannelFuture lastContentFuture;
		if (ctx.pipeline().get(SslHandler.class) == null) {
			sendFileFuture = ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength),
					ctx.newProgressivePromise());
			// Write the end marker.
			lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		} else {
			ChunkedInput<ByteBuf> chunkedFile;
			try {
				chunkedFile = new ChunkedFile(raf, 0, fileLength, 8192);
			} catch (IOException e) {
				log.error("Could not instantiate chunked file.", e);
				sendError(ctx, INTERNAL_SERVER_ERROR);
				return null;
			}
			sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(chunkedFile), ctx.newProgressivePromise());
			// HttpChunkedInput will write the end marker (LastHttpContent) for us.
			lastContentFuture = sendFileFuture;
		}

		sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
				if (total < 0) { // total unknown
					log.info("{} Transfer progress: {}", future.channel(), progress);
				} else {
					log.info("{} Transfer progress: {} / {}", future.channel(), progress, total);
				}
			}

			@Override
			public void operationComplete(ChannelProgressiveFuture future) {
				log.info("{} Transfer complete.", future.channel());
				File file = new File(mAbsoluteFile);
				if (!file.delete()) {
					log.warn("Couldn't delete temp file {}", mAbsoluteFile);
					file.deleteOnExit();
				}
			}
		});

		// Decide whether to close the connection or not.
		if (!keepAlive) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
		return super.clientToProxyRequest(httpObject);
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
				Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		sendAndCleanupConnection(ctx, response);
	}

	/**
	 * If Keep-Alive is disabled, attaches "Connection: close" header to the
	 * response and closes the connection after the response being sent.
	 */
	private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpResponse response) {
		final boolean keepAlive = HttpUtil.isKeepAlive(mRequest);
		HttpUtil.setContentLength(response, response.content().readableBytes());
		if (!keepAlive) {
			// We're going to close the connection as soon as the response is sent,
			// so we should also make it clear for the client.
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (mRequest.protocolVersion().equals(HTTP_1_0)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		ChannelFuture flushPromise = ctx.writeAndFlush(response);

		if (!keepAlive) {
			// Close the connection as soon as the response is sent.
			flushPromise.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/**
	 * Sets the Date and Cache headers for the HTTP Response
	 *
	 * @param response    HTTP response
	 * @param fileToCache file to extract content type
	 */
	private void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		// Date header
		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

		// Add cache headers
		time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
		response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
		response.headers().set(HttpHeaderNames.LAST_MODIFIED,
				dateFormatter.format(new Date(fileToCache.lastModified())));
	}
}

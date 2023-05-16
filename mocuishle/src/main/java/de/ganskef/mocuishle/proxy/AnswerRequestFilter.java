package de.ganskef.mocuishle.proxy;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Implementation of {@link HttpFilters}. Filters requests on their way from the
 * client to the proxy server. Returns short-circuit responses containing body
 * content in a {@link FullHttpResponse}.
 *
 * <p>
 * Dynamic content of the web application: pages, images and other resources...
 */
public class AnswerRequestFilter extends HttpFiltersAdapter {

	private static final Charset TRANSPARENT_ENCODING = Charset.forName("ISO-8859-1");

	private static final Charset TEXT_ENCODING = Charset.forName("UTF-8");

	private static final Pattern FILE_TYPE_PATTERN = Pattern.compile(".*\\.(.*?)(:?[#?].*)?");

	private final String mAnswer;

	private final HttpResponseStatus mStatus;

	public AnswerRequestFilter(HttpRequest originalRequest, String answer, HttpResponseStatus... status) {
		super(originalRequest);
		mAnswer = answer;
		mStatus = (status != null && status.length > 0) ? status[0] : HttpResponseStatus.OK;
	}

	@Override
	public HttpResponse clientToProxyRequest(HttpObject httpObject) {
		String type = "text/html";
		Matcher m = FILE_TYPE_PATTERN.matcher(originalRequest.getUri());
		if (m.matches()) {
			String fileExtension = m.group(1);
			if (fileExtension.equalsIgnoreCase("css")) {
				type = "text/css";
			} else if (fileExtension.equalsIgnoreCase("png")) {
				type = "image/png";
			} else if (fileExtension.equalsIgnoreCase("ico")) {
				type = "image/x-icon";
			} else if (fileExtension.equalsIgnoreCase("woff2")) {
				type = "application/octet-stream";
			} else if (fileExtension.equalsIgnoreCase("woff")) {
				type = "application/octet-stream";
			} else if (fileExtension.equalsIgnoreCase("ttf")) {
				type = "application/octet-stream";
			} else if (fileExtension.equalsIgnoreCase("js")) {
				type = "text/javascript";
			}
		}
		Charset encoding = type.startsWith("text/") ? TEXT_ENCODING : TRANSPARENT_ENCODING;
		ByteBuf buffer = Unpooled.wrappedBuffer(mAnswer.getBytes(encoding));
		HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, mStatus, buffer);
		HttpHeaders.setContentLength(response, buffer.readableBytes());
		HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE, type);
		return response;
	}
}

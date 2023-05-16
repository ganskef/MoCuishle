package de.ganskef.mocuishle.proxy;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Date;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.littleshoot.proxy.HttpFiltersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICacheableProxy;
import de.ganskef.mocuishle.ICacheableProxy.Alerts;
import de.ganskef.mocuishle.ICacheableProxy.CachedResponse;
import de.ganskef.mocuishle.cache.IProxy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

public class McHttpFilters extends HttpFiltersAdapter implements IProxy {

	private static final Logger log = LoggerFactory.getLogger(McHttpFilters.class);

	private static final Pattern UNSUPPORTED_ENCODING_PATTERN = Pattern
			.compile("(\\s*(?:,\\s*br|br\\s,|br|,\\s*sdch|sdch\\s*,|sdch)\\s*)", Pattern.CASE_INSENSITIVE);

	private ICacheableProxy mElement;

	private boolean mRefresh;

	public McHttpFilters(ICache cache, HttpRequest originalRequest, ChannelHandlerContext ctx, String uri) {
		super(originalRequest, ctx);
		mElement = cache.createElement(uri);
	}

	@Override
	public HttpObject serverToProxyResponse(HttpObject httpObject) {
		log.debug("serverToProxyResponse {}", httpObject);
		if (mElement.isIgnored()) {
			log.info("IGNORED host null for {}", httpObject);
			return httpObject; // ignored
		}
		if (httpObject instanceof FullHttpResponse) {
			mElement.deleteOutgoing();
			FullHttpResponse response = (FullHttpResponse) httpObject;
			HttpResponseStatus status = response.getStatus();
			if (status.equals(HttpResponseStatus.NOT_MODIFIED)) {
				return createCachedResponse(true);
			}
			if (McHttpHeaders.isSpooledStatus(status)) {
				spool(response);
				if (McHttpHeaders.isContentTypeTextHtml(response.headers())) {
					return modify(response);
				}
			} else {
				log.info("State {} for {}", status, originalRequest.getUri());
			}
			log.debug("origin headers {}", response.headers().entries());
		} else {
			log.warn("Unexpected type: {}", httpObject.getClass());
		}
		return httpObject;
	}

	private HttpObject modify(FullHttpResponse original) {
		ByteBuffer content = original.content().nioBuffer();
		ByteBuffer modified = mElement.getModifiedTextHtml(content, true);
		ByteBuf wrapped = Unpooled.wrappedBuffer(modified);
		HttpHeaders headers = original.headers();
		HttpResponseStatus status = original.getStatus();
		HttpResponse result = createResponse(status, headers, wrapped);
		return result;
	}

	private void spool(FullHttpResponse response) {
		try {
			ByteBuf content = response.content().copy();
			ByteBuffer spoolBuffer = content.nioBuffer();

			Iterable<Entry<String, String>> headers = response.headers();
			HttpVersion protocolVersion = response.getProtocolVersion();
			HttpResponseStatus status = response.getStatus();
			String stateLine = String.format("%s %s\r\n", protocolVersion, status);
			mElement.store(status.code(), stateLine, headers, spoolBuffer);

			// mElement.deleteOutgoing();
		} catch (Exception e) {
			log.error("Can't store response " + response, e);
			mElement.addAlert(Alerts.NOT_STORED);
		}
	}

	@Override
	public HttpResponse clientToProxyRequest(HttpObject httpObject) {
		log.debug("clientToProxyRequest {}", httpObject);
		if (httpObject == LastHttpContent.EMPTY_LAST_CONTENT) {
			// Warum kommen hier lauter EMPTY_LAST_CONTENT an? 1.0.0-beta6
			// Was soll man damit anfangen? Gibt es gechunkte Requests? POST?
			return null;
		}
		if (!(httpObject instanceof HttpRequest)) {
			log.warn("Expect HttpRequest but receive {}", httpObject);
			return null;
		}
		HttpRequest request = (HttpRequest) httpObject;
		HttpMethod method = request.getMethod();
		if (method == HttpMethod.CONNECT) {
			log.warn("{}", request);
			return null;
		}
		String methodName = method.name();
		String protocolVersion = request.getProtocolVersion().text();
		HttpHeaders headers = request.headers();
		mRefresh = McHttpHeaders.isRefresh(headers);
		if (mElement.isDocumentExtension()
				|| (!mElement.isNocontentExtension() && McHttpHeaders.isAcceptTextHtml(headers))) {
			// record the page request only, since fetch loads references
			mElement.recordForDownload(methodName, protocolVersion, headers);
		}
		if (mElement.needBadGateway()) {
			return createBadGatewayResponse();
		}
		if (mElement.needCached(mRefresh)) {
			HttpResponse response = createCachedResponse(false);
			if (!McHttpHeaders.isContentTypeTextHtml(response.headers())) {
				// FIXME removes document extensions ???
				mElement.deleteOutgoing();
			}
			return response;
		}
		if (mRefresh) {
			headers.remove(HttpHeaders.Names.IF_MODIFIED_SINCE);
			headers.remove(HttpHeaders.Names.IF_NONE_MATCH);
		} else {
			long lastModified = mElement.lastModified();
			if (lastModified != 0L) {
				HttpHeaders.setDateHeader(request, HttpHeaders.Names.IF_MODIFIED_SINCE, new Date(lastModified));
			}
		}
		String allowedEncoding = headers.get(HttpHeaders.Names.ACCEPT_ENCODING);
		if (allowedEncoding != null) {
			Matcher m = UNSUPPORTED_ENCODING_PATTERN.matcher(allowedEncoding);
			if (m.find()) {
				String filtered = m.replaceAll("");
				headers.set(HttpHeaders.Names.ACCEPT_ENCODING, filtered);
			}
		}
		return null;
	}

	private HttpResponse createBadGatewayResponse() {
		// ByteBuf buffer =
		// Unpooled.copiedBuffer("Bad Gateway, resource not available in Offline mode",
		// McElement.DEFAULT_ENCODING);
		// HttpHeaders headers = new DefaultHttpHeaders();
		// HttpResponse response =
		// createResponse(HttpResponseStatus.BAD_GATEWAY, headers, buffer);

		HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
		HttpHeaders.setHeader(response, HttpHeaders.Names.LOCATION, mElement.getProxyHome() + "/browse-outgoing");
		HttpHeaders.setHeader(response, Names.CONNECTION, Values.CLOSE);
		return response;
	}

	private HttpResponse createCachedResponse(boolean online) {
		CachedResponse cachedResponse = mElement.readCachedResponseToView();
		HttpResponseStatus status = HttpResponseStatus.valueOf(cachedResponse.getStatusCode());
		ByteBuffer buffer = cachedResponse.getContent();
		HttpHeaders cachedHeaders = new McHttpHeaders(cachedResponse.getHeaders());
		if (McHttpHeaders.isContentTypeTextHtml(cachedHeaders)) {
			buffer = mElement.getModifiedTextHtml(buffer, online);
		}
		HttpResponse response = createResponse(status, cachedHeaders, Unpooled.wrappedBuffer(buffer));
		updateHeaderDates(response);
		return response;
	}

	private void updateHeaderDates(HttpResponse response) {
		long loadDistance = System.currentTimeMillis() - mElement.lastModified();
		for (String each : new String[] { Names.DATE, Names.EXPIRES, Names.LAST_MODIFIED }) {
			Date date = null;
			try {
				date = HttpHeaders.getDateHeader(response, each);
			} catch (ParseException e) {
				// ignore parse exception
			}
			if (date != null) {
				Date updated = new Date(date.getTime() + loadDistance);
				HttpHeaders.setDateHeader(response, each, updated);
			}
		}
		// String cookie = HttpHeaders.getHeader(response, Names.SET_COOKIE);
		// if (cookie != null) {
		// Set<Cookie> cookies = CookieDecoder.decode(cookie);
		// for (Cookie each : cookies) {
		// System.out.println(each);
		// }
		// }
	}

	private HttpResponse createResponse(HttpResponseStatus status, HttpHeaders headers, ByteBuf buffer) {
		HttpVersion version = HttpVersion.HTTP_1_1;
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(version, status, buffer);
		response.headers().set(headers);
		int length = buffer.readableBytes();
		HttpHeaders.setContentLength(response, length);
		log.debug("cached headers {}", response.headers().entries());
		return response;
	}
}

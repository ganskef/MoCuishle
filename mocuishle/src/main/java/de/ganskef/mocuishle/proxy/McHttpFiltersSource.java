package de.ganskef.mocuishle.proxy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.IActionExport;
import de.ganskef.mocuishle.IActionRedirect;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ui.Requested;
import de.ganskef.mocuishle.ui.SpecialUrl;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AttributeKey;

/**
 * Choose a {@link HttpFilters} implementation matching to the URL:
 *
 * <ul>
 * <li>Requests direct to the proxy address require a http server response like
 * an UI feature or a resource.
 * <li>Requests via HTTPS with CONNECT in the first request require initializing
 * the MITM feature and putting the hostname in the context to use in the
 * following requests of the connection.
 * <li>Requests to a foreign host require a proxy response, cached or relayed.
 * There are different filters to answer contents like markup, resources,
 * redirect or download (ZIP).
 * </ul>
 */
public class McHttpFiltersSource extends HttpFiltersSourceAdapter {

	private static final Logger log = LoggerFactory.getLogger(McHttpFiltersSource.class);

	private static final Pattern HTTP_HOST_PATTERN = Pattern.compile("http://(.*?)/.*");

	private static final Pattern CONNECT_HOST_PATTERN = Pattern.compile("(.*?)(:\\d+)?");

	private final ICache mCache;

	private final Requested mRequested;

	public static final AttributeKey<String> CONNECTED_HOST_ATTRIBUTE = AttributeKey.valueOf("connectedHost");

	public static final AttributeKey<ChannelHandlerContext> CONNECTED_SERVER_CONTEXT = AttributeKey
			.valueOf("connectedServerContext");

	public McHttpFiltersSource(ICache cache) {
		mCache = cache;
		mRequested = new Requested();
	}

	@Override
	public HttpFilters filterRequest(final HttpRequest originalRequest, ChannelHandlerContext ctx) {
		String hostName = getHost(originalRequest);
		log.debug("Host {} from {}", hostName, originalRequest.getUri());
		log.debug("Host {} from {}", HttpHeaders.getHost(originalRequest), originalRequest);
		String url = originalRequest.getUri();
		if (hostName != null) {
			mRequested.add(hostName);
			// TODO config file for whitelisted URLs
			if (url.endsWith("/crossdomain.xml")) {
				log.info("Whitelisted {}", url);
				return createHttpFilters(originalRequest, ctx);
			}
			if (mCache.isBlocked(hostName)) {
				log.info("Blocked {}", url);
				return createAnswer(originalRequest, "");
			}
			if (originalRequest.getMethod() == HttpMethod.CONNECT) {
				log.debug("https CONNECT to {} {}", hostName, originalRequest);
				ctx.channel().attr(CONNECTED_HOST_ATTRIBUTE).set(url.replaceFirst(":443$", ""));
				return new HttpFiltersAdapter(originalRequest, ctx) {
					@Override
					public void proxyToServerConnectionSucceeded(ChannelHandlerContext serverCtx) {
						ctx.channel().attr(CONNECTED_SERVER_CONTEXT).set(serverCtx);
					}
				};
			}
			if (mCache.isPassed(url)) {
				log.info("Passed {}", url);
				return createForwarder(originalRequest, ctx);
			}

		} else {
			String connectedHost = ctx.channel().attr(CONNECTED_HOST_ATTRIBUTE).get();
			if (connectedHost != null) {
				mRequested.add(connectedHost);
				String uri = "https://" + connectedHost + url;
				if (mCache.isPassed(uri)) {
					log.info("Passed {}", uri);
					return createForwarder(originalRequest, ctx);
				}
				log.debug("{} {} {}", originalRequest.getMethod(), uri, originalRequest);
				return createHttpFilters(originalRequest, ctx, uri);
			}

			IAction action = SpecialUrl.createAnswer(mCache, mRequested, url);
			if (action instanceof IActionRedirect) {
				return new RedirRequestFilter(originalRequest, ctx, (IActionRedirect) action);
			}
			if (action instanceof IActionExport) {
				return new McExport(originalRequest, ctx, (IActionExport) action);
			}
			HttpResponseStatus status = HttpResponseStatus.valueOf(action.status().code());
			if (status != HttpResponseStatus.OK) {
				log.info("{} {}", status, url);
			}
			String markup = action.prepareAnswer();
			return createAnswer(originalRequest, markup, HttpResponseStatus.valueOf(action.status().code()));
		}
		log.debug("Adding filter for {}", url);
		return createHttpFilters(originalRequest, ctx);
	}

	private String getHost(HttpRequest originalRequest) {
		String uri = originalRequest.getUri();
		Matcher m;
		if (originalRequest.getMethod() == HttpMethod.CONNECT) {
			m = CONNECT_HOST_PATTERN.matcher(uri);
		} else {
			m = HTTP_HOST_PATTERN.matcher(uri);
		}
		if (m.matches()) {
			return m.group(1);
		}
		return null;
	}

	@Override
	public int getMaximumResponseBufferSizeInBytes() {
		return mCache.getMaximumBufferSizeInBytes();
	}

	private HttpFilters createAnswer(final HttpRequest originalRequest, String markup, HttpResponseStatus... status) {
		return new AnswerRequestFilter(originalRequest, markup, status);
	}

	private HttpFilters createForwarder(final HttpRequest originalRequest, ChannelHandlerContext clientCtx) {
		if (mCache.isOffline()) {
			return createAnswer(originalRequest, "Offline");
		}
		return new HttpFiltersAdapter(originalRequest, clientCtx) {
			@Override
			public void proxyToServerConnectionSucceeded(ChannelHandlerContext serverCtx) {
				ctx.channel().attr(CONNECTED_SERVER_CONTEXT).set(serverCtx);
			}

			@Override
			public void proxyToServerRequestSending() {
				ChannelHandlerContext serverCtx = ctx.channel().attr(CONNECTED_SERVER_CONTEXT).get();
				ChannelPipeline pipeline = serverCtx.pipeline();
				if (pipeline.get("inflater") != null) {
					log.debug("Remove inflator from {}", pipeline);
					pipeline.remove("inflater");
				}
				if (pipeline.get("aggregator") != null) {
					log.debug("Remove aggregator from {}", pipeline);
					pipeline.remove("aggregator");
				}
			}
		};
	}

	private HttpFilters createHttpFilters(HttpRequest originalRequest, ChannelHandlerContext ctx) {
		return createHttpFilters(originalRequest, ctx, originalRequest.getUri());
	}

	private HttpFilters createHttpFilters(HttpRequest originalRequest, ChannelHandlerContext ctx, String uri) {
		// if (mCache.isDisabled()) {
		// return createForwarder(originalRequest, ctx);
		// }
		return new McHttpFilters(mCache, originalRequest, ctx, uri);
	}
}

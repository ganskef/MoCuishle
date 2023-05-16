package de.ganskef.mocuishle.proxy;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.util.HttpDateUtil;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class McHttpHeaders extends DefaultHttpHeaders {

	private static final Logger log = LoggerFactory.getLogger(McHttpHeaders.class);

	/* FIXME use static methods */
	@Deprecated
	public McHttpHeaders(Iterable<Entry<String, String>> headers) {
		for (Map.Entry<String, String> each : headers) {
			set(each.getKey(), each.getValue());
		}
	}

	public static final boolean isAcceptTextHtml(HttpHeaders headers) {
		String contentType = headers.get(Names.ACCEPT);
		return isContentTypeTextHtml(contentType);
	}

	public static final boolean isContentTypeTextHtml(HttpHeaders headers) {
		String contentType = headers.get(Names.CONTENT_TYPE);
		return isContentTypeTextHtml(contentType);
	}

	public static String getContentType(Iterable<Entry<String, String>> headers) {
		for (Map.Entry<String, String> each : headers) {
			if ("content-type".equalsIgnoreCase(each.getKey())) {
				return each.getValue();
			}
		}
		return null;
	}

	public static boolean isContentTypeTextHtml(Iterable<Entry<String, String>> headers) {
		String contentType = getContentType(headers);
		if (contentType != null) {
			return isContentTypeTextHtml(contentType);
		}
		return false;
	}

	public static boolean isContentTypeTextHtml(String contentType) {
		return contentType != null && (contentType.contains("text/html") || contentType.contains("application/xhtml"));
	}

	/* FIXME use static methods */
	@Deprecated
	public boolean isContentTypeTextHtml() {
		return isContentTypeTextHtml(this);
	}

	/**
	 * Das wird bei WWWOFFLE ganz anders verwendet, als ich mir vorgestellt hatte.
	 * Hier geht es um die Antwort mit einem 304 State:
	 *
	 * <pre>
	 * Return a not-modified header if it isn't modified.
	 * </pre>
	 */
	@Deprecated
	public boolean isModified(HttpRequest request) {
		// see wwwoffle parse.c IsModified

		boolean isModified = true;
		boolean checkTime = true;

		HttpHeaders requestHeaders = request.headers();

		/* Check the entity tags */
		final List<String> ifNonMaches = requestHeaders.getAll(Names.IF_NONE_MATCH);
		final String etag = get(Names.ETAG);
		if (!ifNonMaches.isEmpty() && etag != null) {
			checkTime = false;
			for (String each : ifNonMaches) {
				if ("*".equals(each) || !etag.equals(each)) {
					log.info("Matching ETag with If-None-Match header value {} {} of {}", etag, each, ifNonMaches);
					isModified = false;
					checkTime = true;
					break;
				}
			}
		}
		/* Check the If-Modified-Since header if there are no matching Etags */
		if (checkTime) {
			String ifModifiedSince = requestHeaders.get(Names.IF_MODIFIED_SINCE);
			if (ifModifiedSince != null) {
				String lastModified = get(Names.LAST_MODIFIED);
				if (lastModified != null) {
					isModified = HttpDateUtil.lt(ifModifiedSince, lastModified);
					if (isModified) {
						log.info("If modified since header is less than last modified {} {}", ifModifiedSince,
								lastModified);
					}
				} else {
					log.warn("NOT IMPLEMENTED YET: compare file with IF_MODIFIED_SINCE {}", ifModifiedSince);
				}
			}
		}

		return isModified;
	}

	public static final Date getLastModified(HttpMessage message) {
		try {
			return HttpHeaders.getDateHeader(message, HttpHeaders.Names.LAST_MODIFIED);
		} catch (ParseException e) {
			return null;
		}
	}

	public static final String getHeaderText(HttpResponse response) {
		// FIXME remove duplicate code
		// de.ganskef.mocuishle.proxy.McHttpFilters.getHeaderText(LastHttpContent)

		StringBuilder b = new StringBuilder();
		HttpVersion protocolVersion = response.getProtocolVersion();
		HttpResponseStatus status = response.getStatus();
		b.append(protocolVersion).append(' ').append(status).append('\r').append('\n');
		HttpHeaders headers = response.headers();
		for (Entry<String, String> each : headers) {
			b.append(each.getKey()).append(": ").append(each.getValue()).append('\r').append('\n');
		}
		b.append('\r').append('\n');
		return b.toString();
	}

	public static final boolean isRefresh(HttpHeaders headers) {
		// see wwwoffle parse.c RequireForced
		final List<String> cacheControls = headers.getAll(HttpHeaders.Names.CACHE_CONTROL);
		for (String each : cacheControls) {
			if (Values.NO_CACHE.equals(each)) {
				CacheControl.logHeaderCacheControlIsNoCache();
				return true;
			}
			if ("max-age=0".equals(each)) {
				CacheControl.logHeaderCacheControlIsMaxAgeZero();
				return true;
			}
		}
		final List<String> pragmas = headers.getAll(HttpHeaders.Names.PRAGMA);
		for (String each : pragmas) {
			if (Values.NO_CACHE.equals(each)) {
				CacheControl.logHeaderPragmaIsNoCache();
				return true;
			}
		}
		// final String cookie = rhs.get(HttpHeaders.Names.COOKIE);
		// if (null != cookie) {
		// log.info("Header cookie is not null, refresh is requested.");
		// return true;
		// }
		CacheControl.logNoRefreshHeaderFound();
		return false;
	}

	// public Charset getEncoding() {
	// // TODO Auto-generated method stub
	// return null;
	// }

	public static boolean isSpooledStatus(HttpResponseStatus status) {
		return status.equals(HttpResponseStatus.OK) //
				|| status.equals(HttpResponseStatus.MOVED_PERMANENTLY) //
				|| status.equals(HttpResponseStatus.FOUND);
	}

	public static final boolean isRedirect(HttpHeaders headers) {
		return getLocation(headers) != null;
	}

	public static final String getLocation(HttpHeaders headers) {
		return headers.get(HttpHeaders.Names.LOCATION);
	}
}

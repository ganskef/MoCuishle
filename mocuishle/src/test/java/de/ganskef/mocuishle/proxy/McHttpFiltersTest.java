package de.ganskef.mocuishle.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICacheable;
import de.ganskef.mocuishle.ICacheableProxy.CachedResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class McHttpFiltersTest {

	@Test
	public void shouldAnswerSameIfElementIgnored() {
		String uri = null;

		ChannelHandlerContext ctx = null;

		ICacheable element = mock(ICacheable.class);
		when(element.isIgnored()).thenReturn(true); // <<< HERE

		HttpRequest originalRequest = mock(HttpRequest.class);

		ICache cache = mock(ICache.class);
		when(cache.createElement(uri)).thenReturn(element);

		HttpObject response = mock(HttpResponse.class);

		McHttpFilters filters = new McHttpFilters(cache, originalRequest, ctx, uri);

		HttpObject result = filters.serverToProxyResponse(response);

		assertSame(response, result);
	}

	@Test
	public void shouldAnswerCachedIfNotModified() {
		String uri = null;

		ChannelHandlerContext ctx = null;

		CachedResponse cachedResponse = mock(CachedResponse.class);
		when(cachedResponse.getContent()).thenReturn(ByteBuffer.wrap("Hallo Welt!".getBytes()));

		ICacheable element = mock(ICacheable.class);
		when(element.isIgnored()).thenReturn(false); // <<< HERE
		when(element.readCachedResponseToView()).thenReturn(cachedResponse);

		HttpRequest originalRequest = mock(HttpRequest.class);

		ICache cache = mock(ICache.class);
		when(cache.createElement(uri)).thenReturn(element);

		FullHttpResponse response = mock(FullHttpResponse.class); // <<< HERE
		when(response.getStatus()).thenReturn(HttpResponseStatus.NOT_MODIFIED);

		McHttpFilters filters = new McHttpFilters(cache, originalRequest, ctx, uri);
		DefaultFullHttpResponse result = (DefaultFullHttpResponse) filters.serverToProxyResponse(response);

		verify(cachedResponse).getContent();
		assertEquals("Hallo Welt!", new String(ByteBufUtil.getBytes(result.content())));
	}

	@Test
	public void shouldSpoolResponseIfStatusOK() {
		String uri = null;

		ChannelHandlerContext ctx = null;

		ICacheable element = mock(ICacheable.class);
		when(element.isIgnored()).thenReturn(false); // <<< HERE

		HttpRequest originalRequest = mock(HttpRequest.class);

		ICache cache = mock(ICache.class);
		when(cache.createElement(uri)).thenReturn(element);

		HttpHeaders headers = mock(HttpHeaders.class);

		FullHttpResponse response = mock(FullHttpResponse.class); // <<< HERE
		when(response.getStatus()).thenReturn(HttpResponseStatus.OK);
		when(response.getProtocolVersion()).thenReturn(HttpVersion.HTTP_1_1);
		when(response.headers()).thenReturn(headers);
		ByteBuf buffer = Unpooled.buffer();
		when(response.content()).thenReturn(buffer);

		McHttpFilters filters = new McHttpFilters(cache, originalRequest, ctx, uri);
		filters.serverToProxyResponse(response);

		verify(response).content();
		verify(response, atLeastOnce()).getProtocolVersion();
		verify(response, atLeastOnce()).headers();
		verify(element).store(200, "HTTP/1.1 200 OK\r\n", headers, buffer.nioBuffer());
	}

	@Test
	public void shouldModifySpooledText() {
		String uri = null;

		ChannelHandlerContext ctx = null;

		ICacheable element = mock(ICacheable.class);
		when(element.isIgnored()).thenReturn(false); // <<< HERE
		ByteBuf buffer = Unpooled.buffer();
		when(element.getModifiedTextHtml(buffer.nioBuffer(), true)).thenReturn(buffer.nioBuffer()); // <<< HERE

		HttpRequest originalRequest = mock(HttpRequest.class);
		when(originalRequest.headers()).thenReturn(HttpHeaders.EMPTY_HEADERS);

		ICache cache = mock(ICache.class);
		when(cache.createElement(uri)).thenReturn(element);

		HttpHeaders headers = mock(HttpHeaders.class);
		when(headers.get(Names.CONTENT_TYPE)).thenReturn("text/html");
		// don't duplicate mock in set
		when(headers.iterator()).thenReturn(Collections.emptyIterator());

		FullHttpResponse response = mock(FullHttpResponse.class); // <<< HERE
		when(response.getStatus()).thenReturn(HttpResponseStatus.OK);
		when(response.headers()).thenReturn(headers);
		when(response.content()).thenReturn(buffer);

		McHttpFilters filters = new McHttpFilters(cache, originalRequest, ctx, uri);
		filters.serverToProxyResponse(response);

		verify(element).getModifiedTextHtml(buffer.nioBuffer(), true);
	}

	@Test
	public void shouldModifyCachedText() {
		String uri = null;

		ChannelHandlerContext ctx = null;

		Map<String, String> entries = new HashMap<String, String>();
		entries.put(Names.CONTENT_TYPE, "text/html");
		HttpHeaders headers = new McHttpHeaders(entries.entrySet());
		// HttpHeaders headers = mock(HttpHeaders.class);
		// when(headers.get(Names.CONTENT_TYPE)).thenReturn("text/html");
		// when(headers.iterator()).thenReturn(Collections.emptyIterator());
		// when(headers.isEmpty()).thenReturn(true); // don't duplicate mock in set

		CachedResponse cachedResponse = mock(CachedResponse.class);
		ByteBuffer buffer = ByteBuffer.wrap("unmodified!".getBytes());
		when(cachedResponse.getContent()).thenReturn(buffer);
		when(cachedResponse.getHeaders()).thenReturn(headers);

		ICacheable element = mock(ICacheable.class);
		when(element.readCachedResponseToView()).thenReturn(cachedResponse);
		when(element.getModifiedTextHtml(buffer, false)).thenReturn(ByteBuffer.wrap("modified!".getBytes()));
		when(element.needCached(false)).thenReturn(true);

		ICache cache = mock(ICache.class);
		when(cache.createElement(uri)).thenReturn(element);

		HttpRequest originalRequest = mock(HttpRequest.class);

		HttpRequest request = mock(HttpRequest.class);
		when(request.headers()).thenReturn(HttpHeaders.EMPTY_HEADERS);
		when(request.getMethod()).thenReturn(HttpMethod.GET);
		when(request.getProtocolVersion()).thenReturn(HttpVersion.HTTP_1_1);

		McHttpFilters filters = new McHttpFilters(cache, originalRequest, ctx, uri);
		filters.clientToProxyRequest(request);

		verify(element).getModifiedTextHtml(buffer, false);
	}

	@Test
	public void shouldRemoveBrotliContentEncoding() {
		String uri = null;

		ChannelHandlerContext ctx = null;

		ICacheable element = mock(ICacheable.class);

		ICache cache = mock(ICache.class);
		when(cache.createElement(uri)).thenReturn(element);

		HttpRequest originalRequest = mock(HttpRequest.class);

		HttpHeaders headers = mock(HttpHeaders.class);
		when(headers.get(HttpHeaders.Names.ACCEPT_ENCODING)).thenReturn("gzip, deflate, br");

		HttpRequest request = mock(HttpRequest.class);
		when(request.headers()).thenReturn(headers);
		when(request.getMethod()).thenReturn(HttpMethod.GET);
		when(request.getProtocolVersion()).thenReturn(HttpVersion.HTTP_1_1);

		McHttpFilters filters = new McHttpFilters(cache, originalRequest, ctx, uri);
		filters.clientToProxyRequest(request);

		verify(headers).set(HttpHeaders.Names.ACCEPT_ENCODING, "gzip, deflate");
	}
}

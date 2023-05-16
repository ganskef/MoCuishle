package de.ganskef.mocuishle.proxy;

import org.littleshoot.proxy.HttpFiltersAdapter;

import de.ganskef.mocuishle.IActionRedirect;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class RedirRequestFilter extends HttpFiltersAdapter {

	private final String mLocation;

	public RedirRequestFilter(HttpRequest originalRequest, ChannelHandlerContext ctx, IActionRedirect action) {
		super(originalRequest, ctx);
		mLocation = action.prepareAnswer();
	}

	@Override
	public HttpResponse clientToProxyRequest(HttpObject httpObject) {
		HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
		HttpHeaders.setHeader(response, HttpHeaders.Names.LOCATION, mLocation);
		HttpHeaders.setHeader(response, Names.CONNECTION, Values.CLOSE);
		return response;
	}
}

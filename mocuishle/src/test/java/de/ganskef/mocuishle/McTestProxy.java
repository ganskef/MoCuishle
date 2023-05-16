package de.ganskef.mocuishle;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy.Type;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.littleshoot.proxy.ActivityTracker;
import org.littleshoot.proxy.ActivityTrackerAdapter;
import org.littleshoot.proxy.FlowContext;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.cache.McCache;
import de.ganskef.mocuishle.main.JavaPlatform;
import de.ganskef.mocuishle.proxy.McHttpFiltersSource;
import de.ganskef.test.IProxy;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;

public class McTestProxy implements IProxy, IPlatform {

	private static final Logger log = LoggerFactory.getLogger(McTestProxy.class);

	private HttpProxyServer server;

	protected final int proxyPort;

	public McTestProxy() {
		this(9091);
	}

	@Override
	public int getProxyPort() {
		return proxyPort;
	}

	public McTestProxy(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public McTestProxy start() {
		if (server != null) {
			server.stop();
		}
		server = bootstrap().start();
		return this;
	}

	protected HttpProxyServerBootstrap bootstrap() {
		final ICache cache = new McCache(this);
		HttpFiltersSource filtersSource = new McHttpFiltersSource(cache);
		ActivityTracker activityTracker = new ActivityTrackerAdapter() {
			@Override
			public void bytesSentToClient(FlowContext flowContext, int numberOfBytes) {
				log.warn("Bytes sent to client {}", numberOfBytes);
			}

			@Override
			public void responseSentToClient(FlowContext flowContext, HttpResponse httpResponse) {
				log.warn("Response sent to client {}", httpResponse);
				if (httpResponse instanceof HttpContent) {
					HttpContent content = (HttpContent) httpResponse;
					ByteBuf buffer = content.content();
					log.warn("Response sent to client {} {}", buffer.capacity(), buffer.nioBufferCount());
				}
			}
		};
		HttpProxyServerBootstrap b = DefaultHttpProxyServer.bootstrap() //
				.plusActivityTracker(activityTracker) //
				.withFiltersSource(filtersSource) //
				// .withFiltersSource(new HttpFiltersSourceAdapter()) //
				.withPort(proxyPort) //
				.withName(IPlatform.APPLICATION_NAME);
		return b;
	}

	@Override
	public File getHttpSpoolDir() {
		File file = new File(System.getProperty("user.dir"),
				String.format("target/%s/http", IPlatform.APPLICATION_NAME));
		file.mkdirs();
		return file;
	}

	@Override
	public File getHttpsSpoolDir() {
		File file = new File(System.getProperty("user.dir"),
				String.format("target/%s/https", IPlatform.APPLICATION_NAME));
		file.mkdirs();
		return file;
	}

	public void stop() {
		server.stop();
	}

	public java.net.Proxy getHttpProxySettings() {
		InetSocketAddress isa = server.getListenAddress();
		return new java.net.Proxy(Type.HTTP, isa);
	}

	@Override
	public ConnectionState getConnectionState() {
		if (JavaPlatform.isConnected()) {
			return ConnectionState.FULL;
		}
		return ConnectionState.OFFLINE;
	}

	@Override
	public boolean isOfflineDefault() {
		return false;
	}

	@Override
	public boolean isChainedProxy() {
		return false;
	}

	@Override
	public int getChainedProxyPort() {
		throw new IllegalStateException();
	}

	@Override
	public String getChainedProxyAddress() {
		throw new IllegalStateException();
	}

	@Override
	public int getMaximumBufferSizeInBytes() {
		return 10 * 1024 * 1024;
	}

	@Override
	public IFullTextIndex getFullTextIndex(ICache cache) {
		throw new IllegalStateException();
	}

	@Override
	public IFullTextSearch getFullTextSearch(ICache cache) {
		throw new IllegalStateException();
	}

	@Override
	public Path resolve(String name) {
		return Paths.get(System.getProperty("user.dir")).resolve("target").resolve(IPlatform.APPLICATION_NAME)
				.resolve(name);
	}
}

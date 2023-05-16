package de.ganskef.mocuishle.proxy;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Queue;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.DefaultHostResolver;
import org.littleshoot.proxy.HostResolver;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.MitmManager;
import org.littleshoot.proxy.TransportProtocol;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.mitm.Authority;
import org.littleshoot.proxy.mitm.HostNameMitmManager;
import org.littleshoot.proxy.mitm.RootCertificateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.cache.McCache;
import io.netty.handler.codec.http.HttpRequest;

public class McProxy {

	private static final String READY_MESSAGE = "Startup done";

	private static final Logger log = LoggerFactory.getLogger(McProxy.class);

	private final IPlatform mPlatform;

	private HttpProxyServer mServer;

	public McProxy(IPlatform platform) {
		mPlatform = platform;
	}

	public void start() throws RootCertificateException {
		int proxyPort = mPlatform.getProxyPort();
		log.info("Starting proxy server with port {} ...", proxyPort);

		final ICache cache = new McCache(mPlatform);
		final HttpFiltersSource filtersSource = new McHttpFiltersSource(cache);
		HttpProxyServerBootstrap b = DefaultHttpProxyServer.bootstrap() //
				.withAllowLocalOnly(true) //
				.withFiltersSource(filtersSource) //
				.withPort(proxyPort) //
				.withName(IPlatform.APPLICATION_NAME);
		if (mPlatform.isChainedProxy()) {
			log.debug("Adding chained proxy...");
			b.withChainProxyManager(createChainProxyManager());
		}
		b.withTransportProtocol(TransportProtocol.TCP);
		HostResolver serverResolver = new DefaultHostResolver() {
			@Override
			public InetSocketAddress resolve(String host, int port) throws UnknownHostException {
				if (cache.isConnectionLimited()) {
					InetSocketAddress
					// isa = InetSocketAddress.createUnresolved(host, port);
					isa = new InetSocketAddress(host, port);
					return isa;
				}
				log.debug("resolve host {} {}", host, port);
				return super.resolve(host, port);
			}
		};
		b.withServerResolver(serverResolver);

		try {
			b.withManInTheMiddle(createMitmManager());
		} catch (Exception e) {
			log.warn("MITM disabled, could not be initialized.", e);
		}
		mServer = b.start();
		log.info(READY_MESSAGE);
	}

	private MitmManager createMitmManager() throws RootCertificateException {
		File keyStoreDir = mPlatform.getHttpSpoolDir().getParentFile();
		String alias = "mocuishle";
		char[] password = "Be Your Own Lantern".toCharArray();
		String commonName = "Proxy for offline use";
		String organization = "Mo Cuishle";
		String organizationalUnitName = "Certificate Authority";
		String certOrganization = organization;
		String certOrganizationalUnitName = "Offline Cache";
		Authority authority = new Authority(keyStoreDir, alias, password, commonName, organization,
				organizationalUnitName, certOrganization, certOrganizationalUnitName);
		return new HostNameMitmManager(authority);
	}

	private ChainedProxyManager createChainProxyManager() {
		final int port = mPlatform.getChainedProxyPort();
		final String host = mPlatform.getChainedProxyAddress();
		log.info(" (with chained proxy at {}:{})", host, port);
		ChainedProxyManager chainProxyManager = new ChainedProxyManager() {
			public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {
				chainedProxies.add(new ChainedProxyAdapter() {
					@Override
					public InetSocketAddress getChainedProxyAddress() {
						return new InetSocketAddress(host, port);
					}
				});
				// No fallback, this should fail, if not available.
				// chainedProxies.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION);
			}
		};
		return chainProxyManager;
	}

	public void stop() {
		if (mServer != null) {
			mServer.abort();
		}
	}
}

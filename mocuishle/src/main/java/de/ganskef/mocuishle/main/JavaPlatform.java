package de.ganskef.mocuishle.main;

import java.io.File;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.IFullTextIndex;
import de.ganskef.mocuishle.IFullTextSearch;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.sqlite.FullTextIndex;
import de.ganskef.mocuishle.sqlite.FullTextSearch;

public class JavaPlatform implements IPlatform {

	private static final Path APPLICATION_PATH = Paths.get(System.getProperty("user.home", "."))
			.resolve(IPlatform.APPLICATION_NAME);

	@Override
	public Path resolve(String name) {
		Path path = APPLICATION_PATH.resolve(name).normalize();
		if (path.startsWith(APPLICATION_PATH)) {
			return path;
		}
		throw new IllegalStateException("Illegal path: " + name);
	}

	@Override
	public File getHttpSpoolDir() {
		return resolve("http").toFile();
	}

	@Override
	public File getHttpsSpoolDir() {
		return resolve("https").toFile();
	}

	@Override
	public ConnectionState getConnectionState() {
		if (isConnected()) {
			return ConnectionState.FULL;
		}
		return ConnectionState.OFFLINE;
	}

	/**
	 * Determines if there is a network interface which is not off line.
	 *
	 * <p>
	 * This code is taken from LittleProxy org.littleshoot.proxy.impl.NetworkUtils
	 */
	public static final boolean isConnected() {
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = networkInterfaces.nextElement();
				try {
					if (networkInterface.isUp()) {
						for (InterfaceAddress ifAddress : networkInterface.getInterfaceAddresses()) {
							if (ifAddress.getNetworkPrefixLength() > 0 && ifAddress.getNetworkPrefixLength() <= 32
									&& !ifAddress.getAddress().isLoopbackAddress()) {
								return true;
							}
						}
					}
				} catch (SocketException ignored) {
					// try next interface
				}
			}
		} catch (SocketException ignored) {
			// assume to be off line
		}
		return false;
	}

	@Override
	public boolean isOfflineDefault() {
		return false;
	}

	@Override
	public int getProxyPort() {
		return 9090;
	}

	@Override
	public boolean isChainedProxy() {
		return false;
	}

	@Override
	public int getChainedProxyPort() {
		return 8228;
	}

	@Override
	public String getChainedProxyAddress() {
		return "localhost";
	}

	@Override
	public int getMaximumBufferSizeInBytes() {
		return 10 * 1024 * 1024;
	}

	public boolean isUnmodifiedOnlineContent() {
		return false;
	}

	@Override
	public IFullTextIndex getFullTextIndex(ICache cache) {
		return new FullTextIndex(cache);
	}

	@Override
	public IFullTextSearch getFullTextSearch(ICache cache) {
		return new FullTextSearch(cache);
	}
}

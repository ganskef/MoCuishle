package de.ganskef.mocuishle;

import java.io.File;
import java.nio.file.Path;

public interface IPlatform {

	String APPLICATION_NAME = "MoCuishle";

	File getHttpSpoolDir();

	File getHttpsSpoolDir();

	/** The availability of the network connection */
	enum ConnectionState {
		FULL, LIMITED, OFFLINE
	}

	/** Returns the quality of the network connection */
	ConnectionState getConnectionState();

	boolean isOfflineDefault();

	int getProxyPort();

	boolean isChainedProxy();

	int getChainedProxyPort();

	String getChainedProxyAddress();

	int getMaximumBufferSizeInBytes();

	IFullTextIndex getFullTextIndex(ICache cache);

	IFullTextSearch getFullTextSearch(ICache cache);

	Path resolve(String name);
}

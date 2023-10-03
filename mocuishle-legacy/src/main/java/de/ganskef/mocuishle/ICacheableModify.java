package de.ganskef.mocuishle;

public interface ICacheableModify extends ITouched {

	boolean isCached(String href);

	boolean isRequested(String href);

	String toUrl(String href);

	void recordBrowse(String title);

	/**
	 * Returns the base URL of the proxy server as part of the special URL commands.
	 */
	String getLocalUrl();

	/** Return the host name of the URL of the modified item. */
	String getHostName();

	/** Return true, only if the response was status 200 OK. */
	boolean isOkStatus();

	String getUrl();
}

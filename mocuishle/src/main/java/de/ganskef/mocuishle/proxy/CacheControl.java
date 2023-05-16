package de.ganskef.mocuishle.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidate debug output to simpler defining a logger
 *
 * <p>
 * XXX obsolete used only in one class, consider to inline the methods
 */
public final class CacheControl {

	private static final Logger log = LoggerFactory.getLogger(CacheControl.class);

	public static void logHeaderCacheControlIsNoCache() {
		log.info("Header cache control is no-cache, refresh is requested.");
	}

	public static void logHeaderCacheControlIsMaxAgeZero() {
		log.info("Header cache control is max-age=0, refresh is requested.");
	}

	public static void logHeaderPragmaIsNoCache() {
		log.info("Header pragma is no-cache, refresh is requested.");
	}

	public static void logNoRefreshHeaderFound() {
		log.debug("No header found to force a refresh.");
	}
}

package de.ganskef.mocuishle.cache;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;

import de.ganskef.mocuishle.ICache.BrowseDoc;
import de.ganskef.mocuishle.ICacheableProxy.CachedResponse;

public interface IStore {

	String HISTORY_TIMESTAMP_NAME = "StoreHistory-timestamp";

	File getSpoolDir(String folderName);

	String getHashedName(String uri);

	void moveDir(File targetDir, File sourceDir);

	void deletePageDirectory(File trashDir);

	void movePage(File targetFile, File sourceDir);

	File getSpoolDir(String folderName, String hostName);

	String readFileToString(File file);

	File getPageUrlFile(String hostName, File browseFile);

	CharBuffer decodeUTF8(ByteBuffer in) throws CharacterCodingException;

	/**
	 * Read contents from cache to present in the UI. Updates last requested.
	 * Introduced by the history navigation UI features.
	 */
	BrowseDoc createBrowseDoc(String uri);

	/**
	 * Read contents from cache without updating the requested date. Introduced by
	 * export UI feature.
	 */
	CachedResponse createCachedResponse(String uri);

	/**
	 * Indicates a read only cache collecting used cache entries to invalidate
	 * obsoletes.
	 */
	boolean isCacheOnly();

	/** Checks if an URL is marked as validated already. */
	boolean isValidated(String url);

	/** Create a temporary marker file to enable interruption and restart. */
	void markValidated(String url);

	/** Clean cache after validation. */
	void purgeInvalidateds();
}

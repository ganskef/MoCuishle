package de.ganskef.mocuishle;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import de.ganskef.mocuishle.cache.McElement;
import de.ganskef.mocuishle.proxy.McHttpHeaders;

public interface ICacheableProxy {

	public enum Alerts {
		NOT_STORED, REC_BROWSED_FAILED
	}

	/** Returns true, if the URL of this request is not cached by the proxy. */
	boolean isIgnored();

	/** The request data will be recorded to fetch it later. */
	// FIXME rename to record outgoing
	void recordForDownload(String method, String protocolVersion, Iterable<Entry<String, String>> headers);

	/** Remove outgoing file if resumed interactive online or with fetch */
	// FIXME rename to clear outgoing
	void deleteOutgoing();

	/**
	 * Returns true, if not in cache while offline to avoid blocking.
	 *
	 * <p>
	 * TODO consider to inline into cacheable
	 */
	boolean needBadGateway();

	/**
	 * Returns true, if a cached response should be created.
	 *
	 * <p>
	 * TODO consider to inline into cacheable
	 *
	 * @param refresh
	 */
	boolean needCached(boolean refresh);

	/** TODO consider to inline into cacheable */
	ByteBuffer getModifiedTextHtml(ByteBuffer content, boolean online);

	/** Result object to retrieve response data from cache. */
	public static class CachedResponse {

		private static final char CR = 13, LF = 10;

		private int mStatusCode;

		private ByteBuffer mContent;

		private Iterable<Map.Entry<String, String>> mHeaders;

		private long mLoadedDate;

		private File mFile;

		public CachedResponse(int statusCode, Iterable<Map.Entry<String, String>> headers, ByteBuffer content,
				long lastModified, File file) {
			mStatusCode = statusCode;
			mHeaders = headers;
			mContent = content;
			mLoadedDate = lastModified;
			mFile = file;
		}

		public int getStatusCode() {
			return mStatusCode;
		}

		public Iterable<Map.Entry<String, String>> getHeaders() {
			return mHeaders;
		}

		private void readMappedBuffer() {
			try (RandomAccessFile input = new RandomAccessFile(mFile, "r")) {
				FileChannel ch = input.getChannel();
				ByteBuffer content = ByteBuffer.allocate(4096);
				ch.read(content);
				content.flip();
				Collection<Entry<String, String>> headers = new HashSet<Map.Entry<String, String>>();
				int rc = McElement.readHeaders(content, headers);
				int offset = content.position();
				content.clear();
				long size = input.length() - offset;
				/*
				 * A mapped file could not be deleted on Windows:
				 *
				 * This is a known Bug in Java on Windows, please see Bug #4715154
				 *
				 * Sun evaluated the problem and closed the bug with the following explanation:
				 *
				 * We cannot fix this. Windows does not allow a mapped file to be deleted. This
				 * problem should be ameliorated somewhat once we fix our garbage collectors to
				 * deallocate direct buffers more promptly (see 4469299), but otherwise there's
				 * nothing we can do about this.
				 *
				 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154
				 */
				mContent = ch.map(MapMode.READ_ONLY, offset, size).load();
				mStatusCode = rc;
				mHeaders = headers;
			} catch (BufferUnderflowException | IOException e) {
				mContent = ByteBuffer.allocate(0);
			}
		}

		private void readStreamedBuffer() {
			try (DataInputStream dis = new DataInputStream(new FileInputStream(mFile))) {
				// Always read the whole file in rather than using memory mapping.
				// Windows' file system semantics also mean that there's a period after a search
				// finishes but before the buffer is actually unmapped where you can't write to
				// the file (see Sun bug 6359560).
				// Being unable to manually unmap causes no functional problems but hurts
				// performance on Unix (see Sun bug 4724038). ...
				//
				// On Linux mapped buffer is 10% faster in my tests exporting a ZIP of many
				// files from the cache. (www.google.com -> 250MB 38 vs. 43 seconds).
				if (mFile.length() > Integer.MAX_VALUE) {
					throw new IOException("Very large file: " + mFile.length() + " bytes");
				}
				int byteCount = (int) mFile.length();
				final byte[] bytes = new byte[byteCount];
				dis.readFully(bytes);
				int offset = -1;
				for (int i = 0, j = 1, k = 2, l = 3; l < byteCount; i++, j++, k++, l++) {
					if (bytes[i] == CR && bytes[j] == LF && bytes[k] == CR && bytes[l] == LF) {
						offset = l + 1;
						break;
					}
				}
				mContent = ByteBuffer.wrap(bytes, offset, byteCount - offset);
			} catch (IOException e) {
				mContent = ByteBuffer.allocate(0);
			}
		}

		public ByteBuffer getContent() {
			if (mContent != null) {
				return mContent;
			}
			if (mFile == null) {
				return ByteBuffer.allocate(0);
			}
			if (File.separatorChar == '/') {
				readMappedBuffer();
			} else {
				readStreamedBuffer();
			}
			return mContent;
		}

		public long getLoadedDate() {
			return mLoadedDate;
		}

		public boolean isTextHtml() {
			return McHttpHeaders.isContentTypeTextHtml(mHeaders);
		}

		public String getContentType() {
			return McHttpHeaders.getContentType(mHeaders);
		}
	}

	/** Store response data of this URL into the cache. */
	void store(int stateCode, String stateLine, Iterable<Entry<String, String>> headers, ByteBuffer buffer);

	/** Read response data from cache and update the last viewed time stamp. */
	CachedResponse readCachedResponseToView();

	/** Returns response data from cache. */
	CachedResponse getCachedResponse();

	/** An alert should trigger a user notification in the browsed html. */
	void addAlert(Alerts alert);

	/**
	 * Returns last modified of the cached element as the time of the response or 0L
	 * if the element is not found in cache.
	 */
	long lastModified();

	boolean isDocumentExtension();

	boolean isNocontentExtension();

	String getProxyHome();
}

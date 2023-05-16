package de.ganskef.mocuishle;

import java.io.File;
import java.util.Date;
import java.util.Iterator;

import de.ganskef.mocuishle.cache.History;
import de.ganskef.mocuishle.cache.Outgoing;
import de.ganskef.mocuishle.cache.ValidateIterator;

/**
 * Interface used by features to refer to the cache. It should never be a cache
 * package fragment imported in an other package.
 */
public interface ICache {

	public enum PageMode {
		BROWSE, MORE, TRASH;

		public static PageMode forCommand(String command) {
			String upperCase = command.toUpperCase();
			for (ICache.PageMode each : ICache.PageMode.values()) {
				if (upperCase.startsWith(each.name())) {
					return each;
				}
			}
			throw new IllegalArgumentException(command);
		}
	}

	ICacheable createElement(String uri);

	boolean isConnectionLimited();

	Date getDateModified(String url);

	int getMaximumBufferSizeInBytes();

	/** A host item displayed in listings. */
	public static class BrowseHost {

		private final String mHostName;

		private final boolean mDeleted;

		private final long mLastModified;

		public BrowseHost(String hostName, long lastModified, boolean deleted) {
			mHostName = hostName;
			mLastModified = lastModified;
			mDeleted = deleted;
		}

		@Override
		public String toString() {
			return mHostName;
		}

		public String getHostName() {
			return mHostName;
		}

		public boolean isDeleted() {
			return mDeleted;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((mHostName == null) ? 0 : mHostName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			BrowseHost other = (BrowseHost) obj;
			if (mHostName == null) {
				if (other.mHostName != null) {
					return false;
				}
			} else if (!mHostName.equals(other.mHostName)) {
				return false;
			}
			return true;
		}

		public long lastModified() {
			return mLastModified;
		}
	}

	public static class BrowsePage {

		private final String mUrl;

		private final String mTitle;

		private final long mLastModified;

		private final boolean mDeleted;

		public BrowsePage(String url, String title, long lastModified, boolean deleted) {
			mUrl = url;
			mTitle = title;
			mLastModified = lastModified;
			mDeleted = deleted;
		}

		public String getUrl() {
			return mUrl;
		}

		public String getTitle() {
			return mTitle;
		}

		public boolean isDeleted() {
			return mDeleted;
		}

		public long lastModified() {
			return mLastModified;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((mUrl == null) ? 0 : mUrl.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			BrowsePage other = (BrowsePage) obj;
			if (mUrl == null) {
				if (other.mUrl != null) {
					return false;
				}
			} else if (!mUrl.equals(other.mUrl)) {
				return false;
			}
			return true;
		}
	}

	void removeBlocked(String host);

	void addBlocked(String host);

	boolean isBlocked(String host);

	/* extract class History */ @Deprecated
	void deleteBrowsePath(String scheme, String hostName, String path);

	/* extract class History */ @Deprecated
	void undeleteBrowsePath(String scheme, String hostName, String path);

	/* extract class History */ @Deprecated
	void emptyTrash();

	Markup getMarkup();

	boolean isOffline();

	public static class BrowseDoc {

		private final String mHash;

		private final CharSequence mContent;

		public BrowseDoc(String hash, CharSequence content) {
			mHash = hash;
			mContent = content;
		}

		public String getHash() {
			return mHash;
		}

		public CharSequence getContent() {
			return mContent;
		}
	}

	Iterator<BrowseDoc> browseDocIterator();

	IFullTextSearch getFullTextSearch();

	String getUrl(BrowseDoc doc);

	File getWritableDir();

	IFullTextIndex getFullTextIndex();

	/* extract class Outgoing */ @Deprecated
	Iterator<String> outgoingUriIterator(PageMode mode);

	/* extract class Outgoing */ @Deprecated
	boolean isDeletedOutgoing(String uri);

	/* extract class Outgoing */ @Deprecated
	void deleteOutgoing(String uri);

	/* extract class Outgoing */ @Deprecated
	void undeleteOutgoing(String uri);

	/* extract class Outgoing */ @Deprecated
	void emptyTrashOutgoing();

	/* extract class Outgoing */ @Deprecated
	void fetchOutgoingUris();

	/**
	 * Proxy network connection mode to set by a UI feature. AUTOMATIC is the
	 * default behavior to switch between full and mobile connections. FLATERATE is
	 * full if mobile, TETHERING is mobile if full, CACHEONLY is always off.
	 */
	public enum State {
		FLATRATE, AUTOMATIC, TETHERING, CACHEONLY;
	}

	/**
	 * Set the mode for the proxy's network connection handling. Introduced by a UI
	 * feature.
	 */
	State setState(State newState);

	/**
	 * Returns the mode for the proxy's network connection handling. Introduced by a
	 * UI feature.
	 */
	State getState();

	/** Introduced by extract class History from Cache refactoring. */
	History getHistory();

	/** Inroduced by extract class Outgoing from Cache refactoring. */
	Outgoing getOutgoing();

	/**
	 * Export cache contents of the given path to the file system. Introduced by a
	 * UI feature.
	 *
	 * @return the absolute file system path of the exported ZIP archive
	 */
	String export(String path);

	/**
	 * Introduced to avoid filling the file system with useless data on streaming or
	 * downloading.
	 */
	boolean isPassed(String url);

	/** Introduced to include pages without title or images to the index. */
	boolean isIndex(String url);

	/**
	 * Introduced to avoid filling the file system with useless data on streaming or
	 * downloading.
	 */
	void addPassed(String definition);

	/**
	 * Introduced to avoid filling the file system with useless data on streaming or
	 * downloading.
	 */
	void removePassed(String address);

	/**
	 * Iterator of URLs to be opened in a browser to validate cache entries. Used
	 * entries will be marked and unused can be deleted after completion.
	 */
	ValidateIterator validateIterator();
}

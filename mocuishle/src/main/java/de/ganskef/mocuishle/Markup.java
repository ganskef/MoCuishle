package de.ganskef.mocuishle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.cache.McElement;
import de.ganskef.mocuishle.modify.HtmlModifier;

/**
 * Templates to create output ot the user interface, stored in resources in the
 * JAR, could be overridden with text files in a directory with custom files.
 */
public class Markup {

	private static final Logger LOG = LoggerFactory.getLogger(Markup.class);

	private final File mConfigDir;

	private final Map<String, String> resources = new HashMap<>();

	private final Modify mModify;

	private final Browse mBrowse;

	private final Search mSearch;

	private final Outgoing mOutgoing;

	private final Welcome mWelcome;

	private final Locale mLocale;

	public String getResource(String name) {
		if (resources.containsKey(name)) {
			return resources.get(name);
		}
		String resource = load(name);
		resources.put(name, resource);
		return resource;
	}

	public Object loaded(ITouched cached) {
		return xmlEncoded(new LoadedFormat(cached));
	}

	public static Object urlEncoded(Object input) {
		return xmlEncoded(new UrlEncoded(input));
	}

	public static Object urlDecoded(Object input) {
		return xmlEncoded(new UrlDecoded(input));
	}

	public static Object truncated(Object input, int beginIndex) {
		return xmlEncoded(new Truncated(input, beginIndex, 20));
	}

	public static Object truncated(Object input, int beginIndex, int breakIndex) {
		return xmlEncoded(new Truncated(input, beginIndex, breakIndex));
	}

	public static Object xmlEncoded(Object input) {
		return new XmlEncoded(input);
	}

	public static Object xmlDecoded(Object input) {
		return new XmlDecoded(input);
	}

	public static class Truncated {

		private final Object mInput;
		private final int mBeginIndex;
		private final int mBreakIndex;

		private Truncated(Object input, int beginIndex, int breakIndex) {
			mInput = input;
			mBeginIndex = beginIndex;
			mBreakIndex = breakIndex;
		}

		@Override
		public String toString() {
			String input = mInput.toString();
			return truncate(input, mBeginIndex, mBreakIndex);
		}

		public static final String truncate(String input, int beginIndex, int breakIndex) {
			int length = input.length();
			if (beginIndex >= length) {
				return input;
			}
			int max = breakIndex + 10;
			if (length - max - beginIndex < 0) {
				return input.substring(beginIndex);
			}
			String prefix = input.substring(beginIndex, beginIndex + breakIndex);
			String suffix = input.substring(length - 7, length);
			return prefix + "..." + suffix;
		}
	}

	public static class XmlEncoded {

		private final Object mInput;

		private XmlEncoded(Object input) {
			mInput = input;
		}

		@Override
		public String toString() {
			String input = mInput.toString();
			return input.replaceAll("&amp;", "&").replaceAll("&", "&amp;").replaceAll("\"", "&quot;")
					.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
		}
	}

	public static class XmlDecoded {

		private final Object mInput;

		private XmlDecoded(Object input) {
			mInput = input;
		}

		@Override
		public String toString() {
			String input = mInput.toString();
			return input.replaceAll("&gt;", ">").replaceAll("<", "&lt;").replaceAll("&quot;", "\"").replaceAll("&amp;",
					"&");
		}
	}

	public static class UrlEncoded {

		private final Object mInput;

		private UrlEncoded(Object input) {
			mInput = input;
		}

		@Override
		public String toString() {
			String input = String.valueOf(mInput);
			try {
				return URLEncoder.encode(input, "UTF-8").replace(' ', '+');
			} catch (UnsupportedEncodingException e) {
				LOG.error("Failed to edcode URL for " + mInput, e);
				return input;
			}
		}
	}

	public static class UrlDecoded {

		private final Object mInput;

		private UrlDecoded(Object input) {
			mInput = input;
		}

		@Override
		public String toString() {
			String input = String.valueOf(mInput);
			try {
				return URLDecoder.decode(input, "UTF-8");
			} catch (IllegalArgumentException | UnsupportedEncodingException e) {
				LOG.error("Failed to decode URL for " + mInput, e);
				return input;
			}
		}
	}

	public class LoadedFormat {

		private static final int YEARS = 0;
		private static final int WEEKS = 4;
		private static final int DAYS = 6;
		private static final int HOURS = 8;
		private static final int MINUTES = 10;
		private static final int SECONDS = 12;
		private static final int ONLINE = 14;

		private final ITouched mTouched;

		private LoadedFormat(ITouched touched) {
			mTouched = touched;
		}

		@Override
		public String toString() {
			long touched = mTouched.lastModified();
			if (touched == 0) {
				return "";
			}
			long ms = System.currentTimeMillis() - touched;
			float age;
			if (ms >= 365L / 2L * 24L * 60L * 60L * 1000L) {
				age = (float) (ms / 365L / 24L / 60L / 60L / 100L) / 10F;
				return format(YEARS, age);
			}
			if (ms >= 7L * 24L * 60L * 60L * 1000L) {
				age = (float) (ms / 7L / 24L / 60L / 60L / 100L) / 10F;
				return format(WEEKS, age);
			}
			if (ms >= 12L * 60L * 60L * 1000L) {
				age = (float) (ms / 24L / 60L / 60L / 100L) / 10F;
				return format(DAYS, age);
			}
			if (ms >= 30L * 60L * 1000L) {
				age = (float) (ms / 60L / 60L / 100L) / 10F;
				return format(HOURS, age);
			}
			if (ms >= 12L * 60L * 60L * 1000L) {
				age = (float) (ms / 60L / 60L / 100L) / 10F;
				return format(HOURS, age);
			}
			if (ms >= 30L * 1000L) {
				age = (float) (ms / 60L / 100L) / 10F;
				return format(MINUTES, age);
			}
			if (ms >= 1000L) {
				age = ms / 1000L;
				return format(SECONDS, age);
			}
			return format(ONLINE, 0);
		}

		private String format(int index, float age) {
			Modify def = mTouched.getMarkup().getModify();
			String[] formats = def.getLoadedFormats();
			if (index >= formats.length) {
				return "";
			}
			if (index == ONLINE) {
				return formats[ONLINE];
			}
			String[] singles = def.getLoadedSingles();
			String result = String.format(mLocale, formats[index], age);
			if (singles[index].equals(result)) {
				return formats[index + 1];
			}
			return result;
		}
	}

	public class Modify {
		private final String mHeadBegin;
		private final String mHeadEnd;
		private final String mBodyBegin;
		private final String mBodyEnd;
		private final String mCached;
		private final String mRequested;
		private final String mNotCached;
		private final String mColorCached;
		private final String mColorRequested;
		private final String mColorNotCached;
		private final String[] mLoadedFormats;
		private final String[] mLoadedSingles;

		public Modify() {
			mHeadBegin = load("MODIFY_HEAD_BEGIN.txt");
			mHeadEnd = load("MODIFY_HEAD_END.txt");
			mBodyBegin = load("MODIFY_BODY_BEGIN.txt");
			mBodyEnd = load("MODIFY_BODY_END.txt");
			mColorCached = load("MODIFY_COLOR_CACHED.txt");
			mColorRequested = load("MODIFY_COLOR_REQUESTED.txt");
			mColorNotCached = load("MODIFY_COLOR_NOT_CACHED.txt");
			mCached = String.format(load("MODIFY_ANCHOR_CACHED.txt"), mColorCached);
			mRequested = String.format(load("MODIFY_ANCHOR_REQUESTED.txt"), mColorRequested);
			mNotCached = String.format(load("MODIFY_ANCHOR_NOT_CACHED.txt"), mColorNotCached);
			mLoadedFormats = load("MODIFY_LOADED_FORMATS.txt").split("(?:\r\n|\r|\n)");
			mLoadedSingles = new String[mLoadedFormats.length];
			for (int i = 0; i < mLoadedSingles.length; i += 2) {
				mLoadedSingles[i] = String.format(mLoadedFormats[i], 1f);
			}
		}

		public boolean hasHeadBegin() {
			return mHeadBegin.length() == 0;
		}

		public String getHeadBegin() {
			return mHeadBegin;
		}

		public boolean hasHeadEnd() {
			return mHeadEnd.length() == 0;
		}

		public String getHeadEnd() {
			return mHeadEnd;
		}

		public boolean hasBodyBegin() {
			return mBodyBegin.length() == 0;
		}

		public String getBodyBegin() {
			return mBodyBegin;
		}

		public boolean hasBodyEnd() {
			return mBodyEnd.length() == 0;
		}

		public String getBodyEnd() {
			return mBodyEnd;
		}

		public boolean hasCached() {
			return mCached.length() == 0;
		}

		public String getCached() {
			return mCached;
		}

		public boolean hasRequested() {
			return mRequested.length() == 0;
		}

		public String getRequested() {
			return mRequested;
		}

		public boolean hasNotCached() {
			return mNotCached.length() == 0;
		}

		public String getNotCached() {
			return mNotCached;
		}

		public String getColorCached() {
			return mColorCached;
		}

		public String getColorNotCached() {
			return mColorNotCached;
		}

		public String getColorRequested() {
			return mColorRequested;
		}

		public String[] getLoadedFormats() {
			return mLoadedFormats;
		}

		public String[] getLoadedSingles() {
			return mLoadedSingles;
		}
	}

	public class Browse {

		private final String mSwitchQuick;
		private final String mSwitchAll;
		private final String mHostsDeleted;
		private final String mHostsElement;
		private final String mTrashFull;
		private final String mTrashEmpty;
		private final String mHosts;
		private final String mPagesDeleted;
		private final String mPagesElement;
		private final String mPages;
		private final String mCurrentsBlocked;
		private final String mCurrentsPassed;
		private final String mCurrentsElement;
		private final String mCurrentsTable;
		private final String mEmptyTrashFull;
		private final String mEmptyTrashEmpty;

		public Browse() {
			mSwitchQuick = load("BROWSE_SWITCH_QUICK.txt");
			mSwitchAll = load("BROWSE_SWITCH_ALL.txt");
			mHostsDeleted = load("BROWSE_HOSTS_DELETED.txt");
			mHostsElement = load("BROWSE_HOSTS_ELEMENT.txt");
			mTrashFull = load("BROWSE_TRASH_FULL.txt");
			mTrashEmpty = load("BROWSE_TRASH_EMPTY.txt");
			mHosts = load("BROWSE_HOSTS.txt");
			mPagesDeleted = load("BROWSE_PAGES_DELETED.txt");
			mPagesElement = load("BROWSE_PAGES_ELEMENT.txt");
			mPages = load("BROWSE_PAGES.txt");
			mCurrentsBlocked = load("BROWSE_CURRENTS_BLOCKED.txt");
			mCurrentsPassed = load("BROWSE_CURRENTS_PASSED.txt");
			mCurrentsElement = load("BROWSE_CURRENTS_ELEMENT.txt");
			mCurrentsTable = load("BROWSE_CURRENTS_TABLE.txt");
			mEmptyTrashFull = load("BROWSE_EMPTY_TRASH_FULL.txt");
			mEmptyTrashEmpty = load("BROWSE_EMPTY_TRASH_EMPTY.txt");
		}

		public String getSwitchQuick() {
			return mSwitchQuick;
		}

		public String getSwitchAll() {
			return mSwitchAll;
		}

		public String getHostsDeleted() {
			return mHostsDeleted;
		}

		public String getHostsElement() {
			return mHostsElement;
		}

		public String getTrashFull() {
			return mTrashFull;
		}

		public String getTrashEmpty() {
			return mTrashEmpty;
		}

		public String getHosts() {
			return mHosts;
		}

		public String getPagesDeleted() {
			return mPagesDeleted;
		}

		public String getPagesElement() {
			return mPagesElement;
		}

		public String getPages() {
			return mPages;
		}

		public String getCurrentsBlocked() {
			return mCurrentsBlocked;
		}

		public String getCurrentsPassed() {
			return mCurrentsPassed;
		}

		public String getCurrentsElement() {
			return mCurrentsElement;
		}

		public String getCurrentsTable() {
			return mCurrentsTable;
		}

		public String getEmptyTrashFull() {
			return mEmptyTrashFull;
		}

		public String getEmptyTrashEmpty() {
			return mEmptyTrashEmpty;
		}
	}

	public Markup(IPlatform platform) {
		File wrkDir = platform.getHttpSpoolDir().getParentFile();
		mConfigDir = new File(wrkDir, "markup");
		if (!mConfigDir.exists() && !mConfigDir.mkdirs()) {
			LOG.warn("Markup dir couldn't be created {}", mConfigDir);
		}
		mModify = new Modify();
		mBrowse = new Browse();
		mSearch = new Search();
		mOutgoing = new Outgoing();
		mWelcome = new Welcome();
		mLocale = Locale.forLanguageTag(load("LANGUAGE_TAG.txt"));
	}

	public Modify getModify() {
		return mModify;
	}

	public Browse getBrowse() {
		return mBrowse;
	}

	public Search getSearch() {
		return mSearch;
	}

	public Outgoing getOutgoing() {
		return mOutgoing;
	}

	public Welcome getWelcome() {
		return mWelcome;
	}

	private String load(String name) {
		File customDir = new File(mConfigDir, "custom");
		customDir.mkdirs();
		File customized = new File(customDir, name);
		if (customized.exists()) {
			if (customized.canRead() && customized.isFile()) {
				try {
					return FileUtils.readFileToString(customized, McElement.DEFAULT_ENCODING);
				} catch (IOException e) {
					LOG.error("Couldn't read " + customized, e);
				}
			}
			return loadResource(name);

		} else {
			String loadResource = loadResource(name);
			File configFile = new File(mConfigDir, name);
			try {
				FileUtils.write(configFile, loadResource, McElement.DEFAULT_ENCODING);
			} catch (IOException e) {
				LOG.error("Couldn't write " + configFile, e);
			}
			return loadResource;
		}
	}

	private String loadResource(String name) {
		InputStream is = HtmlModifier.class.getResourceAsStream('/' + name);
		if (is == null) {
			LOG.error("Resource missed /" + name);
		} else {
			try {
				return IOUtils.toString(is, McElement.DEFAULT_ENCODING);
			} catch (IOException e) {
				LOG.error("Couldn't load resource /" + name, e);
			}
		}
		return "<!-- Mo Cuishle RESSOURCE MISSED: /" + name + " -->";
	}

	public class Search {

		private final String mPages;
		private final String mPagesDeleted;
		private final String mPagesElement;
		private final String mTrashFull;
		private final String mTrashEmpty;
		private final String mSwitchAll;
		private final String mPrevEnabled;
		private final String mPrevDisabled;
		private final String mNextEnabled;
		private final String mNextDisabled;
		private final String mEmptyTrashFull;
		private final String mEmptyTrashEmpty;

		public Search() {
			mPages = load("SEARCH_PAGES.txt");
			mPagesDeleted = load("SEARCH_PAGES_DELETED.txt");
			mPagesElement = load("SEARCH_PAGES_ELEMENT.txt");
			mTrashFull = load("SEARCH_TRASH_FULL.txt");
			mTrashEmpty = load("SEARCH_TRASH_EMPTY.txt");
			mSwitchAll = load("SEARCH_SWITCH_ALL.txt");
			mPrevEnabled = load("SEARCH_PREV_ENABLED.txt");
			mPrevDisabled = load("SEARCH_PREV_DISABLED.txt");
			mNextEnabled = load("SEARCH_NEXT_ENABLED.txt");
			mNextDisabled = load("SEARCH_NEXT_DISABLED.txt");
			mEmptyTrashFull = load("BROWSE_EMPTY_TRASH_FULL.txt");
			mEmptyTrashEmpty = load("BROWSE_EMPTY_TRASH_EMPTY.txt");
		}

		public String getPages() {
			return mPages;
		}

		public String getPagesDeleted() {
			return mPagesDeleted;
		}

		public String getPagesElement() {
			return mPagesElement;
		}

		public String getTrashEmpty() {
			return mTrashEmpty;
		}

		public String getTrashFull() {
			return mTrashFull;
		}

		public String getSwitchAll() {
			return mSwitchAll;
		}

		public String getPrevEnabled() {
			return mPrevEnabled;
		}

		public String getPrevDisabled() {
			return mPrevDisabled;
		}

		public String getNextEnabled() {
			return mNextEnabled;
		}

		public String getNextDisabled() {
			return mNextDisabled;
		}

		public String getEmptyTrashFull() {
			return mEmptyTrashFull;
		}

		public String getEmptyTrashEmpty() {
			return mEmptyTrashEmpty;
		}
	}

	public class Outgoing {

		private final String mUrls;

		private final String mElement;

		private final String mDeleted;

		private final String mSwitchAll;

		private final String mSwitchQuick;

		private final String mTrashFull;

		private final String mTrashEmpty;

		private final String mEmpty;

		public Outgoing() {
			mUrls = load("OUTGOING.txt");
			mElement = load("OUTGOING_ELEMENT.txt");
			mDeleted = load("OUTGOING_DELETED.txt");
			mSwitchAll = load("OUTGOING_SWITCH_ALL.txt");
			mSwitchQuick = load("OUTGOING_SWITCH_QUICK.txt");
			mTrashFull = load("OUTGOING_TRASH_FULL.txt");
			mTrashEmpty = load("OUTGOING_TRASH_EMPTY.txt");
			mEmpty = load("OUTGOING_EMPTY.txt");
		}

		public String getTrashFull() {
			return mTrashFull;
		}

		public String getTrashEmpty() {
			return mTrashEmpty;
		}

		public String getUrls() {
			return mUrls;
		}

		public String getElement() {
			return mElement;
		}

		public String getDeleted() {
			return mDeleted;
		}

		public String getSwitchAll() {
			return mSwitchAll;
		}

		public String getSwitchQuick() {
			return mSwitchQuick;
		}

		public String getEmpty() {
			return mEmpty;
		}
	}

	public class Welcome {

		private final String mPage;

		private final String mCacheonly;

		private final String mCacheonlyDisabled;

		private final String mTethering;

		private final String mTetheringDisabled;

		private final String mAutomatic;

		private final String mAutomaticDisabled;

		private final String mFlatrate;

		private final String mFlatrateDisabled;

		public Welcome() {
			mPage = load("WELCOME.txt");
			mCacheonly = load("WELCOME_IS_CACHEONLY.txt");
			mCacheonlyDisabled = load("WELCOME_CACHEONLY.txt");
			mTethering = load("WELCOME_IS_TETHERING.txt");
			mTetheringDisabled = load("WELCOME_TETHERING.txt");
			mAutomatic = load("WELCOME_IS_AUTOMATIC.txt");
			mAutomaticDisabled = load("WELCOME_AUTOMATIC.txt");
			mFlatrate = load("WELCOME_IS_FLATRATE.txt");
			mFlatrateDisabled = load("WELCOME_FLATRATE.txt");
		}

		public String getPage() {
			return mPage;
		}

		public String getAutomatic() {
			return mAutomatic;
		}

		public String getAutomaticDisabled() {
			return mAutomaticDisabled;
		}

		public String getCacheonly() {
			return mCacheonly;
		}

		public String getCacheonlyDisabled() {
			return mCacheonlyDisabled;
		}

		public String getFlatrate() {
			return mFlatrate;
		}

		public String getFlatrateDisabled() {
			return mFlatrateDisabled;
		}

		public String getTethering() {
			return mTethering;
		}

		public String getTetheringDisabled() {
			return mTetheringDisabled;
		}
	}
}

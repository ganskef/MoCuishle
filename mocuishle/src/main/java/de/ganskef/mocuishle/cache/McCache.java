package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.ICacheableProxy.CachedResponse;
import de.ganskef.mocuishle.IFullTextIndex;
import de.ganskef.mocuishle.IFullTextSearch;
import de.ganskef.mocuishle.IPlatform;
import de.ganskef.mocuishle.IPlatform.ConnectionState;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.main.JavaPlatform;
import de.ganskef.mocuishle.util.base64.Base64;

public class McCache implements ICache, IStore {

	// TODO cache.McCache - Host is null for url:
	// /web/20110519232259/http://www.apple.com/macosx/technology/images/sidenav_coreanimation20070611.png
	private static Pattern URI_SEPARATION_PATTERN = Pattern.compile( //
			// Group 1 contains the host name
			"^https?://(.*?)(?:\\:\\d+)?/.*", //
			// Schemes are case-insensitive:
			// http://tools.ietf.org/html/rfc3986#section-3.1
			Pattern.CASE_INSENSITIVE);

	private static final Logger log = LoggerFactory.getLogger(McCache.class);

	private final IPlatform mPlatform;

	private final Markup mMarkup;

	private final Set<String> mBlockeds;

	private State mState;

	private final UrlConfig mPassedConfig, mIndexConfig;

	private ValidateIterator mValidateIterator;

	private long mLastValidation;

	public McCache(IPlatform platform) {
		mHistory = new History(this);
		mOutgoing = new Outgoing(this);
		mPlatform = platform;
		mMarkup = new Markup(mPlatform);
		if (platform.isOfflineDefault()) {
			mState = State.CACHEONLY;
		} else {
			mState = State.AUTOMATIC;
		}
		mBlockeds = new HashSet<String>(readBlockeds());
		mPassedConfig = new UrlConfig(new File(getWritableDir(), "pass.txt"),
				"(?:https?://arte\\.gl-systemhaus\\.de/.*|.*\\.googlevideo\\.com/.*|.*\\.(?:img|iso|zip|gz|tgz|bz2|7z|tar\\.xz|tar|tbz|dmg|exe|jar|war|ear|Z|mp4|mp3|flv|apk|xpi))");
		mIndexConfig = new UrlConfig(new File(getWritableDir(), "index.txt"),
				"(?:https://mediandr-a.akamaihd.net/.*\\.mp3)");
		removeTemporaryFiles();
		mLastValidation = initLastValidation();
	}

	@Override
	public State getState() {
		return mState;
	}

	@Override
	public History getHistory() {
		return mHistory;
	}

	@Override
	public Outgoing getOutgoing() {
		return mOutgoing;
	}

	private void removeTemporaryFiles() {
		long ms = System.currentTimeMillis();
		final String tmpName = getTempFilePrefix();
		log.debug("Cleanup temp files named '{}.*'...", tmpName);
		File tmpDir = mPlatform.getHttpSpoolDir().getParentFile();
		File[] tmpFiles = tmpDir.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.getName().startsWith(tmpName);
			}
		});
		for (File each : tmpFiles) {
			each.delete();
		}
		if (tmpFiles.length > 0) {
			log.info("Removed temporary files {} in {}ms", (Object[]) tmpFiles, System.currentTimeMillis() - ms);
		}
	}

	private String getTempFilePrefix() {
		// Referenced by thread name
		// see: de.ganskef.mocuishle.proxy.McHttpFilters.spool(HttpResponse)
		return IPlatform.APPLICATION_NAME + "-";
	}

	@Override
	public String getHashedName(String url) {
		// compatible to WWWOFFLE
		byte[] bytes = url.getBytes(McElement.DEFAULT_ENCODING);
		makeHostNameLowerCase(bytes);
		MessageDigest md = getMessageDigest();
		byte[] hash = Arrays.copyOf(md.digest(bytes), 16);
		// Yes, this is like WWWOFFLE does (not "URLSave" from Base64)
		String name = Base64.encodeBase64String(hash).replace('/', '-');
		int len = name.indexOf('=');
		if (len == -1) {
			return name;
		}
		return name.substring(0, len);
	}

	private void makeHostNameLowerCase(byte[] bytes) {
		for (int i = 0, count = 0; i < bytes.length; i++) {
			char c = (char) bytes[i];
			if (c == '/') {
				count++;
				if (count == 3) {
					break;
				}
			}
			bytes[i] = (byte) Character.toLowerCase((char) bytes[i]);
		}
	}

	private MessageDigest getMessageDigest() {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			return md;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean isCacheHit(String url) {
		File spoolFile = getSpoolFile(url);
		return spoolFile.exists();
	}

	private File getSpoolFile(String url) {
		String fileName = "U" + getHashedName(url);
		File cacheDir = isHttps(url) ? mPlatform.getHttpsSpoolDir() : mPlatform.getHttpSpoolDir();
		String host = parseHost(url);
		File hostDir = (host == null) ? new File(cacheDir, "undefined_host") : new File(cacheDir, host);
		File spoolFile = new File(hostDir, fileName);
		return spoolFile;
	}

	private boolean isHttps(String url) {
		return url.toLowerCase().startsWith("https://");
	}

	private String parseHost(String url) {
		Matcher m = URI_SEPARATION_PATTERN.matcher(url);
		if (m.matches()) {
			return m.group(1).toLowerCase(Locale.US);
		}
		return null;
	}

	public File initHostDir(String url) {
		// Note that getHashedName uses host name always in lower case.
		String host = parseHost(url);
		if (host == null) {
			log.info("Host is null for url: {}", url);
			return null;
		}
		File spoolDir = isHttps(url) ? mPlatform.getHttpsSpoolDir() : mPlatform.getHttpSpoolDir();
		File dir = new File(spoolDir, host);
		if (!dir.exists() && !dir.mkdirs()) {
			log.error("Can't create host directoy: {}", dir);
			return null;
		}
		if (!dir.canWrite()) {
			log.error("Can't write into host directoy: {}", dir);
			return null;
		}
		initNomediaMarkerForAndroid(dir);
		return dir;
	}

	public void initNomediaMarkerForAndroid(File dir) {
		File nomedia = new File(dir, ".nomedia");
		if (!nomedia.exists()) {
			try {
				nomedia.createNewFile();
			} catch (IOException e) {
				log.error("Can't write .nomedia into host directoy: {}", dir);
			}
		}
	}

	/**
	 * Der Inhalt soll aus dem Cache und nicht aus dem Internet gelesen werden wenn:
	 *
	 * <ul>
	 * <li>Immer wenn OFFLINE, auch refresh
	 * <li>Nicht wenn nicht OFFLINE mit refresh
	 * <li>Bevorzugt wenn LIMITED oder FULL mit TETHERING
	 * <li>Änderungsabhängig wenn FULL oder LIMITED mit FLATRATE
	 * </ul>
	 *
	 * @param refresh
	 */
	public boolean needCached(boolean refresh) {
		if (mState == State.TETHERING && refresh) {
			return false;
		}
		if (isOffline()) {
			return true;
		}
		if (refresh || mState == State.FLATRATE) {
			return false;
		}
		return mState == State.TETHERING || mPlatform.getConnectionState() != ConnectionState.FULL;
	}

	@Override
	public State setState(State state) {
		State old = mState;
		mState = state;
		return old;
	}

	@Override
	public File getSpoolDir(String folderName, String hostName) {
		return new File(getSpoolDir(folderName), hostName);
	}

	@Override
	public File getSpoolDir(String folderName) {
		return new File(mPlatform.getHttpSpoolDir().getParentFile(), folderName);
	}

	@Override
	public McElement createElement(String url) {
		return new McElement(this, url);
	}

	@Override
	public boolean isOffline() {
		return mState == State.CACHEONLY || mPlatform.getConnectionState() == IPlatform.ConnectionState.OFFLINE;
	}

	@Override
	public boolean isConnectionLimited() {
		if (mState == State.CACHEONLY) {
			return true;
		}
		final ConnectionState connectionState = mPlatform.getConnectionState();
		if (mState == State.TETHERING && connectionState == IPlatform.ConnectionState.FULL) {
			return true; // prefer offline
		}
		if (mState == State.FLATRATE && connectionState == IPlatform.ConnectionState.LIMITED) {
			return false; // prefer online
		}
		return connectionState != IPlatform.ConnectionState.FULL; // automatic
	}

	@Override
	public Date getDateModified(String url) {
		File spoolFile = getSpoolFile(url);
		if (spoolFile.exists()) {
			return new Date(spoolFile.lastModified());
		}
		return null;
	}

	@Override
	public int getMaximumBufferSizeInBytes() {
		return mPlatform.getMaximumBufferSizeInBytes();
	}

	@Override
	public File getPageUrlFile(String hostName, File browseFile) {
		File httpDir = new File(mPlatform.getHttpSpoolDir(), hostName);
		File urlFile = new File(httpDir, "U" + browseFile.getName());
		if (urlFile.exists()) {
			return urlFile;
		}
		File httpsDir = new File(mPlatform.getHttpsSpoolDir(), hostName);
		return new File(httpsDir, "U" + browseFile.getName());
	}

	/** Try to decode as UTF-8 with throwing an exception if it fails. */
	@Override
	public CharBuffer decodeUTF8(ByteBuffer in) throws CharacterCodingException {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder() //
				.onMalformedInput(CodingErrorAction.REPORT) //
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		CharBuffer buffer = decoder.decode(in);
		return buffer;
	}

	public String getProxyHome() {
		return "http://127.0.0.1:" + mPlatform.getProxyPort();
	}

	private File getBlockConfiguration() {
		return new File(mPlatform.getHttpSpoolDir().getParentFile(), "block.txt");
	}

	private List<String> readBlockeds() {
		File file = getBlockConfiguration();
		if (!file.isDirectory() && file.canRead()) {
			try {
				return FileUtils.readLines(file);
			} catch (IOException e) {
				log.info("Can't read block configuration " + file, e);
			}
		}
		return new ArrayList<String>();
	}

	@Override
	public void removeBlocked(String host) {
		if (host != null && host.trim().length() != 0) {
			if (mBlockeds.remove(host)) {
				List<String> lines = readBlockeds();
				lines.remove(host);
				File file = getBlockConfiguration();
				try {
					FileUtils.writeLines(file, lines);
				} catch (IOException e) {
					log.error("Could'nt update blocking configuration " + file, e);
				}
			}
		}
	}

	@Override
	public void addBlocked(String host) {
		if (host != null && host.trim().length() != 0) {
			if (mBlockeds.add(host.trim())) {
				List<String> lines = readBlockeds();
				lines.add(0, host);
				File file = getBlockConfiguration();
				try {
					FileUtils.writeLines(file, lines);
				} catch (IOException e) {
					log.error("Could'nt update blocking configuration " + file, e);
				}
			}
			mPassedConfig.remove(host);
		}
	}

	@Override
	public boolean isBlocked(String host) {
		return mBlockeds.contains(host);
	}

	@Override
	public boolean isPassed(String url) {
		return mPassedConfig.matches(url);
	}

	@Override
	public boolean isIndex(String url) {
		return mIndexConfig.matches(url);
	}

	@Override
	public void addPassed(String definition) {
		String hostName = mPassedConfig.add(definition);
		mBlockeds.remove(hostName);
	}

	@Override
	public void removePassed(String definition) {
		mPassedConfig.remove(definition);
	}

	@Override
	public Markup getMarkup() {
		return mMarkup;
	}

	@Override
	public File getWritableDir() {
		File dir = mPlatform.getHttpSpoolDir().getParentFile();
		if (!dir.exists() && !dir.mkdirs()) {
			log.error("Can't create writeable directoy: {}", dir);
			return null;
		}
		return dir;
	}

	@Override
	public IFullTextIndex getFullTextIndex() {
		return mPlatform.getFullTextIndex(this);
	}

	@Override
	public Iterator<BrowseDoc> browseDocIterator() {
		return new FullTextIndexIterator(mHistory);
	}

	@Override
	public BrowseDoc createBrowseDoc(String uri) {
		McElement element = createElement(uri);
		String hash = (isHttps(uri) ? "s/" : "/") + element.getHostName() + "/" + getHashedName(uri);

		CharSequence content = element.getFullTextSearchContent();

		return new BrowseDoc(hash, content);
	}

	@Override
	public IFullTextSearch getFullTextSearch() {
		return mPlatform.getFullTextSearch(this);
	}

	@Override
	public String getUrl(BrowseDoc doc) {
		String hash = doc.getHash();
		int lastSlash = hash.lastIndexOf('/');
		File dir = new File(mPlatform.getHttpSpoolDir() + hash.substring(0, lastSlash));
		File urlFile = new File(dir, "U" + hash.substring(lastSlash + 1));
		return readFileToString(urlFile);
	}

	@Override
	public String readFileToString(File file) {
		try {
			return FileUtils.readFileToString(file, McElement.DEFAULT_ENCODING);
		} catch (IOException e) {
			log.error("Read file failed", e);
			return "read file failed";
		}
	}

	@Override
	public void moveDir(File targetDir, File sourceDir) {
		if (sourceDir.exists()) {
			if (targetDir.exists()) {
				long lastModified = Math.max(sourceDir.lastModified(), targetDir.lastModified());
				for (File pageFile : sourceDir.listFiles()) {
					File deletedFile = new File(targetDir, pageFile.getName());
					deletedFile.delete();
					pageFile.renameTo(deletedFile);
				}
				targetDir.setLastModified(lastModified);
				sourceDir.delete();
			} else {
				targetDir.getParentFile().mkdirs();
				sourceDir.renameTo(targetDir);
			}
		}
	}

	@Override
	public void movePage(File targetFile, File sourceDir) {
		File sourceFile = new File(sourceDir, targetFile.getName());
		if (sourceFile.exists()) {
			targetFile.delete();
			// long lastModified = sourceFile.lastModified();
			sourceFile.renameTo(targetFile);
			// targetFile.setLastModified(lastModified);
			if (sourceDir.list().length == 0) {
				sourceDir.delete();
			}
		}
	}

	@Override
	public void deletePageDirectory(File dir) {
		if (!dir.isDirectory()) {
			log.error("ILLEGAL STATE require directory to delete {}", dir);
			if (dir.isFile()) {
				dir.delete();
			}
			if (!dir.exists()) {
				return; // XXX should never happens???
			}
		}
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				log.error("ILLEGAL STATE require files to delete {}", dir);
				deletePageDirectory(file);
				// return; // XXX should never happens???
			} else {
				file.delete();
				deletePageFiles(dir.getName(), file.getName());
			}
		}
		dir.delete();
	}

	private void deletePageFiles(String hostName, String hashedName) {
		File httpDir = new File(mPlatform.getHttpSpoolDir(), hostName);
		deletePageFiles(httpDir, hashedName);
		File httpsDir = new File(mPlatform.getHttpsSpoolDir(), hostName);
		deletePageFiles(httpsDir, hashedName);
	}

	private void deletePageFiles(File cacheDir, String hashedName) {
		new File(cacheDir, "U" + hashedName).delete();
		new File(cacheDir, "D" + hashedName).delete();
	}

	/* extract class History */ @Deprecated
	private History mHistory;

	/* extract class History */ @Deprecated
	public void recordBrowsing(File hostDir, String hashedName, String title) throws IOException {
		mHistory.recordBrowsing(hostDir, hashedName, title);
	}

	/* extract class History */ @Deprecated
	public void reorgBrowse(File hostDir, String hashedName, String title, long lastModified) throws IOException {
		mHistory.reorgBrowse(hostDir, hashedName, title, lastModified);
	}

	/* extract class History */ @Deprecated
	public void deleteBrowsePath(String scheme, String hostName, String path) {
		mHistory.deleteBrowsePath(scheme, hostName, path);
	}

	/* extract class History */ @Deprecated
	public void undeleteBrowsePath(String scheme, String hostName, String path) {
		mHistory.undeleteBrowsePath(scheme, hostName, path);
	}

	/* extract class History */ @Deprecated
	public void emptyTrash() {
		mHistory.emptyTrash();
	}

	/* extract class Outgoing */ @Deprecated
	private Outgoing mOutgoing;

	private boolean mDeleteMappedFiles;

	/* extract class Outgoing */ @Deprecated
	public File initOutgoingDir() {
		return mOutgoing.initOutgoingDir();
	}

	/* extract class Outgoing */ @Deprecated
	public boolean isRequested(String url) {
		return mOutgoing.isRequested(url);
	}

	/* extract class Outgoing */ @Deprecated
	@Override
	public Iterator<String> outgoingUriIterator(PageMode mode) {
		return mOutgoing.uriIterator(mode);
	}

	/* extract class Outgoing */ @Deprecated
	@Override
	public boolean isDeletedOutgoing(String uri) {
		return mOutgoing.isDeleted(uri);
	}

	/* extract class Outgoing */ @Deprecated
	@Override
	public void deleteOutgoing(String uri) {
		mOutgoing.deleteOutgoing(uri);
	}

	/* extract class Outgoing */ @Deprecated
	@Override
	public void undeleteOutgoing(String uri) {
		mOutgoing.undeleteOutgoing(uri);
	}

	/* extract class Outgoing */ @Deprecated
	@Override
	public void emptyTrashOutgoing() {
		mOutgoing.emptyTrashOutgoing();
	}

	/* extract class Outgoing */ @Deprecated
	@Override
	public void fetchOutgoingUris() {
	}

	@Override
	public CachedResponse createCachedResponse(String uri) {
		if (isCacheHit(uri)) {
			McElement element = createElement(uri);
			return element.getCachedResponse();
		}
		return null;
	}

	@Override
	public String export(String path) {
		return new Export(this).export(path);
	}

	private long initLastValidation() {
		File markerDir = getSpoolDir("marker");
		File markerFile = new File(markerDir, HISTORY_TIMESTAMP_NAME);
		if (markerFile.lastModified() == 0L) {
			markerDir.mkdirs();
			try {
				markerFile.createNewFile();
			} catch (IOException e) {
				return System.currentTimeMillis();
			}
		}
		return markerFile.lastModified();
	}

	@Override
	public boolean isCacheOnly() {
		return mState == State.CACHEONLY;
	}

	@Override
	public ValidateIterator validateIterator() {
		// Is it Thread save? If a second iterator will be instantiated, it will
		// be used without an issue because of the marker files.
		if (mValidateIterator == null) {
			mValidateIterator = new ValidateIterator(mHistory, this);
		}
		return mValidateIterator;
	}

	@Override
	public boolean isValidated(String url) {
		Path marker = markerPath(url);
		String host = marker.getParent().getFileName().toString();
		if (isBlocked(host) || isPassed(url)) {
			return true;
		}
		return Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
	}

	private Path markerPath(String url) {
		Path path = getSpoolFile(url).toPath();
		int index = path.getNameCount() - 3;
		Path marker = path.getParent().getParent().getParent().resolve("marker")
				.resolve(path.subpath(index, path.getNameCount()));
		return marker;
	}

	@Override
	public void markValidated(String url) {
		if (isCacheOnly()) {
			try {
				Path marker = markerPath(url);
				Files.createDirectories(marker.getParent());
				if (!Files.exists(marker)) {
					Files.createFile(marker);
					log.debug("Validated {} {}", url, marker);
				}
			} catch (IOException e) {
				log.error("Validated failed {} {}", url, e);
			}
		}
	}

	@Override
	public void purgeInvalidateds() {
		List<File> markers = new StoreMarker().read(getSpoolDir("marker"));
		StoreRemove purge = new StoreRemove(this, markers);
		purge.clean(getSpoolDir("http"));
		purge.clean(getSpoolDir("https"));
		new StoreRemove(this, Collections.emptyList()).clean(getSpoolDir("marker"));
	}

	public static McCache create(File mocuishleDir) {
		return new McCache(new JavaPlatform() {
			@Override
			public File getHttpSpoolDir() {
				return new File(mocuishleDir, "http");
			}

			@Override
			public File getHttpsSpoolDir() {
				return new File(mocuishleDir, "https");
			}
		});
	}

	public void markValidated(File file) {
		if (file.lastModified() < mLastValidation) {
			file.setLastModified(mLastValidation);
		}
	}

	public boolean isValidated(File file) {
		return file.lastModified() >= mLastValidation;
	}
}

package de.ganskef.mocuishle.cache;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICacheable;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.modify.HtmlModifier;

public class McElement implements ICacheable {

	private static final Logger log = LoggerFactory.getLogger(McElement.class);

	// // RFC 2396, appendix B.
	// // See also http://internet.ls-la.net/folklore/url-regexpr.html
	// m_uriPattern =
	// Pattern.compile(
	// "^(?:([^:/?#]+):)?" + // Group 1: optional scheme.
	// "(?://([^/?#]*))?" + // Group 2: optional authority.
	// "([^?#]*)" + // Group 3: path.
	// "(?:\\?([^#]*))?" + // Group 4: optional query string.
	// "(?:#(.*))?"); // Group 5: optional fragment.
	//
	// m_pathNameValuePattern = Pattern.compile("([^;/?&=#]+)=([^;&/?#]*)");
	// m_queryStringNameValuePattern = Pattern.compile("([^;&=#]+)=([^;&#]*)");

	private static final Pattern PATH_SEPARATION_PATTERN = Pattern.compile("(https?://.*?/)(.*)");

	private static final Pattern DOT_SLASH_PATTERN = Pattern.compile("^\\./");

	private static final Pattern PATH_PARENT_PATTERN = Pattern.compile("((?:.+/)?).+?/");

	private static final Pattern PATH_RELATIVE_PATTERN = Pattern.compile("\\.\\./(.*)");

	// HTTP/1.1 200 OK
	private static final Pattern FIRST_LINE_PATTERN = Pattern.compile("(HTTP/1.[01]) (\\d\\d\\d) .*");

	static final Pattern NOCONTENT_EXTENSION_PATTERN = Pattern.compile(".*/([^/]*)\\.(?:js|ico|png|gif|jpe?g)",
			Pattern.CASE_INSENSITIVE);

	static final Pattern DOCUMENT_EXTENSION_PATTERN = Pattern
			.compile(".*/([^/]*\\.(?:pdf|docx?|xlsx?|ppdx?|odt|odp|ods|txt)|README|INSTALL)", Pattern.CASE_INSENSITIVE);

	static final Pattern DOCUMENT_NAME_PATTERN = Pattern.compile(".*/([^/]*)\\..*");

	private static final char CR = 13, LF = 10;

	/**
	 * Encoding to handle all text/html. ISO-8859-1 decodes the bytes 1:1 into chars
	 * and thus is transparent when recoding to bytes.
	 */
	public static final Charset DEFAULT_ENCODING = StandardCharsets.ISO_8859_1;

	private final McCache mCache;

	private final String mHashedName;

	private final File mHostDir;

	private final String mUrl;

	private CachedResponse mCachedResponse;

	McElement(McCache cache, String url) {
		mCache = cache;
		mUrl = url;
		mHashedName = cache.getHashedName(url);
		mHostDir = cache.initHostDir(url);
	}

	public String getUrl() {
		return mUrl;
	}

	private static final Pattern SPACE_PATTERN = Pattern.compile(" ");
	private static final Pattern AMP_PATTERN = Pattern.compile("&amp;");
	private static final Pattern PLUS_PATTERN = Pattern.compile("\\+");

	public CachedResponse readCachedResponseToView() {
		long ms = System.currentTimeMillis();
		CachedResponse result = getCachedResponse();
		recordBrowseForDocuments();
		updateLastRecentUsage();
		log.info("Loaded in {}ms {} {}", System.currentTimeMillis() - ms, mHashedName, mUrl);
		return result;
	}

	public void updateLastRecentUsage() {
		mCache.markValidated(new File(mHostDir, "U" + mHashedName));
		mCache.markValidated(new File(mHostDir, "D" + mHashedName));
	}

	public CachedResponse getCachedResponse() {
		if (mCachedResponse == null) {
			mCachedResponse = initCachedResponse();
		}
		return mCachedResponse;
	}

	private CachedResponse initCachedResponse() {
		int statusCode = 500;
		Collection<Entry<String, String>> headers = new HashSet<Map.Entry<String, String>>();
		File file = new File(mHostDir, "D" + mHashedName);
		if (!file.isFile()) {
			log.info("Missed data file {}", file);
			return new CachedResponse(statusCode, headers, null, System.currentTimeMillis(), null);
		}
		long date = 0L; // fallback if no date header
		ByteBuffer content = ByteBuffer.allocate(4096);
		try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
			FileChannel ch = input.getChannel();
			ch.read(content);
			content.flip();
			statusCode = readHeaders(content, headers);
			int offset = content.position();
			content.clear();
			content = null; // lazy load the buffer

		} catch (BufferUnderflowException | IOException e) {
			log.info("Invalid data file {} {}", file, String.valueOf(e));
			return new CachedResponse(statusCode, headers, null, System.currentTimeMillis(), null);
		}
		for (Map.Entry<String, String> each : headers) {
			if (each.getKey().equalsIgnoreCase("Date")) {
				try {
					String rfcDate = each.getValue();
					ZonedDateTime zoned = ZonedDateTime.parse(rfcDate, DateTimeFormatter.RFC_1123_DATE_TIME);
					date = zoned.toEpochSecond() * 1000;
				} catch (DateTimeParseException e) {
					try {
						String rfcDate = WIDESPACE_PATTERN.matcher(each.getValue()).replaceAll(" ") //
								.replace("UTC", "GMT").replace("PDT", "GMT").replace("CET", "GMT");
						ZonedDateTime zoned = ZonedDateTime.parse(rfcDate, DateTimeFormatter.RFC_1123_DATE_TIME);
						date = zoned.toEpochSecond() * 1000;
					} catch (DateTimeParseException e2) {
						log.warn("{} {}", each, e2);
					}
				}
				break;
			}
		}
		return new CachedResponse(statusCode, headers, content, date, file);
	}

	public static int readHeaders(ByteBuffer input, Collection<Map.Entry<String, String>> headers) {
		int statusCode = 500;
		if (input.remaining() == 0) {
			log.error("result.remaining() == 0 in readHeaders");
			return statusCode;
		}
		StringBuilder b = new StringBuilder();
		char c;
		boolean firstLine = true;
		while (true) {
			String line = "";
			while (true) {
				c = (char) input.get();
				if (c == LF) {
					line = b.toString();
					b.setLength(0);
					break;
				} else if (c == CR) {
					line = b.toString();
					b.setLength(0);
					char c2 = (char) input.get();
					if (c2 == CR) {
						return statusCode; // empty line
					}
					if (c2 != LF) {
						b.append(c2);
						c = c2;
					}
					break;
				} else {
					b.append(c);
				}
			}
			if (firstLine) {
				firstLine = false;
				Matcher m = FIRST_LINE_PATTERN.matcher(line);
				if (m.matches()) {
					statusCode = Integer.parseInt(m.group(2));
				} else {
					log.error("INVALID SPOOL DATA first line={}", line);
					return statusCode;
				}
			}
			if (line.isEmpty()) {
				return statusCode; // empty line
			}
			log.debug(line);
			int pos = line.indexOf(':');
			if (pos > 0) {
				String name = line.substring(0, pos);
				String value = line.substring(pos + 1).trim();
				headers.add(new SimpleEntry<String, String>(name, value));
			}
		}
	}

	public ByteBuffer getModifiedTextHtml(ByteBuffer content, boolean online) {
		long ms = System.currentTimeMillis();
		CharBuffer html = DEFAULT_ENCODING.decode(content);
		HtmlModifier hc = new HtmlModifier(this, html);
		CharSequence modified = hc.getModifiedHtml(online);
		ByteBuffer result = DEFAULT_ENCODING.encode(CharBuffer.wrap(modified));
		log.info("Modify in {}ms {} {}", System.currentTimeMillis() - ms, mHashedName, mUrl);
		return result;
	}

	public boolean isCached(String href) {
		if (mHostDir == null) {
			return false;
		}
		String url = toUrl(href);
		return mCache.isCacheHit(url);
	}

	public boolean isRequested(String href) {
		String url = toUrl(href);
		return mCache.isRequested(url);
	}

	public String toUrl(String href) {
		// TODO see org.apache.commons.io.FileUtils.decodeUrl(String)
		href = SPACE_PATTERN.matcher(href).replaceAll("%20");
		href = AMP_PATTERN.matcher(href).replaceAll("&");
		href = DOT_SLASH_PATTERN.matcher(href).replaceAll("");
		if (href.isEmpty()) {
			// An anchor href to the same page
			return mUrl;
		}
		if (href.startsWith("//")) {
			// Use the same protocol Google api
			return mUrl.substring(0, mUrl.indexOf('/')) + href;
		}
		if (href.contains("://")) {
			int last = href.lastIndexOf('/');
			if (last <= 7) {
				// Host without a slash
				return href + '/';
			}
			// Note that getHashedName() uses host name always in lower case.
			return href;
		}
		final String schema = mHostDir.getParentFile().getName().toLowerCase();
		if (href.startsWith("/")) {
			return String.format("%s://%s%s", schema, mHostDir.getName(), href);
		}
		int length = mUrl.lastIndexOf('/');
		if (length > -1) {
			return concatWithoutRelative(mUrl.substring(0, length + 1), href);
		}
		return String.format("%s://%s/%s", schema, mHostDir.getName(), href);
	}

	private String concatWithoutRelative(String base, String href) {
		final Matcher m = PATH_SEPARATION_PATTERN.matcher(base);
		if (m.matches()) {
			final String root = m.group(1);
			String basePath = m.group(2);
			String subHref = href;
			Matcher parentMatcher = PATH_PARENT_PATTERN.matcher(basePath);
			Matcher relativeMatcher = PATH_RELATIVE_PATTERN.matcher(href);
			while (parentMatcher.matches() && relativeMatcher.matches()) {
				basePath = parentMatcher.group(1);
				subHref = relativeMatcher.group(1);
				parentMatcher = PATH_PARENT_PATTERN.matcher(basePath);
				relativeMatcher = PATH_RELATIVE_PATTERN.matcher(subHref);
			}
			return root + basePath + subHref;
		}
		return base + href;
	}

	public boolean isIgnored() {
		return mHostDir == null;
	}

	public boolean needCached(boolean refresh) {
		return mCache.needCached(refresh) && isCached(mUrl);
	}

	public boolean needBadGateway() {
		return mCache.isOffline() && !isCached(mUrl);
	}

	public void recordForDownload(String method, String protocolVersion, Iterable<Entry<String, String>> headers) {
		if (mHostDir == null || mCache.isCacheOnly()) {
			return;
		}
		StringBuffer b = new StringBuffer();
		b.append(method).append(' ').append(mUrl).append(' ').append(protocolVersion).append('\r').append('\n');
		for (Map.Entry<String, String> each : headers) {
			String key = each.getKey();
			String value = each.getValue();
			b.append(key).append(": ").append(value).append('\r').append('\n');
		}
		File file = new File(mCache.initOutgoingDir(), "O" + mHashedName);
		try {
			FileUtils.write(file, b, DEFAULT_ENCODING);
			log.debug("Written {} {}", mHashedName, mUrl);
			log.debug("{}", b);
		} catch (IOException e) {
			log.error("Can't write outgoing file: " + file + " for URL: " + mUrl, e);
		}
	}

	public void store(int statusCode, String stateLine, Iterable<Entry<String, String>> headers, ByteBuffer content) {
		if (mHashedName == null) {
			log.warn("Spooling failed, hash is null {} {}", mUrl, headers);
			return;
		}
		if (mHostDir == null) {
			log.warn("Spooling failed, host is null {} {}", mUrl, headers);
			return;
		}
		long ms = System.currentTimeMillis();
		File tempFile = null;
		try {
			tempFile = File.createTempFile(mHashedName, ".txt", mHostDir);
			try (RandomAccessFile output = new RandomAccessFile(tempFile, "rw")) {
				FileChannel channel = output.getChannel();
				String headerText = getHeaderText(stateLine, headers);
				channel.write(ByteBuffer.wrap(headerText.getBytes(DEFAULT_ENCODING)));
				channel.write(content);
			}
		} catch (IOException e) {
			cleanup(tempFile);
			log.error("Can't write data to temp file: " + tempFile, e);
			return;
		}
		File urlFile = new File(mHostDir, "U" + mHashedName);
		try {
			FileUtils.write(urlFile, mUrl, DEFAULT_ENCODING);
			log.debug("Written URL to file");
		} catch (IOException e) {
			cleanup(tempFile);
			log.error("Can't write URL file " + urlFile + " " + mUrl, e);
			return;
		}
		File dataFile = new File(mHostDir, "D" + mHashedName);
		if (dataFile.exists() && !dataFile.delete()) {
			cleanup(tempFile);
			log.error("Can't delete old data file {} {}", dataFile, mUrl);
			return;
		}
		if (tempFile != null && tempFile.renameTo(dataFile)) {
			log.info("Stored in {}ms {} {}", System.currentTimeMillis() - ms, mHashedName, mUrl);
		} else {
			cleanup(tempFile);
			log.error("Can't rename to D temp file {} {}", tempFile, mUrl);
			return;
		}
		long lastModified = System.currentTimeMillis();
		mCachedResponse = new CachedResponse(statusCode, headers, content, lastModified, null);
		recordBrowseForDocuments();
	}

	private void cleanup(File file) {
		if (file != null && file.exists() && !file.delete()) {
			file.deleteOnExit();
		}
	}

	private String getHeaderText(String stateLine, Iterable<Entry<String, String>> headers) {
		StringBuilder b = new StringBuilder(stateLine);
		for (Entry<String, String> each : headers) {
			b.append(each.getKey()).append(": ").append(each.getValue()).append('\r').append('\n');
		}
		b.append('\r').append('\n');
		String headerText = b.toString();
		return headerText;
	}

	private void recordBrowseForDocuments() {
		if (mCache.isIndex(mUrl) || isDocumentExtension()) {
			// use the document name for title
			String title = documentName();
			recordBrowse(title);
		}
	}

	private String documentName() {
		try {
			String path = new URL(mUrl).getPath();
			Matcher m = DOCUMENT_NAME_PATTERN.matcher(path);
			if (m.matches()) {
				return m.group(1).replaceAll("(:?%20|_|\\s)+", " ");
			}
			return path;
		} catch (MalformedURLException e) {
			log.warn("recordBrowseForDocuments {} {}", mUrl, e.toString());
			return mUrl;
		}
	}

	@Override
	public boolean isNocontentExtension() {
		Matcher m = NOCONTENT_EXTENSION_PATTERN.matcher(mUrl);
		return m.matches();
	}

	@Override
	public boolean isDocumentExtension() {
		Matcher m = DOCUMENT_EXTENSION_PATTERN.matcher(mUrl);
		return m.matches();
	}

	public void deleteOutgoing() {
		if (mHostDir == null) {
			log.warn("Can not resume (delete), host dir is null for URL {}", mUrl);
			return;
		}
		File file = new File(mCache.initOutgoingDir(), "O" + mHashedName);
		if (!file.exists()) {
			return;
		}
		if (file.delete()) {
			log.debug("Resumed {} {}", mHashedName, mUrl);
		} else {
			log.warn("Can not resume (delete) {} for URL {}", file, mUrl);
		}
	}

	@Override
	public String toString() {
		return String.format("%s %s", mHashedName, mUrl);
	}

	public void addAlert(Alerts alert) {
		// TODO Auto-generated method stub

	}

	public void recordBrowse(String title) {
		if (isIgnored()) {
			return;
		}
		try {
			mCache.recordBrowsing(mHostDir, mHashedName, title);
		} catch (IOException e) {
			log.error("Can't create browse file", e);
			addAlert(Alerts.REC_BROWSED_FAILED);
		}
	}

	public String getLocalUrl() {
		return mCache.getProxyHome();
	}

	public String getHostName() {
		return mHostDir.getName();
	}

	public boolean isOkStatus() {
		return getCachedResponse().getStatusCode() == 200;
	}

	@Override
	public Markup getMarkup() {
		return mCache.getMarkup();
	}

	@Override
	public long lastModified() {
		if (!isCached(mUrl)) {
			return 0L;
		}
		return getCachedResponse().getLoadedDate();
	}

	private static final Pattern WIDESPACE_PATTERN = Pattern.compile("\\s+");
	private static final int MAX_HTML_LENGTH = 1024 * 1024;

	public CharSequence getFullTextSearchContent() {
		CachedResponse cr = getCachedResponse();

		// TODO filter document types to text (doc, pdf...)

		if (cr.isTextHtml()) {
			ByteBuffer input = cr.getContent();
			CharSequence html;
			try {
				html = mCache.decodeUTF8(input);
			} catch (CharacterCodingException e) {
				html = DEFAULT_ENCODING.decode(input);
			}
			// result = HTML_SCRIPT_PATTERN.matcher(result).replaceAll("");
			// result = XML_TAG_PATTERN.matcher(result).replaceAll("");
			// result = WIDESPACE_PATTERN.matcher(result).replaceAll(" ");
			// FIXME unmask HTML encoded text, to remove Jsoup again

			if (html.length() > MAX_HTML_LENGTH / 2) {
				log.info("Warning {} chars in D{} for {}", html.length(), mHashedName, mUrl);
			}
			CharSequence limited = html.length() > MAX_HTML_LENGTH ? html.subSequence(0, MAX_HTML_LENGTH) : html;
			Document doc = Jsoup.parse(limited.toString());
			doc.select("script").remove();
			doc.select("style").remove();
			doc.select("noscript").remove();
			String result = doc.text();
			result = WIDESPACE_PATTERN.matcher(result).replaceAll(" ");
			return result;
		}
		return "";
	}

	@Override
	public String getProxyHome() {
		return mCache.getProxyHome();
	}

	public void reorgBrowse(Collection<String> titles, Collection<String> referenceds) {
		try {
			CachedResponse cr = getCachedResponse();
			if (cr.getStatusCode() != 200) {
				for (Entry<String, String> each : cr.getHeaders()) {
					if (each.getKey().equalsIgnoreCase("Location")) {
						String location = toUrl(each.getValue());
						McElement element = mCache.createElement(location);
						if (!element.isIgnored() && !mCache.isBlocked(element.getHostName())
								&& !mCache.isPassed(location)) {
							updateLastRecentUsage();
							break;
						}
					}
				}
			} else if (cr.isTextHtml()) {
				ByteBuffer input = cr.getContent();
				CharSequence html;
				try {
					html = mCache.decodeUTF8(input);
				} catch (CharacterCodingException e) {
					html = StandardCharsets.ISO_8859_1.decode(input);
				}
				Document doc = Jsoup.parse(html.toString());
				doc.select("script").remove();
				doc.select("style").remove();
				doc.select("noscript").remove();
				Elements title = doc.select("title");
				if (title.hasText() && doc.select("body").hasText()) {
					titles.add(mUrl);
					reorgBrowse(title.text(), cr.getLoadedDate());
				}
				addExistingReferences(doc, referenceds);
				updateLastRecentUsage();
			} else if (mCache.isIndex(mUrl) || DOCUMENT_EXTENSION_PATTERN.matcher(mUrl).matches()) {
				titles.add(mUrl);
				String title = documentName();
				reorgBrowse(title, cr.getLoadedDate());
				updateLastRecentUsage();
			}
		} catch (IOException e) {
			// ignore while reorganizing cache
		}
	}

	private void addExistingReferences(Document doc, Collection<String> referenceds) {
		Elements anchors = doc.select("a[href]");
		anchors.forEach(a -> {
			String ref = PLUS_PATTERN.matcher(a.absUrl("href")).replaceAll("%20");
			if (ref.length() > 0) {
				referenceds.add(ref);
			}
		});
	}

	public void reorgBrowse(String title, long lastModified) throws IOException {
		mCache.reorgBrowse(mHostDir, mHashedName, title, lastModified);
	}
}

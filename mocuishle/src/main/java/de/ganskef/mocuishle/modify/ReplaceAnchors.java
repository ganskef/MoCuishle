package de.ganskef.mocuishle.modify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICacheableModify;
import de.ganskef.mocuishle.Markup.Modify;

public class ReplaceAnchors implements IModifier {

	private static final int TIMEOUT_MILLISECONDS = 5000;

	// http://www.mkyong.com/regular-expressions/how-to-extract-html-links-with-regular-expression/
	//
	private static final Pattern ANCHOR_PATTERN = Pattern.compile(
			"(?:<!\\[CDATA\\[.*?\\]\\]>|<!--.*?-->|<a(\\s[^>]+)>(.*?)</a>)", //
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

	private static final Pattern HREF_PATTERN = Pattern.compile(
			"\\s+href\\s*=\\s*(\"([^\"]+)\"|'([^']+)'|([^'\">\\s]+))", //
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

	private final ICacheableModify mCached;

	private final String mTitle;

	private static final Logger log = LoggerFactory.getLogger(ReplaceAnchors.class);

	public ReplaceAnchors(ICacheableModify cached, String title) {
		mCached = cached;
		mTitle = title;
	}

	public StringBuilder modify(StringBuilder b) {
		boolean modified = false;
		Matcher m = ANCHOR_PATTERN.matcher(b);
		StringBuffer buffer = new StringBuffer(b.length());
		// 5 seconds timeout for https://en.m.wikipedia.org/wiki/Clint_Eastwood
		// since RegEx seems to be very slow on Android
		//
		long timeout = System.currentTimeMillis() + TIMEOUT_MILLISECONDS;
		int count = 0;
		while (m.find()) {
			if ((System.currentTimeMillis() > timeout)) {
				log.warn("Timeout {}ms after {} hrefs {}", TIMEOUT_MILLISECONDS, count, mCached);
				break;
			}
			// FIXME wenn m.group(2) einen weiteren <a\b Beginn enthält loggen
			//
			if (m.group(1) != null) {
				Matcher m2 = HREF_PATTERN.matcher(m.group(1));
				if (m2.find()) {
					count++;
					log.debug(m.group(0));
					String href = stripUrl(m2.group(1));
					String replacement = getReplacement(href);
					m.appendReplacement(buffer, replacement);
					modified = true;
				} else {
					log.debug("INVALID HREF {} {}", m.group(0), mCached);
				}
			}
		}
		if (modified) {
			m.appendTail(buffer);
			// FIXME das passt hier überhaupt nicht hin
			mCached.recordBrowse(mTitle);
			return new StringBuilder(buffer);
		}
		return b;
	}

	private String stripUrl(String input) {
		String cleared = input.replaceAll("\"", "").replaceAll("'", "").replaceAll("&amp;", "&")
				.replaceAll("&quot;", "\"").replaceAll("&lt;", "<").replaceAll("&gt;", ">");
		int index = cleared.indexOf('#');
		if (index == -1) {
			return cleared;
		}
		return cleared.substring(0, index);
	}

	private String getReplacement(String href) {
		if (mCached.isCached(href)) {
			return getModifyDefinition().getCached();
		}
		if (mCached.isRequested(href)) {
			return getModifyDefinition().getRequested();
		}
		return getModifyDefinition().getNotCached();
	}

	private Modify getModifyDefinition() {
		return mCached.getMarkup().getModify();
	}
}

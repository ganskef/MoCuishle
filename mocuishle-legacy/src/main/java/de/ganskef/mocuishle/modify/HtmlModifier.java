package de.ganskef.mocuishle.modify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICacheableModify;
import de.ganskef.mocuishle.Markup;
import de.ganskef.mocuishle.Markup.Modify;

public class HtmlModifier {

	private static final Pattern BEGIN_BODY_PATTERN = Pattern.compile("<body.*?>\\s*",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

	private static final Pattern END_BODY_PATTERN = Pattern.compile("</body>", Pattern.CASE_INSENSITIVE);

	private static final Pattern BEGIN_HEAD_PATTERN = Pattern.compile("<head.*?>\\s*",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

	private static final Pattern END_HEAD_PATTERN = Pattern.compile("</head>", Pattern.CASE_INSENSITIVE);

	private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>\\s*([^<]*)\\s*</title>", //
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

	private final CharSequence mHtml;

	private final ICacheableModify mCached;

	private static final Logger log = LoggerFactory.getLogger(HtmlModifier.class);

	public HtmlModifier(ICacheableModify cached, CharSequence html) {
		mCached = cached;
		mHtml = html;
	}

	/** Title is required to modify HTML only. Don't touch javascript (...). */
	public CharSequence getModifiedHtml(boolean online) {
		if (!mCached.isOkStatus()) {
			log.debug("NOT MODIFIED not 200 OK state");
			return mHtml;
		}
		Matcher m3 = TITLE_PATTERN.matcher(mHtml);
		if (!m3.find()) {
			log.debug("NOT MODIFIED has no title");
			return mHtml;
		}
		log.debug("Modify HTML for {}", mCached);
		String title = WHITESPACE_PATTERN.matcher(m3.group(1)).replaceAll(" ");
		// FIXME hier muss der Titel in UTF-8 umgewandelt werden, sonst wird er
		// falsch im Markup ausgegeben und falsch in die browse-Datei
		// geschrieben, dann muss er generell als UTF-8 behandelt werden

		StringBuilder b = new StringBuilder(mHtml);

		/*
		 * FIXME Die Modifiable sollen in einer Liste bereitgestellt und verarbeitet
		 * werden. Status: online (from filter), localUrl (from proxy via cached),
		 * hostName (from cached)
		 */
		// b = new ReplaceIframes().modify(b);
		b = new ReplaceAnchors(mCached, title).modify(b);

		Modify def = mCached.getMarkup().getModify();
		b = new InsertAfter(BEGIN_HEAD_PATTERN, def.getHeadBegin()).modify(b);
		b = new InsertBefore(END_HEAD_PATTERN,
				String.format(def.getHeadEnd(), online ? def.getColorCached() : def.getColorRequested())).modify(b);
		b = new InsertAfter(BEGIN_BODY_PATTERN, def.getBodyBegin()).modify(b);
		Object url = new Object() {
			@Override
			public String toString() {
				return mCached.getUrl();
			}
		};
		Object encodedTitle = Markup.urlEncoded(title);
		Object encodedUrl = Markup.urlEncoded(url);
		Object age = mCached.getMarkup().loaded(mCached);
		b = new InsertBefore(END_BODY_PATTERN, String.format(def.getBodyEnd() //
				, mCached.getLocalUrl() // %1$s
				, mCached.getHostName() // %2$s
				, encodedUrl // %3$s für Florian Pittner
				, encodedTitle // %4$s
				, age // %5$s
		)).modify(b);

		return b;
		// return mHtml;
	}
}

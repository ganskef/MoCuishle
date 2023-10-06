package de.ganskef.mocuishle.cache;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateIterator implements Iterator<String> {

	private static final Logger log = LoggerFactory.getLogger(ValidateIterator.class);

	private static final Pattern NOHREF_EXTENSION_PATTERN = Pattern
			.compile(".*/([^/]*\\.(?:pdf|docx?|xlsx?|ppdx?|odt|odp|ods|txt)|README|INSTALL)", Pattern.CASE_INSENSITIVE);

	private final HistoryIterator mDelegate;

	private final IStore mStore;

	private String mNext;

	public ValidateIterator(History history, IStore store) {
		mDelegate = new HistoryIterator(history);
		mStore = store;
	}

	@Override
	public boolean hasNext() {
		if (!mStore.isCacheOnly()) {
			return false;
		}
		if (mNext != null) {
			return true;
		}
		while (mDelegate.hasNext()) {
			String url = mDelegate.next().getUrl();
			if (NOHREF_EXTENSION_PATTERN.matcher(url).matches()) {
				log.debug("Ignore Document without hrefs {}", url);
			} else if (mStore.isValidated(url)) {
				log.debug("Ignore allready validated, blocked or passed {}", url);
			} else {
				mNext = url;
				return true;
			}
		}
		return false;
	}

	@Override
	public String next() {
		if (mNext == null) {
			throw new NoSuchElementException();
		}
		String url = mNext;
		mNext = null;
		mStore.markValidated(url);
		return url;
	}
}

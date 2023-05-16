package de.ganskef.mocuishle.cache;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICache.BrowseDoc;
import de.ganskef.mocuishle.ICache.BrowsePage;

public class FullTextIndexIterator implements Iterator<BrowseDoc> {

	private static final Logger log = LoggerFactory.getLogger(FullTextIndexIterator.class);

	private final HistoryIterator mDelegate;

	private final History mHistory;

	private BrowseDoc mNext;

	public FullTextIndexIterator(History history) {
		mDelegate = new HistoryIterator(history);
		mHistory = history;
	}

	@Override
	public boolean hasNext() {
		if (mNext != null) {
			return true;
		}
		while (mDelegate.hasNext()) {
			BrowsePage page = mDelegate.next();
			String url = page.getUrl();
			try {
				mNext = mHistory.createBrowseDoc(url);
				return true;
			} catch (Exception e) {
				log.error("Error reading " + url, e);
			}
		}
		return false;
	}

	@Override
	public BrowseDoc next() {
		if (mNext == null) {
			throw new NoSuchElementException();
		}
		BrowseDoc result = mNext;
		mNext = null;
		return result;
	}
}

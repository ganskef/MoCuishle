package de.ganskef.mocuishle.cache;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.ganskef.mocuishle.ICache.BrowseHost;
import de.ganskef.mocuishle.ICache.BrowsePage;
import de.ganskef.mocuishle.ICache.PageMode;

public class HistoryIterator implements Iterator<BrowsePage> {

	private static final Logger log = LoggerFactory.getLogger(HistoryIterator.class);

	private enum State {
		READY, NOT_READY, DONE, FAILED,
	}

	private State mState = State.NOT_READY;

	private BrowsePage mNext;

	private final History mHistory;

	private Iterator<BrowseHost> mHostIterator;

	private Iterator<BrowsePage> mPageIterator;

	private BrowseHost mCurrentHost;

	public HistoryIterator(History history) {
		mHistory = history;
	}

	private BrowsePage computeNext() {
		nextHostIfNecessary();
		while (mPageIterator != null && mPageIterator.hasNext()) {
			BrowsePage page = mPageIterator.next();
			if (page != null) {
				return page;
			} else {
				nextHostIfNecessary();
			}
		}
		mState = State.DONE;
		return null;
	}

	private void nextHostIfNecessary() {
		if (mHostIterator == null) {
			List<BrowseHost> hosts = mHistory.getBrowseHosts(PageMode.MORE);
			Collections.reverse(hosts);
			mHostIterator = hosts.iterator();
		}
		if (mPageIterator == null || !mPageIterator.hasNext()) {
			while (mHostIterator.hasNext()) {
				mCurrentHost = mHostIterator.next();
				String hostName = mCurrentHost.getHostName();
				List<BrowsePage> pages = mHistory.getBrowsePages(hostName, PageMode.MORE);
				log.info("Enter {} ({})", hostName, pages.size());
				Collections.reverse(pages);
				mPageIterator = pages.iterator();
				if (mPageIterator.hasNext()) {
					return;
				} else {
					log.warn("Skipped empty host {}", mCurrentHost.getHostName());
				}
			}
		}
	}

	@Override
	public boolean hasNext() {
		if (mState == State.FAILED) {
			throw new IllegalStateException(String.valueOf(mState));
		}
		switch (mState) {
		case DONE:
			log.info("Nothing to do");
			return false;
		case READY:
			return true;
		default:
		}
		return tryToComputeNext();
	}

	private boolean tryToComputeNext() {
		mState = State.FAILED; // temporary pessimism
		mNext = computeNext();
		if (mState != State.DONE) {
			mState = State.READY;
			return true;
		}
		return false;
	}

	@Override
	public BrowsePage next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		mState = State.NOT_READY;
		return mNext;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}

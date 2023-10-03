package de.ganskef.mocuishle.ui;

import java.util.Iterator;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;
import de.ganskef.mocuishle.cache.Outgoing;

public class OutgoingIterateNavigation extends AbstractMarkupNavigation implements IAction {

	private final String mMarkup;

	private final Status mHttpResponseStatus;

	public OutgoingIterateNavigation(ICache cache) {
		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		String markup = "http://localhost:9090/done";
		Status status = Status.NOT_FOUND;
		Outgoing outgoing = cache.getOutgoing();
		Iterator<String> it = outgoing.refreshIterator();
		while (it.hasNext()) {
			String next = it.next();
			if (!outgoing.isDeleted(next)) {
				markup = next;
				status = Status.OK;
				break;
			}
		}
		mMarkup = markup;
		mHttpResponseStatus = status;
	}

	@Override
	public String prepareAnswer() {
		return mMarkup;
	}

	@Override
	public Status status() {
		return mHttpResponseStatus;
	}
}

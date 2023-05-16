package de.ganskef.mocuishle.ui;

import java.util.Iterator;

import de.ganskef.mocuishle.IAction;
import de.ganskef.mocuishle.ICache;

public class ValidateNavigation extends AbstractMarkupNavigation implements IAction {

	private final String mMarkup;

	private final Status mHttpResponseStatus;

	public ValidateNavigation(final ICache cache) {
		if (cache == null) {
			throw new IllegalArgumentException("cache is null");
		}
		final Iterator<String> it = cache.validateIterator();
		if (it.hasNext()) {
			mMarkup = it.next();
			mHttpResponseStatus = Status.OK;
		} else {
			mMarkup = "http://localhost:9090/done";
			mHttpResponseStatus = Status.NOT_FOUND;
		}
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

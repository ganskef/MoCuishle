package de.ganskef.mocuishle.ui;

import de.ganskef.mocuishle.IActionRedirect;

public class RedirectAction implements IActionRedirect {

	private final String mLocation;

	public RedirectAction(String redirect) {
		this.mLocation = redirect;
	}

	@Override
	public String prepareAnswer() {
		return mLocation;
	}

	@Override
	public Status status() {
		return Status.FOUND;
	}
}

package de.ganskef.mocuishle.ui;

import de.ganskef.mocuishle.IAction;

public abstract class AbstractMarkupNavigation implements IAction {

	@Override
	public IAction.Status status() {
		return Status.OK;
	}
}

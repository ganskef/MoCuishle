package de.ganskef.mocuishle.ui;

import de.ganskef.mocuishle.IActionExport;

public class ExportAction implements IActionExport {

	private final String mAbsoluteFile;

	public ExportAction(String absoluteFile) {
		mAbsoluteFile = absoluteFile;
	}

	@Override
	public String prepareAnswer() {
		return mAbsoluteFile;
	}

	@Override
	public Status status() {
		return Status.OK;
	}
}

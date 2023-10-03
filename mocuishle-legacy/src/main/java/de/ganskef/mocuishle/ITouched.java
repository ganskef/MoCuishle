package de.ganskef.mocuishle;

/** Introduced to show time stamps of things at the UI. */
public interface ITouched {

	/** Return markup templates to modify. */
	Markup getMarkup();

	long lastModified();
}

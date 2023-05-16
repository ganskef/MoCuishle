package de.ganskef.mocuishle;

/**
 * Special handling to download a ZIP archive of a host.
 *
 * @see {@link IAction}
 */
public interface IActionExport extends IAction {

	/**
	 * Process the request and answer the absolute file system path of the produced
	 * temporary ZIP archive.
	 *
	 * @return the answer
	 */
	String prepareAnswer();
}

package de.ganskef.mocuishle;

public interface IActionRedirect extends IAction {

	/**
	 * Process the request and answer the redirect location.
	 *
	 * @return the answer
	 */
	String prepareAnswer();
}

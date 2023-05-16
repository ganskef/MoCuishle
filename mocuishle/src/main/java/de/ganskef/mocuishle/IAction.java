package de.ganskef.mocuishle;

/** Clients reacts to requests from the caller (user interface). */
public interface IAction {

	enum Status {
		OK {
			public int code() {
				return 200;
			}
		},

		FOUND {
			public int code() {
				return 302;
			}
		},

		BAD_REQUEST {
			public int code() {
				return 400;
			}
		},

		NOT_FOUND {
			public int code() {
				return 404;
			}
		};

		public abstract int code();
	}

	/**
	 * Process the request and answer a text, regulary HTML markup.
	 *
	 * @return the answer
	 */
	String prepareAnswer();

	/**
	 * HTTP status code to answer in the response.
	 *
	 * @return the HTTP status
	 */
	Status status();
}

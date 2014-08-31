package net.villonanny;

public class FatalException extends RuntimeException {

	public FatalException() {
		super();
	}

	public FatalException(String message, Throwable cause) {
		super(message, cause);
	}

	public FatalException(String message) {
		super(message);
	}

	public FatalException(Throwable cause) {
		super(cause);
	}

}

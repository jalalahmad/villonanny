package net.villonanny;

public class TranslationException extends FatalException {

	public TranslationException() {
	}

	public TranslationException(String message, Throwable cause) {
		super(message, cause);
	}

	public TranslationException(String message) {
		super(message);
	}

	public TranslationException(Throwable cause) {
		super(cause);
	}

}

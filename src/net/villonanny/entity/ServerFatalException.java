package net.villonanny.entity;

import net.villonanny.FatalException;

public class ServerFatalException extends FatalException {

	public ServerFatalException(String message, Throwable cause) {
		super(message, cause);
	}

	public ServerFatalException(String message) {
		super(message);
	}

	public ServerFatalException(Throwable cause) {
		super(cause);
	}

}

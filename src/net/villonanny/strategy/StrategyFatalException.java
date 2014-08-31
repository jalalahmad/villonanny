package net.villonanny.strategy;

import net.villonanny.FatalException;

public class StrategyFatalException extends FatalException {

	public StrategyFatalException(String message, Throwable cause) {
		super(message, cause);
	}

	public StrategyFatalException(String message) {
		super(message);
	}

	public StrategyFatalException(Throwable cause) {
		super(cause);
	}

}

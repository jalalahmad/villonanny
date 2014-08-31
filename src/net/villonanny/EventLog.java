package net.villonanny;

import org.apache.log4j.Logger;
import org.slf4j.helpers.MessageFormatter;

public class EventLog {
	private final static Logger log = Logger.getLogger("EventLog");
	
	public static void log(String message) {
		log.info(message);
	}
	
	/**
	 * Log a localised event
	 * @param key the bundle key, like "evt.startup"
	 * @param caller the class of the caller e.g. this.getClass()
	 */
	public static String log(String key, Class caller) {
		String eventMessage = Util.getLocalMessage(key, caller);
		log.info(eventMessage);
		return eventMessage;
	}

	/**
	 * Log a localised event, with one placeholder
	 * @param key the bundle key, like "evt.startup"
	 * @param caller the class of the caller e.g. this.getClass()
	 * @param arg1 the argument to insert in the placeholder
	 */
	public static String log(String key, Class caller, String arg1) {
		String eventMessage = Util.getLocalMessage(key, caller);
		String msg = MessageFormatter.format(eventMessage, arg1);
		log.info(msg);
		return msg;
	}

	/**
	 * Log a localised event, with two placeholders
	 * @param key the bundle key, like "evt.startup"
	 * @param caller the class of the caller e.g. this.getClass()
	 * @param arg1 the argument to insert in the first placeholder
	 * @param arg2 the argument to insert in the second placeholder
	 */
	public static String log(String key, Class caller, String arg1, String arg2) {
		String eventMessage = Util.getLocalMessage(key, caller);
		String msg = MessageFormatter.format(eventMessage, arg1, arg2);
		log.info(msg);
		return msg;
	}

	/**
	 * Log a localised event, with many placeholders
	 * @param key the bundle key, like "evt.startup"
	 * @param caller the class of the caller e.g. this.getClass()
	 * @param args the arguments to insert in the placeholders
	 */
	public static String log(String key, Class caller, Object[] args) {
		String eventMessage = Util.getLocalMessage(key, caller);
		String msg = MessageFormatter.arrayFormat(eventMessage, args);
		log.info(msg);
		return msg;
	}

}

package net.villonanny.misc.captcha;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.entity.ServerFatalException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.log4j.Logger;

/**
 * Superclass of classes that handle the captcha.
 * The default behaviour is to log and exit with an exception.
 * Sublcasses should redefine the "process" method and maybe send an email or solve the captcha.
 */
public class BaseCaptchaHandler {
	private final static Logger log = Logger.getLogger(BaseCaptchaHandler.class);

	/**
	 * Creates the instance of the captcha handler according to the configuration
	 */
	public static void handleCaptcha(String page, HierarchicalConfiguration serverConfig) throws ConversationException {
		// CaptchaHandler configuration is optional for the default behaviour.
		// It can have parameters used by specialised versions.
		// <server ...>
		//    <captchaHandler class="SomeClass">
		//        <someParam/>
		//    </captchaHandler>
		String captchaClassName = serverConfig.getString("/captchaHandler/@class", "BaseCaptchaHandler");
		Class<BaseCaptchaHandler> handlerClass;
		BaseCaptchaHandler handler;
		try {
			handlerClass = (Class<BaseCaptchaHandler>) Class.forName(BaseCaptchaHandler.class.getPackage().getName() + "." + captchaClassName);
			handler = handlerClass.newInstance();
		} catch (Exception e) {
			EventLog.log("msg.captchaClassInvalid", BaseCaptchaHandler.class, captchaClassName);
			log.error(e);
			// Using default
			handlerClass = BaseCaptchaHandler.class;
			handler = new BaseCaptchaHandler();
		}
		handler.process(page, serverConfig);
	}
	
	/**
	 * Method that processes the captcha.
	 * Should be overridden.
	 * @param page
	 * @param serverConfig
	 * @throws ConversationException
	 */
	protected void process(String page, HierarchicalConfiguration serverConfig) throws ConversationException {
		// Just log it and abort with a ConversationException
		log.error("Aborting operation because of captcha. Page dump follows if debug is active");
		log.debug(page);
		throw new ServerFatalException("BaseCaptchaHandler.process Captcha found");
		//throw new ConversationException();
	}

}

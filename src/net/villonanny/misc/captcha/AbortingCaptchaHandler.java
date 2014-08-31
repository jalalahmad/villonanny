package net.villonanny.misc.captcha;

import net.villonanny.ConversationException;
import net.villonanny.entity.ServerFatalException;

import org.apache.commons.configuration.HierarchicalConfiguration;

/**
 * Terminate server on Captcha
 */
public class AbortingCaptchaHandler extends BaseCaptchaHandler {

	protected void process(String page, HierarchicalConfiguration serverConfig) throws ConversationException {
		try {
			super.process(page, serverConfig);
		} catch (Exception e) {
			// Ignore the ConversationException
		}
		throw new ServerFatalException("Captcha found");
	}

}

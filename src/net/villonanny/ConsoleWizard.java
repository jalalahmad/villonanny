package net.villonanny;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.misc.TravianVersion;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.log4j.Logger;
import org.slf4j.helpers.MessageFormatter;

/**
 * This class is the superclass of command-line (aka console) utilities that interact with users
 *
 */
public class ConsoleWizard {
	protected static final Logger log = Logger.getLogger(ConsoleWizard.class);
//	protected HttpClient client;
	protected Util util;
	protected static final boolean FAST = true; // TODO false?
	protected XMLConfiguration config;
	protected ConfigManager configManager;

	protected class ServerInfo {
		protected String loginUrl; // http://speed.travian.it/ or http://speed.travian.it/login.php
		protected String description;
		protected String languageCode;
		protected String baseVillageUrl; // http://speed.travian.it/dorf1.php
		protected String serverUrl; // http://speed.travian.it
		public TravianVersion travianVersion;
	}
	
	protected ConsoleWizard(String rootElementName, ConfigManager newConfigManager) {
		this.config = newConfigManager.getXmlConfiguration();
		this.configManager = newConfigManager;
		// Prepare initial configuration
		config.setExpressionEngine(new XPathExpressionEngine());
		config.setRootElementName(rootElementName);
		config.setEncoding(Util.getEncodingString());
		configureProxy();
	}
	
	protected void configureUtil(HierarchicalConfiguration serverConfig) {
		String language = serverConfig.getString(" @language", null);
		boolean setLanguage = (language!=null);
		util = new Util(serverConfig, configManager, new Console(configManager), setLanguage); // Do not set language or Console
	}

	protected void configureProxy() {
		boolean hasProxy = promptUserBinaryChoice("prompt.hasProxy", "prompt.yes", "prompt.no", "prompt.no");
		if (hasProxy) {
			config.addProperty(" proxy@enabled", "true");
			String host = null;
			do {
				host = promptUser("prompt.proxy.server");
				// Check host
				try {
					InetAddress address = InetAddress.getByName(host);
				} catch (UnknownHostException e) {
					sayKey("msg.unknownHost", host);
					host = null;
				}
			} while (host==null);
			int port = promptUserForInteger("prompt.proxy.port", "8080");
			config.addProperty("proxy hostName", host);
			config.addProperty("proxy hostPort", port);
			boolean authenticationRequired = promptUserBinaryChoice("prompt.proxy.authentication", "prompt.yes", "prompt.no", "prompt.no");
			if (authenticationRequired) {
				String username = promptUser("prompt.proxy.username");
				String password = promptUser("prompt.proxy.password");
				config.addProperty("proxy proxyUser", username);
				config.addProperty("proxy proxyPassword", password);
				boolean isNtlm = promptUserBinaryChoice("prompt.proxy.hasNtlm", "prompt.yes", "prompt.no", "prompt.no");
				if (isNtlm) {
					String defaultHostName = null;
					try {
						InetAddress localhostAddress = InetAddress.getLocalHost();
						if (localhostAddress!=null) {
							defaultHostName = localhostAddress.getCanonicalHostName();
						}
					} catch (UnknownHostException e) {
						// Ignore
					}
					String nthost = promptUser("prompt.proxy.ntlm.host", defaultHostName);
					String ntdomain = promptUser("prompt.proxy.ntlm.domain");
					config.addProperty("proxy NTHost", nthost);
					config.addProperty("proxy NTDomain", ntdomain);
				}
			}
		}
		
	}
	
	protected boolean promptUserBinaryChoice(String messageKey, String trueValueKey, String falseValueKey, String defaultValueKey) {
		String answer = null;
		String trueValue = Util.getLocalMessage(trueValueKey, this.getClass());
		String falseValue = Util.getLocalMessage(falseValueKey, this.getClass());
		String defaultValue = Util.getLocalMessage(defaultValueKey, this.getClass());
		do {
			String promptMessage = Util.getLocalMessage(messageKey, this.getClass());
			answer = Util.inputLine(promptMessage + String.format(" (%s/%s, default=%s)", trueValue, falseValue, defaultValue));
		} while (answer==null || (answer.length()>0 && !answer.equalsIgnoreCase(trueValue) && !answer.equalsIgnoreCase(falseValue)));
		if (answer.length()==0) {
			answer = defaultValue;
			System.out.println(answer);
		}
		return answer.equalsIgnoreCase(trueValue); 
	}
	
	protected String promptUser(String messageKey, String defaultValue) {
		String promptMessage = Util.getLocalMessage(messageKey, this.getClass());
		if (defaultValue!=null && defaultValue.trim().length()>0) {
			promptMessage += String.format(" (default=%s)", defaultValue);
		}
		return Util.inputLine(promptMessage, defaultValue);
	}

	protected String promptUser(String messageKey) {
		return promptUser(messageKey, null);
	}

	protected int promptUserForInteger(String messageKey, String defaultStringValue) {
		do {
			String integer = promptUser(messageKey, defaultStringValue);
			try {
				return Integer.parseInt(integer);
			} catch (NumberFormatException e) {
				sayKey("msg.notNumber", integer);
			}
		} while (true);
	}

	protected int promptUserForInteger(String messageKey) {
		return promptUserForInteger(messageKey, null);
	}

	protected String sayKey(String messageKey) {
		String message = Util.getLocalMessage(messageKey, this.getClass());
		System.out.println(message);
		return message;
	}

	protected String sayKey(String messageKey, String arg) {
		String message = Util.getLocalMessage(messageKey, this.getClass());
		String formattedMessage = MessageFormatter.format(message, arg);
		System.out.println(formattedMessage);
		return formattedMessage;
	}

	protected String sayKey(String messageKey, String arg1, String arg2) {
		String message = Util.getLocalMessage(messageKey, this.getClass());
		String formattedMessage = MessageFormatter.format(message, arg1, arg2);
		System.out.println(formattedMessage);
		return formattedMessage;
	}
	
	protected URL getUrl(String urlString, String errorKey) {
		try {
			return new URL(urlString);
		} catch (MalformedURLException e) {
			String message = sayKey(errorKey, urlString);
			log.error(message, e);
			return null;
		}
	}

	protected boolean fetchServerInfo(ServerInfo info) {
		try {
			// Fetch server login page
			String loginPage = util.httpGetPageNoLogin(info.loginUrl, FAST);
			if (!util.isLoginPage(loginPage)) {
				sayKey("msg.noLoginPage");
				return false;
			}
			// Check language
			// <meta name="content-language" content="en">
			// <meta name="content-language" content="it">
			// Pattern p = Pattern.compile("(?s)(?i)<meta *name=\"content-language\" *content=\"([^\"]*)\"[^>]*>");
			Pattern p = util.getPattern("consoleWizard.checkLanguage");
			Matcher m = p.matcher(loginPage);
			String languageString = null;
			if (m.find()) {
				 languageString = m.group(1);
				 log.debug("Language on login page is " + languageString);
			}
			if (languageString==null) {
				languageString = "en"; // Default
				sayKey("msg.noLanguage");
				// Keep going
			}
			info.languageCode = languageString;
			// Fetch title
			// p = Pattern.compile("(?s)(?i)<title>([^<]*)</title>");
			p = util.getPattern("consoleWizard.fetchTitle");
			m = p.matcher(loginPage);
			if (m.find()) {
				 info.description = m.group(1);
				 log.debug("Description on login page is " + info.description);
			}
			// Travian version detection has been removed because unreliable
			// Find travian version
			info.travianVersion = TravianVersion.DEFAULT;
			return true;
		} catch (ConversationException e) {
			sayKey("msg.urlConnectionFailed");
			log.error(e);
			return false;
		}
	}
	
	protected void doubleOut(PrintWriter out, String line) {
		out.println(line);
		System.out.println(line);
	}


}

package net.villonanny;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.slf4j.helpers.MessageFormatter;

public class AutoConfigurator extends ConsoleWizard {
	static final Logger log = Logger.getLogger(AutoConfigurator.class);
	protected static final String TAB = "    "; // Indent for pretty printing
	
//	/**
//	 * @param args
//	 * @throws ConfigurationException 
//	 */
//	public static void main(String[] args) throws Exception {
//		// File configFile = new File(CONFIGDIR + File.separatorChar + ConfigManager.CONFIGFILE + ".tmp"); // TODO remove .tmp
//		// configFile.delete(); // TODO remove
//		// System.out.println(configFile.getAbsolutePath());
//		XMLConfiguration config = new XMLConfiguration();
//		config.setExpressionEngine(new XPathExpressionEngine());
//		config.setRootElementName("configuration");
//		config.setProperty(" @reloadSeconds", "30");
//		config.addProperty(" proxy@enabled", "false");
//		config.addProperty("proxy hostName", "localhost");
//		config.addProperty("proxy hostPort", "3128");
//		config.addProperty(" dateFormat", "EEE dd MMMM yyyy HH:mm:ss Z");
//		config.addProperty(" server@desc", "T3.com s4 (Testing)");
//		config.addProperty("server @language", "en");
//		config.addProperty("server @enabled", "true");
//		config.addProperty("server @constructionQueues", "1");
//		//StringWriter swriter = new StringWriter();
//		//config.save(swriter);
//		//prettyPrintFile(swriter.toString(), configFile);
//	}
	
	
	public AutoConfigurator(ConfigManager configManager) {
		super("configuration", configManager);
//		config.addProperty(" dateFormat", "EEE dd MMMM yyyy HH:mm:ss Z");
		// To add a tag for the first time, you can add its attribute and the tag will be added as well: " server@enabled"
		config.addProperty(" server@enabled", "true");
		// If you need to add a second attribute, you should use the tag as the index else you will get another tag created, 
		// so "server @pauseLimit" and not " server@pauseLimit"
		// config.addProperty("server @pauseLimit", "86400"); // 24h
		//
		SubnodeConfiguration serverConfig = config.configurationAt("server");
		super.util = new Util(serverConfig, configManager); // Do not set language or Console
	}
	
	public boolean configure() {
		File configFile = configManager.getXmlConfigFile();
		ServerInfo sinfo = new ServerInfo();
		// Ask for login url, repeating when url not valid
		while (!askLoginUrl(sinfo)) {
			// Nothing here
		}
		// Set the transator language
		util.getTranslator().setLanguage(sinfo.languageCode);
		// Load language configuration
		try {
			configManager.loadLanguageConfig(sinfo.languageCode);
		} catch (ConfigurationException e) {
			String message = sayKey("msg.noLanguageConfig", sinfo.languageCode, configFile.getParentFile().getAbsolutePath());
			log.error(message, e);
			return false;
		}
		//
		config.addProperty("server loginUrl", sinfo.loginUrl);
		config.addProperty("server @desc", sinfo.description);
		config.addProperty("server @language", sinfo.languageCode);
		config.addProperty("server @version", sinfo.travianVersion.name().toLowerCase());
		util.setTravianVersion(sinfo.travianVersion);
		String firstPage = "";
		boolean goodLogin = false;
		while (!goodLogin) {
			// Ask for credentials
			String username = super.promptUser("prompt.typeUsername");
			String password = super.promptUser("prompt.typePassword");
			// Create server in configuration
			config.addProperty("server user", username);
			config.addProperty("server password", password);
			// Login and fetch first page
			try {
				firstPage = util.httpGetPage(sinfo.baseVillageUrl, FAST);
				goodLogin = true;
			} catch (Exception e) {
				log.error("", e);
				sayKey("msg.loginFailed");
				config.clearProperty("server/user");
				config.clearProperty("server/password");
			}
		}
		// Find all villages
		// Pattern p = Pattern.compile("(?s)(?i)<a href=\"(\\?newdid=[^\"]*)\"[^>]*>([^<]*)</a>"); // <a href="?newdid=134564" class="active_vl">[02] MyVillage</a>
		Pattern p = util.getPattern("autoConfigurator.findAllVillages");
		Matcher m = p.matcher(firstPage);
		boolean manyVillages = false;
		while (m.find()) {
			String villageUrl = sinfo.baseVillageUrl + m.group(1);
			String villageName = m.group(2);
			addVillage(villageUrl, villageName);
			manyVillages = true;
			log.debug(MessageFormatter.format("Found village \"{}\" at {}", villageName, villageUrl));
		}
		if (!manyVillages) {
			// Adding default village
			String villageUrl = sinfo.baseVillageUrl;
			String villageName = "";
			// Find village name
			// p = Pattern.compile("(?s)(?i)<h1>([^<]*)</h1>"); // <h1>[01] MyVillage</h1>
			p = util.getPattern("autoConfigurator.findVillageName");
			m = p.matcher(firstPage);
			if (m.find()) {
				villageName = m.group(1);
			} else {
				log.error("Can't find village name"); // TODO better message
			}
			addVillage(villageUrl, villageName);
			log.debug(MessageFormatter.format("Found village \"{}\" at {}", villageName, villageUrl));
		}
		
//		// Ask for construction queues
//		int queues = promptUserForInteger("prompt.typeConstructionQueues");
//		config.addProperty("server @constructionQueues", queues);
		
		// Fetch tribe
		String tribe = "INSERT_TRIBE_NAME_HERE";
		try {
			// p = Pattern.compile("(?s)(?i)<a href=\"(spieler.php\\?uid=[^\"]*)\">"); // <a href="spieler.php?uid=19748">
			p = util.getPattern("autoConfigurator.fetchTribe1");
			m = p.matcher(firstPage);
			if (m.find()) {
				String profilePageUrl = sinfo.serverUrl + "/" + m.group(1);
				String profilePage = util.httpGetPage(profilePageUrl, FAST);
				String tribePattern = util.getTranslator().getMatchingPattern("tribe");
				// p = Pattern.compile("(?s)(?i)<td>"+tribeKeyword+":</td><td>([^<]*)</td>"); // <td>Volk:</td><td>Romer</td>
				p = util.getPattern("autoConfigurator.fetchTribe2", tribePattern);
				m = p.matcher(profilePage);
				if (m.find()) {
					tribe = m.group(1); // "Romer"
				} else {
					log.error("Can't find tribe type"); // Just ignore
				}
			} else {
				log.error("Can't find profile page and tribe type"); // Just ignore
			}
		} catch (ConversationException e1) {
			log.error("Can't find tribe type", e1); // Just ignore
		}
		config.addProperty("server @tribe", tribe);
		
		// Ensure unique id's
		try {
			configManager.ensureUniqueIds();
		} catch (DuplicateUidException e1) {
			// This should never happen
			log.error("Internal error: duplicate uid", e1);
			// Keep going
		}
		
		// Build configuration file
		try {
			// Convert configuration to string
			StringWriter swriter = new StringWriter();
			config.save(swriter);
			// PrettyPrint and save to output file
			prettyPrintFile(swriter.toString(), configFile);
		} catch (Exception e) {
			log.error("Failed to save new configuration file", e);
			sayKey("msg.failedToSaveNewConfig");
			sayKey("msg.retry");
			return false;
		}
		return true;
	}
	
	private void addVillage(String villageUrl, String villageName) {
		config.addProperty("server village@desc", villageName); // Create a new <village> tag with a desc attribute
		config.addProperty("server/village[last()] @enabled", true); // Add elements to the <village> just created
		config.addProperty("server/village[last()] url", villageUrl);
		// <strategy desc="Grow Cheapest Field" class="FieldGrowth" enabled="true"/>
		config.addProperty("server/village[last()] strategy@desc", "Grow Cheapest Field");
		config.addProperty("server/village[last()]/strategy[last()] @class", "FieldGrowth");
		config.addProperty("server/village[last()]/strategy[last()] @enabled", true);
		// <strategy class="GrowItem" enabled="true">
        // <item id="26" desc="Main Building" maxLevel="10"/>
		// </strategy>
		config.addProperty("server/village[last()] strategy@desc", "Grow Main Building");
		config.addProperty("server/village[last()]/strategy[last()] @class", "GrowItem");
		config.addProperty("server/village[last()]/strategy[last()] @enabled", true);
		config.addProperty("server/village[last()]/strategy[last()] item@id", 26);
		config.addProperty("server/village[last()]/strategy[last()]/item[last()] @desc", "Main Building");
		config.addProperty("server/village[last()]/strategy[last()]/item[last()] @maxLevel", 10);
	}
	
	protected boolean askLoginUrl(ServerInfo sinfo) {
		sinfo.loginUrl = super.promptUser("prompt.typeLoginUrl"); // http://speed.travian.it/login.php
		if (sinfo.loginUrl==null || sinfo.loginUrl.length()==0) {
			return false;
		}
		// Check if address starts with protocol
		String leader = "http://";
		if (!sinfo.loginUrl.toLowerCase().startsWith(leader)) {
			sinfo.loginUrl = leader + sinfo.loginUrl;
		}
		// Check that url is valid
		URL url = super.getUrl(sinfo.loginUrl, "msg.malformedUrlTyped");
		if (url==null) {
			return false;
		}
		// Check that host exists
		String host = url.getHost();
		try {
			InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			String message = sayKey("msg.unknownHost", host);
			log.error(message, e);
			return false;
		}
		sinfo.serverUrl = url.getProtocol() + "://" + url.getHost() + (url.getPort()>=0?":"+url.getPort():"");
		// Compile base url for villages, e.g. http://speed.travian.it/dorf1.php
		sinfo.baseVillageUrl = sinfo.serverUrl + "/dorf1.php"; 
		// Look for language and description
		sinfo.loginUrl = sinfo.loginUrl;
		return fetchServerInfo(sinfo);
	}

	/**
	 * Saves a flat XML string to an output File, after indenting tags
	 * @param flatXml
	 * @param outputFile
	 * @throws Exception 
	 */
	private void prettyPrintFile(String flatXml, File outputFile) throws Exception {
		// The algorithm makes some strong assumptions on the input format. 
		// It might not work anymore in a future version of Commons Configuration
		StringBuffer indent = new StringBuffer();
		PrintWriter out = new PrintWriter(outputFile, Util.getEncodingString());
		BufferedReader in = new BufferedReader(new StringReader(flatXml));
		String line; // = in.readLine(); // <?xml version="1.0" encoding="UTF-8" standalone="no"?>
		int skipInitialLines=1;
		// Pattern singleOpenTag = Pattern.compile("^<[^/]*>$"); // e.g. <configuration reloadSeconds="30">
		Pattern singleOpenTag = util.getPattern("autoConfigurator.singleOpenTag");
		// Pattern singleCloseTag = Pattern.compile("^</[^>]*>$"); // e.g. </configuration>
		Pattern singleCloseTag = util.getPattern("autoConfigurator.singleCloseTag");
		while ((line=in.readLine())!=null) {
			Matcher m = singleOpenTag.matcher(line);
			if (m.find()) {
				doubleOut(out, indent + line);
				if (skipInitialLines--<=0) {
					indent.append(TAB);
				}
				continue;
			} 
			m = singleCloseTag.matcher(line);
			if (m.find()) {
				indent.setLength(indent.length() - TAB.length());
			} 
			doubleOut(out, indent + line);
		}
		out.close();
	}
	

}

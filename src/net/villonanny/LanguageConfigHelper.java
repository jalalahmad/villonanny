package net.villonanny;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

public class LanguageConfigHelper extends ConsoleWizard {
	static final Logger log = Logger.getLogger(LanguageConfigHelper.class);
	static final String DOTCOM_BUILDINGS_TABLE_URL = "http://help.travian.com/index.php?type=faq&mod=300";
	static final String DOTCOM_TROOPS_ROMAN_URL = "http://help.travian.com/index.php?type=faq&mod=410";
	static final String DOTCOM_TROOPS_GALLIC_URL = "http://help.travian.com/index.php?type=faq&mod=420";
	static final String DOTCOM_TROOPS_TEUTONIC_URL = "http://help.travian.com/index.php?type=faq&mod=430";
	static final String OUTFILE = "result.txt";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			ConfigManager configManager = new ConfigManager();
			configManager.loadLanguageConfig("en");
			Util.setUtf8(args);
			LanguageConfigHelper helper = new LanguageConfigHelper(configManager);
			helper.execute();
		} catch (Exception e) {
			String message = Util.getLocalMessage("msg.exitingWithErrors", LanguageConfigHelper.class);
			log.error(message, e);
			System.out.println(message);
		}
	}

	public LanguageConfigHelper(ConfigManager configManager) {
		super("server", configManager);
		config.addProperty(" @enabled", "true");
		config.addProperty(" @language", "en");
		super.configureUtil(config);
	}


	private void execute() throws ConversationException {
		PrintWriter out;
		File outFile = new File(OUTFILE);
		try {
			out = new PrintWriter(outFile, Util.getEncodingString());
		} catch (Exception e1) {
			String message = sayKey("msg.failedToSaveNewConfig");
			throw new FatalException(message, e1);
		}
		// Prompt for url
		String topLevelDomain = null;
		while (topLevelDomain==null) {
			topLevelDomain = promptUser("prompt.travianUrl", "www.travian.de");
			topLevelDomain = pruneTopLevelDomainInput(topLevelDomain.toLowerCase());
		}
		fetchBuildings(out, topLevelDomain);
		fetchTroops(out, topLevelDomain);
		out.close();
		System.out.println();
		sayKey("evt.fileCreated", outFile.getAbsolutePath());
		sayKey("evt.configHelperEnd", outFile.getAbsolutePath());
	}

	private void fetchBuildings(PrintWriter out, String topLevelDomain) throws ConversationException {
		out.println("\n# Buildings and Fields\n");
		String localisedAddress = createLocalisedAddress(DOTCOM_BUILDINGS_TABLE_URL, topLevelDomain);
		// Fetch and parse english building table
		Map<String, String> englishTable = fetchBuildingTable(DOTCOM_BUILDINGS_TABLE_URL);
		// Fetch and parse localised language table
		Map<String, String> localTable = fetchBuildingTable(localisedAddress);
		// Print
		System.out.println();
		int fromId = 1; // First key
		int toId = englishTable.size();
//		for (String id : englishTable.keySet()) {
		for (int id=fromId; id<=toId; id++) {
			String stringId = String.valueOf(id);
			String englishValue = englishTable.get(stringId);
			if (englishValue==null || englishValue.trim().length()==0) {
				log.debug("No value in english table for id=" + id);
				continue;
			}
			String key;
			try {
				key = util.getTranslator().getKeyword(englishValue);
				String localValue = localTable.get(stringId);
				doubleOut(out, String.format("key.%s = %s", key, localValue));
			} catch (TranslationException e) {
				// Ignore
				log.debug("Value \"" + englishValue + "\" not found in language configuration file (skipped)");
			}
		}
	}
	
	private void fetchTroops(PrintWriter out, String topLevelDomain) throws ConversationException {
		out.println("\n# Troops\n");
		// Fetch and parse english table
		fetchOneTroop("romans", DOTCOM_TROOPS_ROMAN_URL, topLevelDomain, out);
		out.println("");
		fetchOneTroop("gauls", DOTCOM_TROOPS_GALLIC_URL, topLevelDomain, out);
		out.println("");
		fetchOneTroop("teutons", DOTCOM_TROOPS_TEUTONIC_URL, topLevelDomain, out);
		out.println("");
	}	
	
	private void fetchOneTroop(String tribeName, String englishTroopUrl, String topLevelDomain, PrintWriter out) throws ConversationException {
		Map<String, String> englishTable = fetchTroopsTable(englishTroopUrl);
		String localisedAddress = createLocalisedAddress(englishTroopUrl, topLevelDomain);
		// Fetch and parse localised language table
		Map<String, String> localTable = fetchTroopsTable(localisedAddress);
		// Print
		System.out.println();
		int fromId = 1; // First key
		int toId = englishTable.size();
		for (int id=fromId; id<=toId; id++) {
			String stringId = String.valueOf(id);
			String englishValue = englishTable.get(stringId);
			if (englishValue==null || englishValue.trim().length()==0) {
				log.debug("No value in english table for id=" + id);
				continue;
			}
			try {
				String key = util.getTranslator().getKeyword(englishValue, tribeName).replace('/', '.');
				String localValue = localTable.get(stringId);
				doubleOut(out, String.format("key.%s.%s = %s", tribeName, key, localValue));
			} catch (TranslationException e) {
				// Ignore
				log.debug("Value \"" + englishValue + "\" not found in language configuration file (skipped)");
			}
		}
	}

	private String createLocalisedAddress(String dotComUrl, String newTopLevelDomain) {
		// http://help.travian.com/index.php?type=faq&mod=300
		StringBuffer result = new StringBuffer(dotComUrl);
		int start = dotComUrl.indexOf("com");
		int end = start + "com".length();
		result.replace(start, end, newTopLevelDomain);
		return result.toString();
	}

	private String pruneTopLevelDomainInput(String topLevelDomain) {
		String result = topLevelDomain;
		int pos = result.indexOf("travian.");
		if (pos>-1) {
			result = result.substring(pos + "travian.".length());
		} else {
			sayKey("msg.malformedUrlTyped", topLevelDomain);
			return null;
		}
		return result;
	}

	private Map<String, String> fetchBuildingTable(String buildingsTableUrl) throws ConversationException {
		Map<String, String> result = new HashMap<String, String>();
		// Load page
		String page = util.httpGetPageNoLogin(buildingsTableUrl, FAST);
		// There isn't any better identification than <td>gid</td>
		// Pattern p = Pattern.compile("(?s)(?i)<td>gid</td>"); // <td>gid</td>
		Pattern p = util.getPattern("languageConfigHelper.buildingsTable");
		Matcher m = p.matcher(page);
		if (!m.find()) {
			String message = sayKey("msg.noBuildingTable", buildingsTableUrl);
			throw new FatalException(message);
		}
		int startOffset = m.end();
//		// We found the header, so we confine the search to the table
//		p = Pattern.compile("(?s)(?i)</table>");
//		m = p.matcher(page);
//		if (!m.find(startOffset)) {
//			String message = sayKey("msg.noBuildingTable", buildingsTableUrl);
//			throw new FatalException(message);
//		}
		int endOffset = m.regionEnd();
		// Find table elements
		// p = Pattern.compile("(?s)(?i)<td>(\\d+)</td>[^<]*<td>([^<]+)</td>"); // <td>1</td> 	<td>Woodcutter</td>
		p = util.getPattern("languageConfigHelper.building");
		m = p.matcher(page);
		m.region(startOffset, endOffset); // Search confined to the table
		while (m.find()) {
			String buildingId = m.group(1);
			String buildingName = m.group(2);
			if (!result.containsKey(buildingId)) {
				result.put(buildingId, buildingName);
			}
		}
		return result;
	}

	private Map<String, String> fetchTroopsTable(String tableUrl) throws ConversationException {
		Map<String, String> result = new HashMap<String, String>();
		// Load page
		String page = util.httpGetPageNoLogin(tableUrl, FAST);
		// Start of table
		// Pattern p = Pattern.compile("(?s)(?i)</h1>*<table"); // First table after header1: </h1><table
		Pattern p = util.getPattern("languageConfigHelper.tableStart");
		Matcher m = p.matcher(page);
		if (!m.find()) {
			String message = sayKey("msg.noTroopsTable", tableUrl);
			throw new FatalException(message);
		}
		int startOffset = m.end();
		// We found the header, so we confine the search to the table
		// p = Pattern.compile("(?s)(?i)</table>");
		p = util.getPattern("languageConfigHelper.tableEnd");
		m = p.matcher(page);
		if (!m.find(startOffset)) {
			String message = sayKey("msg.noTroopsTable", tableUrl);
			throw new FatalException(message);
		}
		int endOffset = m.regionEnd();
		// Find table elements
		// p = Pattern.compile("(?s)(?i)<a href=\"#tid(\\d\\d?)\">([^<]*)</a>"); // <a href="#tid1">Legionnaire</a>
		p = util.getPattern("languageConfigHelper.tableElements");
		m = p.matcher(page);
		m.region(startOffset, endOffset); // Search confined to the table
		int id=1;
		while (m.find()) {
			String troopId = m.group(1); // We don't use it for now
			troopId = String.valueOf(id++);
			String troopName = m.group(2);
			if (!result.containsKey(troopId)) {
				result.put(troopId, troopName);
			}
		}
		return result;
	}

}

package net.villonanny;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.villonanny.misc.TravianVersion;

import org.apache.commons.configuration.AbstractFileConfiguration;
import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.FileConfiguration;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.NodeCombiner;
import org.apache.commons.configuration.tree.UnionCombiner;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
import org.slf4j.helpers.MessageFormatter;


public class ConfigManager {
	private static final Logger log = Logger.getLogger(ConfigManager.class);
	public static final String DEFAULT_CONFIGFILE = "configuration.xml";
	public static final String CONFIGDIR = "config";
	private static final int RELOAD_TIMEOUT_SECONDS = 4; // Check no faster than every 4 seconds

	private HierarchicalConfiguration mainConfig = null;
	private XMLConfiguration xmlPart = null;
	private String xmlConfigFilename;
	private Map<TravianVersion, PropertiesConfiguration> htmlPatterns = new HashMap<TravianVersion, PropertiesConfiguration>();

	public ConfigManager() {
		this(null);
	}
	
	public ConfigManager(String filename) {
		this.xmlConfigFilename = filename;
		NodeCombiner combiner = new UnionCombiner();
		CombinedConfiguration tmpConfig = new CombinedConfiguration(combiner);
		tmpConfig.setExpressionEngine(new XPathExpressionEngine());
		tmpConfig.setThrowExceptionOnMissing(true); // Throw NoSuchElementException when config element is missing
		tmpConfig.setForceReloadCheck(true);
		xmlPart = new XMLConfiguration();
		xmlPart.setEncoding(Util.getEncodingString());
		xmlPart.setExpressionEngine(new XPathExpressionEngine());
		xmlPart.setThrowExceptionOnMissing(true); // Throw NoSuchElementException when config element is missing
		tmpConfig.addConfiguration(xmlPart);
		mainConfig = tmpConfig;
		// Load html pattern files for all travian versions
		try {
			TravianVersion[] definedVersions = TravianVersion.values();
			for (int i = 0; i < definedVersions.length; i++) {
				TravianVersion version = definedVersions[i];
				PropertiesConfiguration config = new PropertiesConfiguration(version.filename);
				htmlPatterns.put(version, config);
			}
		} catch (ConfigurationException e) {
			String message = "Internal error: html patterns not found";
			log.error(message, e);
			throw new FatalException(message);
		}
	}
	
	public boolean isEmpty() {
		return mainConfig == null || xmlPart == null;
	}
	
	public Set<String> getAllUids() {
		return allUids;
	}	
	
	public Set<String> getAllEnabledUids() {
		return allEnabledUids;
	}
	
	public boolean ensureUniqueIds() throws DuplicateUidException {
		boolean modified = false;
		// Every <village> and every <strategy> must have a unique identifier
		modified |= ensureUniqueIds(xmlPart, "/server/village", "v");
		modified |= ensureUniqueIds(xmlPart, "/server/village/strategy", "s");
		modified |= ensureUniqueIds(xmlPart, "/server/serverStrategy", "ss");
		return modified;
	}
	
	private boolean ensureUniqueIds(XMLConfiguration config, String configurationSelector, String prefix) throws DuplicateUidException {
		boolean modified = false;
		boolean duplicate = false;
		boolean waitUidError = false;
		boolean runwhileUidError = false;
//		String prefix = String.valueOf(configurationSelector.charAt(2));
		List<SubnodeConfiguration> configNodes = config.configurationsAt(configurationSelector);
		for (SubnodeConfiguration configNode : configNodes) {
			String currentUid = configNode.getString("/@uid", null);
			if (currentUid == null) {
				String newUid = Util.getNewUid(prefix);
				configNode.setProperty(" @uid", newUid);
				modified = true;
				currentUid = newUid;
				allUids.add(currentUid);
				log.debug(currentUid+" enabled="+configNode.getBoolean("/@enabled", true));
				if(configNode.getBoolean("/@enabled", true)){
					allEnabledUids.add(newUid);
				}
			} else if (!allUids.add(currentUid)) {
				// Duplicate uid
				duplicate = true;
				EventLog.log("msg.duplicateId01", ConfigManager.class, currentUid);
				EventLog.log("msg.duplicateId02", ConfigManager.class, 
						new String[] {currentUid, configNode.getRoot().getName(), configNode.getString("/@desc", "")});
			}else {
				log.debug(currentUid+" enabled="+configNode.getBoolean("/@enabled", true));
				if(configNode.getBoolean("/@enabled", true)){
					allEnabledUids.add(currentUid);
				}
			}

			String waitingFor = configNode.getString("/@waitFor", null);
			String runWhile = configNode.getString("/@runWhile", null);

			if(waitingFor != null ){
				// check for waiting for resourcs or buildings
				String minLevel = configNode.getString("/@minLevel", null);
				if (minLevel != null) {
					// String keyString = util.getTranslator().getKeyword(nameString); // TranslationException if buildingName not found
					// configManager created before Translator set so cannot check valid at this point
				} else if(!getAllUids().contains(waitingFor)){
					// waitFor uid missing
					if (configNode.getBoolean("/@lookAhead", false)) { //when waiting for a strategy below the current
						EventLog.log("msg.waitforUid "+waitingFor+" Missing. lookAhead set"); //this makes it possible to override this test. It would be better to run 2 passes, but not sure its worth the effort. fofo
					}
					else {
						EventLog.log("msg.waitforUid "+waitingFor+" Missing", ConfigManager.class, configNode.getString("/@desc", ""),waitingFor);
						waitUidError = true;
					}
				} else if(!getAllEnabledUids().contains(waitingFor)){
					// waitFor uid disabled
					EventLog.log("msg.waitforUidDisabled", ConfigManager.class, configNode.getString("/@desc", ""),waitingFor);
				}
			}
			if(runWhile != null && !getAllUids().contains(runWhile)){
				if(!getAllUids().contains(runWhile)){
					// runWhile uid missing
					if (configNode.getBoolean("/@lookAhead", false)) {
						EventLog.log("msg.runWhileUid "+runWhile+" Missing. lookAhead set"); //this makes it possible to override this test. It would be better to run 2 passes, but not sure its worth the effort. fofo
					} else {
						EventLog.log("msg.runwhileUid "+runWhile+" Missing",
								ConfigManager.class, configNode.getString(
										"/@desc", ""), runWhile);
						runwhileUidError = true;
					}
					
				} else if(!getAllEnabledUids().contains(runWhile)){
					// runWhile uid disabled
					EventLog.log("msg.runwhileUidDisabled", ConfigManager.class, configNode.getString("/@desc", ""),runWhile);
				}
			}
		}
		if (duplicate) {
			throw new DuplicateUidException(modified);
		}
		if (waitUidError || runwhileUidError) {
			//should use some other exception - UidException?
			throw new DuplicateUidException(modified);
		}
		return modified;
	}
	
	public boolean isConfigurationThere() {
		File configFile = getXmlConfigFile();
		return configFile.exists();
	}

	public void loadConfiguration() {
		String s = "Loading configuration..."; 
		log.info(s);
		EventLog.log(s);
		try {
			xmlPart.load(xmlConfigFilename);
			xmlPart.setFileName(xmlConfigFilename);
			log.debug("Setting reload interval to " + RELOAD_TIMEOUT_SECONDS);
			FileChangedReloadingStrategy reloadingStrategy = new FileChangedReloadingStrategy();
			reloadingStrategy.setRefreshDelay(RELOAD_TIMEOUT_SECONDS * 1000);
			((FileConfiguration) xmlPart).setReloadingStrategy(reloadingStrategy);
			ConfigurationListener listener = new ConfigurationListener() {
				public void configurationChanged(ConfigurationEvent event) {
					try {
						if (event.getType() == AbstractFileConfiguration.EVENT_RELOAD && !event.isBeforeUpdate()) {
							EventLog.log("Configuration reloaded");
						}
					} catch (Exception e) {
						log.error("Failed to reload configuration", e);
					}
				}
			};
			xmlPart.addConfigurationListener(listener);
			loadLanguageConfig(new File(new java.net.URI(xmlPart.getURL().toString())).getParentFile());
		} catch (ConfigurationException e) {
			Util.log("Invalid configuration", e);
			throw new FatalException("Invalid configuration", e);
		} catch (Exception e) {
			String message = "Failed to load configuration";
			EventLog.log(message);
			throw new FatalException(message, e);
		}
	}

	/** Add the language configuration to a combined configuration.
	 * @param langFolder the folder where language configuration files can be found
	 * @param allConfigs the combined configuration that will be filled with language files
	 * @return the language codes loaded
	 * @throws URISyntaxException
	 * @throws ConfigurationException
	 */
	public Set<String> loadLanguageConfig(File langFolder) throws URISyntaxException, ConfigurationException {
		String s;
		Set<String> languageCodes = new HashSet<String>();
		// Language files
		// Cerco il file principale ... e carico tutti i
		// lang-*.properties della stessa folder
		FilenameFilter filter = new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        if (name.startsWith("lang-") && name.endsWith(".properties")) {
		        	return true;
		        }
		        return false;
		    }
		};

		String[] languages = langFolder.list(filter);
		for (String language : languages) {
			String langName = language.substring(language.indexOf("-") + 1, language.indexOf(".properties"));
			languageCodes.add(langName);
			s = "Adding '" + langName + "' language pack";
			log.info(s);
			// EventLog.log(s);
			// Aggiungo la configurazione per ogni lingua
			// Each language configuration is a node named langName, so the property will be accessed as /<langName>/<propertyName>
			String encodingString = Util.getEncodingString();
			PropertiesConfiguration pconf = new PropertiesConfiguration();
			pconf.setEncoding(encodingString);
			pconf.setFileName(language);
			pconf.load();
//			pconf.setEncoding(Util.getEncodingString());
			pconf.setThrowExceptionOnMissing(true); // Throw NoSuchElementException when config element is missing
			((CombinedConfiguration) mainConfig).addConfiguration(pconf, langName, langName);
		}
		return languageCodes;
	}
	
	/** Load a single language configuration file into the main configuration, if not there already.
	 * @param languageCode the 2-letter language code
	 * @param languageFolder the folder where the configuration file is kept
	 * @throws ConfigurationException if the configuration can not be loaded
	 */
	public void loadLanguageConfig(String languageCode) throws ConfigurationException {
		if (hasLanguage(languageCode)) {
			log.debug(MessageFormatter.format("Configuration for language \"{}\" already loaded; skipping...", languageCode));
			return;
		}
		String languageFile = getLanguageFolderFile().getAbsolutePath() + File.separatorChar + "lang-" + languageCode + ".properties";
		String encodingString = Util.getEncodingString();
		PropertiesConfiguration pconf = new PropertiesConfiguration();
		pconf.setEncoding(encodingString);
		pconf.setFileName(languageFile);
		pconf.load();
		((CombinedConfiguration) mainConfig).addConfiguration(pconf, languageCode, languageCode);
		
	}
	
	private File getLanguageFolderFile() {
		return new File(CONFIGDIR).getAbsoluteFile();
	}

	public boolean hasLanguage(String languageCode) {
		return !mainConfig.subset(languageCode).isEmpty();
	}
	
	/**
	 * This method should trigger a reload when the configuration is changed, but it seems to be subject to race conditions.
	 * Better let the reload happen on property access, as by default.
	 * @deprecated
	 */
	public void reloadIfChanged() {
		// Will reload only if file is changed
		// Will check file timestamp only if RELOAD_TIMEOUT_SECONDS have passed since last check
		String previousNdc = NDC.pop();
		try {
			xmlPart.reload();
		} catch (Throwable e) {
			log.error("Unexpected error while reloading configuration (ignored)", e);
		}
		NDC.push(previousNdc);
	}

	/**
	 * @param key
	 * @return
	 * @see org.apache.commons.configuration.HierarchicalConfiguration#configurationsAt(java.lang.String)
	 */
	public List configurationsAt(String key) {
		return mainConfig.configurationsAt(key);
	}
	
	/**
	 * @param key
	 * @return
	 * @see org.apache.commons.configuration.AbstractConfiguration#getInt(java.lang.String)
	 */
	public int getInt(String key) {
		return mainConfig.getInt(key);
	}

	public int getInt(String key, int defaultValue) {
		return mainConfig.getInt(key, defaultValue);
	}

	/**
	 * @param key
	 * @return
	 * @see org.apache.commons.configuration.AbstractConfiguration#getString(java.lang.String)
	 */
	public String getString(String key) {
		return mainConfig.getString(key);
	}

	/**
	 * @param key
	 * @return
	 * @see org.apache.commons.configuration.AbstractConfiguration#getString(java.lang.String)
	 */
	public String getString(String key, String defaultValue) {
		return mainConfig.getString(key, defaultValue);
	}

	/**
	 * @param key
	 * @return
	 * @see org.apache.commons.configuration.AbstractConfiguration#getBoolean(java.lang.String)
	 */
	public boolean getBoolean(String key) {
		return mainConfig.getBoolean(key);
	}

	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 * @see org.apache.commons.configuration.AbstractConfiguration#getBoolean(java.lang.String, boolean)
	 */
	public boolean getBoolean(String key, boolean defaultValue) {
		return mainConfig.getBoolean(key, defaultValue);
	}
	
	public void addListener(ConfigurationListener listener) {
		xmlPart.addConfigurationListener(listener);
	}
	
	/**
	 * Returns the keys (without the "key." prefix) for the given language code, i.e. crop, wood, ...
	 * @param languageCode
	 * @return
	 */
	public Iterator<String> getLanguageKeys(String languageCode) {
		return getLanguageKeys(languageCode, null);
	}

	/**
	 * Returns the keys (without the "key.<prefix>" prefix) for the given language code, i.e. troop1, troop2, ...
	 * @param languageCode
	 * @param prefix the element of the keyword following "key."
	 * @return
	 */
	public Iterator<String> getLanguageKeys(String languageCode, String prefix) {
		return mainConfig.subset(languageCode + "/key" + (prefix!=null?"/"+prefix:"")).getKeys();
	}

	protected XMLConfiguration getXmlConfiguration() {
		return xmlPart;
	}

	/**
	 * Returns all values defined for a given key
	 * @param key
	 * @return
	 */
	public List<String> getStringList(String key) {
		return mainConfig.getList(key);
	}
	
	public File getXmlConfigFile() {
		return new File(CONFIGDIR + File.separatorChar + getXmlConfigFilename());
	}

	public void saveXmlConfiguration() {
		File originalFile = xmlPart.getFile();
		// Backup original file by making a copy
		try {
			File backupFile = File.createTempFile(xmlConfigFilename.split("\\.")[0] + ".backup.", ".xml", new File(CONFIGDIR));
			try {
				Util.copyFile(originalFile, backupFile);
			} catch (IOException e) {
				log.error(String.format("Copy failed from '%s' to '%s'", originalFile.getCanonicalFile(), backupFile.getCanonicalFile()));
				throw e;
			}
			EventLog.log("evt.fileCreated", ConfigManager.class, backupFile.getCanonicalPath());
		} catch (Exception e) {
			// Backup failed - abort operation
			log.error(String.format("Can't backup config file '%s' - save aborted (ignoring)", originalFile), e);
			return;
		}
		// Save configuration
		try {
			xmlPart.save();
			EventLog.log("evt.configSaved", ConfigManager.class, xmlPart.getFileName());
		} catch (Exception e) {
			log.error("Failed to save configuration (ignoring)", e);
		}
		
	}

	public String getXmlConfigFilename() {
		return xmlConfigFilename;
	}
	
	private PropertiesConfiguration getHtmlPropertiesForVersion(TravianVersion version) {
		PropertiesConfiguration props = htmlPatterns.get(version);
		if (props == null) {
			String message = "Internal error: html patterns not found for version " + version;
			log.error(message);
			throw new FatalException(message);
		}
		return props;
	}
	
	public String getHtmlPattern(String key, TravianVersion version) {
		// First look in requested version
		PropertiesConfiguration props = getHtmlPropertiesForVersion(version);
		String result = props.getString(key, null);
		if (result!=null && result.trim().length()==0) {
			result = null; // Empty properties are the same as missing ones
		}
		// Then look in previous versions if any
		while ((result == null) && version.getPrevious()!=null) {
			version = version.getPrevious();
			props = getHtmlPropertiesForVersion(version);
			result = props.getString(key, null);
			if (result!=null && result.trim().length()==0) {
				result = null; // Empty properties are the same as missing ones
			}
		}
		if (result == null) {
			String message = String.format("Internal error: html pattern key \"%s\" not found", key);
			log.error(message);
			throw new FatalException(message);
		}
		return result;
	}
	
	public Set<String> allUids = new HashSet<String>();
	public Set<String> allEnabledUids = new HashSet<String>();
}

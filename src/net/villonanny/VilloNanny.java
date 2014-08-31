package net.villonanny;

import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.villonanny.entity.Server;

import org.apache.commons.configuration.AbstractFileConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.apache.log4j.Logger;
/**
 * Starts the Nanny, loads the configuration, starts each server, starts the console, 
 */
 
public class VilloNanny {
	private static final Logger log = Logger.getLogger(VilloNanny.class);
	public static SimpleDateFormat formatter = new SimpleDateFormat();
	private Map<String, Server> allServers; // ServerID -> Server instance
	private Date startTime = null;
	private static VilloNanny singleton;
	private ConfigManager configManager;
	private Console console;
	private boolean configurationJustReloaded = false; // True when the configuration has been reloaded and the VilloNanny hasn't been updated yet

	private VilloNanny(String[] args) throws DuplicateUidException {
		String configFilename = ConfigManager.DEFAULT_CONFIGFILE;
		configManager = new ConfigManager(configFilename);
		configManager.loadConfiguration();
		boolean modified = configManager.ensureUniqueIds();
		if (modified) {
			// uid's have been added: save configuration with backup
			configManager.saveXmlConfiguration();
		}
		formatter = new SimpleDateFormat(configManager.getString("/dateFormat", "EEE dd MMMM yyyy HH:mm:ss Z"));
		String waitUntil = Util.startTimeString(args);
		if (waitUntil!=null) {
			try {
				startTime = formatter.parse(waitUntil);
				// EventLog.log("Waiting until " + startTime);
			} catch (ParseException e) {
				String message = "Invalid date: " + waitUntil + "; format should be like \"" + formatter.format(new Date()) + "\"";
				EventLog.log(message);
				log.error(message, e);
			}
		}
		console = new Console(configManager);
	}
	
	public static void main(String[] args) {
		String s = VilloNanny.class.getSimpleName() + " " + Version.VERSION + " starting...";
		EventLog.log(s);
		EventLog.log("");
		EventLog.log("evt.start01", VilloNanny.class);
		EventLog.log("evt.start02", VilloNanny.class);
		EventLog.log("evt.start03", VilloNanny.class);
		EventLog.log("evt.start04", VilloNanny.class);
		EventLog.log("");
		Util.setUtf8(args);
		log.debug(String.format("Current os.name = %s, os.arch = %s, os.version = %s", 
				System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version")));
		log.debug(String.format("Java version %s from %s", System.getProperty("java.version"), System.getProperty("java.vendor")));
		log.debug("Current locale = " + Locale.getDefault());
		log.debug("Current character encoding = " + new OutputStreamWriter(System.out).getEncoding());
		//
		if ("true".equalsIgnoreCase(System.getProperty("QUICK"))) {
			log.warn("!!! Forcing quick pause !!!");
		}
		// Check if we need to create an initial configuration, otherwise load it and run
		String configFilename = ConfigManager.DEFAULT_CONFIGFILE;
		ConfigManager configManager = new ConfigManager(configFilename);
		if (!configManager.isConfigurationThere()) {
			// The configuration file is not there, so call the autoconfigurator
			EventLog.log("evt.newConfigurationStart", VilloNanny.class);
			boolean success = new AutoConfigurator(configManager).configure();
			if (success) {
				EventLog.log("evt.newConfigurationEnd01", VilloNanny.class);
				EventLog.log("evt.newConfigurationEnd01b", VilloNanny.class, configManager.getString("/server/@version"));
				EventLog.log("evt.newConfigurationEnd02", VilloNanny.class, ConfigManager.CONFIGDIR);
			}
		} else {
			try {
				singleton = new VilloNanny(args);
				singleton.execute();
			} catch (DuplicateUidException e) {
				// Don't log, just exit
			}
		}
		EventLog.log("evt.exit", VilloNanny.class);
	}
	
	public static VilloNanny getInstance() {
		return singleton;
	}
	
	public Map<String, Server> getAllServers() {
		return allServers;
	}
	
	private void execute() {
		int totServers = 0;
		try {
			// First we create all servers and login, so that errors and user prompts won't occur at the delayed start
			// Create all servers
			allServers = createServerList();
			if (allServers.size()==0) {
				EventLog.log("No servers defined. Exiting...");
				return;
			}
			// Login to all servers, just to check credentials and prompt for missing passwords
			for (Server server : allServers.values()) {
				try {
					if (server.isEnabled()) {
						server.login();
						totServers++;
					}
				} catch (ConversationException e) {
					EventLog.log("Can't login, disabling server \"" + server.getServerDesc() + "\"");
					log.error("", e);
					server.setEnabledAndStartStop(false);
				}
			}
			if (totServers==0) {
				EventLog.log("msg.noServers", this.getClass());
				return;
			}
			//
			// Delayed start
			if (startTime!=null) {
				Util.sleep(startTime.getTime() - System.currentTimeMillis());
			}
			//
			// Start Console
			console.start();
			// Start all servers
			for (Server server : allServers.values()) {
				server.begin();
			}
			configManager.addListener(getConfigurationListener());
			// 
			while (!console.isQuitting()) {
				try {
					Thread.sleep(4000);
				} catch (InterruptedException e) {
					// Ignored
				}
				if (isConfigurationJustReloaded()) {
					try {
						// Update server list
						Map<String, Server> addedServers = updateServerList(allServers);
						// Start all new servers
						for (Server server : addedServers.values()) {
							server.begin();
						}
					} catch (Exception e2) {
						log.error("Failed to update configuration", e2);
					}
					setConfigurationJustReloaded(false);
				}
			}
			try {
				console.join(); // This thread will wait here until the console is terminated
			} catch (InterruptedException e) {
				log.debug("Interrupted");
			}
			// Wait for all servers to terminate
			for (Server server : allServers.values()) {
				try {
					server.join();
				} catch (InterruptedException e) {
					log.debug("Interrupted");
				}
			}
		} catch (Exception e) {
			Util.log("Program error", e);
			EventLog.log(e.getMessage());
			EventLog.log("Aborting...");
		}
//		EventLog.log("...done.");
	}
	
	private ConfigurationListener getConfigurationListener() {
		return new ConfigurationListener() {
			Thread nannyThread = Thread.currentThread();
			public void configurationChanged(ConfigurationEvent event) {
				if (event.getType() == AbstractFileConfiguration.EVENT_RELOAD && !event.isBeforeUpdate()) {
					setConfigurationJustReloaded(true);
					nannyThread.interrupt(); // Awake from sleep
				}
			}
		};
	}

	/**
	 * Initial creation of the server list
	 * @return
	 */
	private Map<String, Server> createServerList() {
		return updateServerList(new HashMap<String, Server>());
	}
	
	/**
	 * If servers are added or deleted, modify the server list
	 * @param theServers the previous servers
	 * @return the map of added servers than need to be started
	 */
	private Map<String, Server> updateServerList(Map<String, Server> theServers) {
		log.debug("Updating server list");
		List<Server> deletableServers = new ArrayList<Server>(theServers.values());
		Map<String, Server> addedServers = new HashMap<String, Server>();
		
		try {
			List<SubnodeConfiguration> serverConfigs = configManager.configurationsAt("/server");
			for (SubnodeConfiguration serverConfig : serverConfigs) {
				// Configuration is cloned so that it is not wiped on reload
				SubnodeConfiguration serverConfigurationCopy = (SubnodeConfiguration) serverConfig.clone();
				String serverId = Server.idFromConfig(serverConfig);
				Server existingServer = theServers.get(serverId);
				if (existingServer==null) {
					// New server added
					Server newServer = new Server(serverConfigurationCopy, configManager, console);
					addedServers.put(serverId, newServer);
					theServers.put(serverId, newServer);
				} else {
					// Existing server
					if (addedServers.values().contains(existingServer)) {
						// Server just added
						EventLog.log("evt.duplicateServer", this.getClass(), serverId);
						continue;
					}
					deletableServers.remove(existingServer); // Remove this server from the deletable servers
					// When the configuration is reloaded, the SubnodeConfiguration that was previously contained in the Server
					// is not automatically updated: 
					// http://commons.apache.org/configuration/apidocs/org/apache/commons/configuration/SubnodeConfiguration.html
					// Server configuration is updated only when the server chooses to do so (not while in the middle of a strategy)
					// existingServer.updateConfig(serverConfig);
					existingServer.setNextConfiguration(serverConfigurationCopy);
					// Check if enabled/disabled has changed on an existing server
					boolean enabledInConfig = serverConfig.getBoolean("/@enabled", true);
					existingServer.setEnabledAndStartStop(enabledInConfig);
				}
			}
			// Now removedServers contains all servers that are not in the configuration anymore
			// and addedServers contains all new servers
			// Stop and delete all removed servers
			for (Server removedServer : deletableServers) {
				removedServer.terminate();
				theServers.remove(removedServer.getServerId());
			}
		} catch (InvalidConfigurationException e) {
			log.error("Configuration error", e);
			EventLog.log("msg.reloadAborted", VilloNanny.class);
		}
		return addedServers;
	}

	private synchronized boolean isConfigurationJustReloaded() {
		return configurationJustReloaded;
	}

	private synchronized void setConfigurationJustReloaded(boolean configurationJustReloaded) {
		this.configurationJustReloaded = configurationJustReloaded;
	}



}

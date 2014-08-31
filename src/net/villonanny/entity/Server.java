package net.villonanny.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.villonanny.ConfigManager;
import net.villonanny.Console;
import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.ReportMessageReader;
import net.villonanny.StrategyStatus;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.TranslationException;
import net.villonanny.Util;
import net.villonanny.strategy.ServerStrategy;
import net.villonanny.strategy.Strategy;
import net.villonanny.type.TribeType;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
import org.slf4j.helpers.MessageFormatter;

public class Server extends Thread {
	private static final Logger log = Logger.getLogger(Server.class);
	private Util util;
	private SubnodeConfiguration config;
	private SubnodeConfiguration nextConfig = null; // The configuration that has been loaded and needs to be applied next
	private Map<String, Village> villages;
	private Map<String, Strategy> serverStrategies;
	private List<Strategy> serverStrategyList; // Ordered list
	private String loginUrl; // The login URL uniquely identifies a server
	private String user;
	private boolean enabled=false;
	private boolean startedAndRunning=false;
	// private TribeType tribeType = null;
	private	ReportMessageReader reportReader = new ReportMessageReader();
	private String reportMode = "false";			// control if reading reports
	private	ReportMessageReader messageReader = new ReportMessageReader();		// separate instance from reports
	private String messageMode = "false";			// control if reading messages
	private	PropertiesConfiguration valleyConfig = null;	// config file to store valley info
	private boolean suspended=false;
	private final static int	SUSPENDCHECK = 5;	// no of minutes to check if suspended
	private	TimeWhenRunnable sharpTimeWhenRunnable = null;		// earliest sharp strategy - global as check in village and strategy

	
	// Status object shared by all villages
	private final StrategyStatus strategyStatus = new StrategyStatus();

	public Server(SubnodeConfiguration serverConfig, ConfigManager configManager, Console console) throws InvalidConfigurationException {
		// Put here only values that don't need config refresh
		this.enabled = serverConfig.getBoolean("/@enabled", true);
		this.loginUrl = serverConfig.getString("/loginUrl");
		this.user = serverConfig.getString("/user");
		this.villages = new HashMap<String, Village>();
		this.util = new Util(serverConfig, configManager, console);
		this.serverStrategies = new HashMap<String, Strategy>();
		this.serverStrategyList = new ArrayList<Strategy>();
		// All other attributes must be fetched in updateConfig
		updateConfig(serverConfig);
	}
	
	private void setTribeType(SubnodeConfiguration serverConfig) {
		String tribeString = serverConfig.getString("@tribe", null);
		if (tribeString==null) {
			String desc = serverConfig.getString("@desc", "");
			String msg = Util.getLocalMessage("msg.noTribe", this.getClass());
			String message = MessageFormatter.format(msg, desc);
			throw new FatalException(message);
		}
//		String tribeKey = util.getTranslator().getKeyword(tribeString);
		try {
			this.util.setTribeType(TribeType.fromLanguageValue(tribeString, util.getTranslator()));
		} catch (InvalidConfigurationException e) {
			throw new FatalException(e);
		}
	}
	
	public Collection<Village> getVillages() {
		return villages.values();
	}
	
	public String getServerDesc() {
		// When "desc" is not set, use the server url instead
		return config.getString("/@desc", getServerId());
	}
	
	public void run() {
		EventLog.log("Starting nanny for \"" + getServerDesc() + "\"");
		setStartedAndRunning(true);
		NDC.push(getServerDesc());
		try {
			// Nanny villages
			while (isKeepRunning()) {
				util.getConsole().checkPause();
				TimeWhenRunnable firstTimeWhenRunnable = null;
				sharpTimeWhenRunnable = null;
				if ((messageMode != null) && !messageMode.equals("false")) {
					// check messages before for commands including resume
					int msgRead = messageReader.readMessages(util, loginUrl, messageMode);
					log.info("Messages Done next "+msgRead);
				}
				// check if still enabled
				if (!suspended) {
					// Loop all enabled villages
					EventLog.log("Checking Villages");							
					firstTimeWhenRunnable = processVillages();
					log.info("Villages done");
					// gac - move servers below villages
					// Loop all enabled server strategies							
					TimeWhenRunnable serverStrategiesTimeWhenRunnable = processServerStrategies();
					if (serverStrategiesTimeWhenRunnable.before(firstTimeWhenRunnable)) {
						log.debug("ServerStrategy Earlier than villages");
						firstTimeWhenRunnable = serverStrategiesTimeWhenRunnable;
					}
					// gac add report processing - exists as server strategy to run periodically
					// but this will run at the end of every active period
					// count if outstanding after this read
					int moreRead = 0;
					if ((reportMode != null) && !reportMode.equals("false")) {
						// set the reporting mode and check reports
						// ReportMessageReader.getInstance().setReportsMode(reportMode);
						// EventLog.log(loginUrl+" about to read reportMode="+reportMode);
						moreRead = reportReader.readReports(util, loginUrl, reportMode);
						log.info("Server Reports done");
					}
					// read messages again
					if ((messageMode != null) && !messageMode.equals("false")) {
						// check messages
						int msgRead = messageReader.readMessages(util, loginUrl, messageMode);
						log.info("Messages at end done next "+msgRead);
						// read more if consuming all message not just scanning titles for commands
						// if (!messageMode.equalsIgnoreCase("titles")) {
						if (!messageMode.equalsIgnoreCase(messageReader.getScanMode())) {
							moreRead += msgRead;
						}
					}
					if ( moreRead != 0 ) {
						// still more to read
						firstTimeWhenRunnable = TimeWhenRunnable.NOW; // Re-run immediately
						log.debug("Villages will be run again because more reports to read ("+moreRead+")");
					} else {							
						// no new reports left
					}
					if (isConfigurationChanged()) {
						// Update configuration
						updateConfig(getNextConfig());
						EventLog.log("evt.configReloadDone", this.getClass());
						this.nextConfig = null; // No need to synchronize here
						firstTimeWhenRunnable = TimeWhenRunnable.NOW; // Re-run immediately
						log.debug("Villages will be run again because of configuration update");
					}
				} else {
					// suspended
					log.debug("All Strategies Suspended");
					firstTimeWhenRunnable = new TimeWhenRunnable(System.currentTimeMillis() + SUSPENDCHECK*Util.MILLI_MINUTE); // try again later
				}
				boolean sharp = false;
				long milliPause = 0;
				long milliSharp = -1L;
				if (firstTimeWhenRunnable != null) {
					log.debug("Earliest Strategy @ "+firstTimeWhenRunnable);
					sharp = firstTimeWhenRunnable.isSharp();
					milliPause = firstTimeWhenRunnable.getTime() - System.currentTimeMillis();
					if (milliPause<0) {
						milliPause=0;
					}
					int pauseLimit = config.getInt("/@pauseLimit", 86400) * 1000; //  Max 24h
					if (milliPause > pauseLimit) {
						log.info("Limiting pause from " + milliPause + " to " + pauseLimit);
						milliPause = pauseLimit;
					}
					if (sharpTimeWhenRunnable != null){
						log.debug("Earliest sharp Strategy @ "+sharpTimeWhenRunnable);
						milliSharp = sharpTimeWhenRunnable.getTime() - System.currentTimeMillis();
						if (milliSharp<0) {
							milliSharp=0;
						}
					}
					log.debug("Next available Action in " + Util.milliToTimeString(milliPause)+ 
													(sharp ? " sharp: " + Util.milliToTimeString(milliSharp) : ""));
				}
				
				if (isKeepRunning()) {
					util.userPause(milliPause, sharp, milliSharp);
					util.shortestPause(sharp); // Just to be safe
				}
			}
		} catch (Exception e) {
			EventLog.log(e.getMessage());
			log.error("", e);
			setEnabledAndStartStop(false);
		} finally {
			NDC.remove(); // not pop()
		}
	}

	private TimeWhenRunnable processServerStrategies() {
		TimeWhenRunnable firstTimeWhenRunnable = TimeWhenRunnable.NEVER;
		// Run through each strategy
		for (Strategy strategy : this.serverStrategyList) {
			if (strategy.isDeleted() || util.getConsole().isQuitting()) {
				continue;
			}
			NDC.push("(" + strategy.getDesc() + ")");
			try {
				util.getConsole().checkFlags();
				TimeWhenRunnable timeWhenRunnable = strategy.getTimeWhenRunnable();
				if (timeWhenRunnable == null || timeWhenRunnable.isOpportunist() || timeWhenRunnable.before(new Date())) {
					EventLog.log("Executing strategy \"" + strategy.getDesc() + "\"");
					TimeWhenRunnable nextTime = strategy.execute();
					if (nextTime == null) { // Just to be safe
						EventLog.log("Timewhenrunnable = null returned");
						log.warn("(Internal Error) Shouldn't return null");
						nextTime = new TimeWhenRunnable(System.currentTimeMillis());
					}
					log.debug(String.format("Server Strategy %s will be run after %s ", strategy.getDesc(), nextTime.getTimeWhenRunnable()));
					strategy.setTimeWhenRunnable(nextTime);
					if (firstTimeWhenRunnable.after(nextTime)) {
						firstTimeWhenRunnable = nextTime;
						log.trace("Earliest Server Strategy is "+strategy.getDesc()+" "+firstTimeWhenRunnable);
					}
					// check if this one is sharp - if so store earliest sharp one
					/*
					if (timeWhenRunnable!=null && timeWhenRunnable.isSharp()) {
						if (sharpTimeWhenRunnable == null || sharpTimeWhenRunnable.after(timeWhenRunnable)) {
						// log.trace("Earliest Village "+village.getDesc()+" "+newTimeWhenRunnable);
						sharpTimeWhenRunnable = timeWhenRunnable;
						log.debug("Earliest sharp Strategy is "+strategy.getDesc()+" "+timeWhenRunnable);
						}
					}
					*/
					if (nextTime.isSharp()) {
						if (sharpTimeWhenRunnable == null || sharpTimeWhenRunnable.after(nextTime)) {
							// log.trace("Earliest Village "+village.getDesc()+" "+newTimeWhenRunnable);
							sharpTimeWhenRunnable = nextTime;
							log.trace("Earliest sharp Strategy is "+strategy.getDesc()+" "+sharpTimeWhenRunnable);
						}
					}					
				}
			} catch (ConversationException e) {
				String s = "Error while executing strategy \"" + strategy.getDesc() + "\" (skipping)";
				Util.log(s, e);
				EventLog.log(s);
				util.shortestPause(false); // Just to be safe
				// Just keep going to the next strategy
			} catch (TranslationException e) {
				EventLog.log(e.getMessage());
				log.error("Translation error", e);
				// Keep going
			} catch (Exception e) {
				// Any other exception skips the strategy but keeps the server going
				// so that bugs in one strategy don't prevent other strategies from executing
				String message = EventLog.log("msg.strategyException", this.getClass(), strategy.toString());
				log.error(message, e);
				util.shortestPause(false); // Just to be safe
				firstTimeWhenRunnable = TimeWhenRunnable.NOW; // Retry after userPause
			} finally {
				NDC.pop();
			}	
		}
		return firstTimeWhenRunnable;
	}

	private TimeWhenRunnable processVillages() throws InvalidConfigurationException {
		TimeWhenRunnable firstTimeWhenRunnable = null;
		try {
			List<SubnodeConfiguration> villageConfigs = config.configurationsAt("/village[@enabled='true']");
			for (SubnodeConfiguration villageConfig : villageConfigs) {
				// If configuration changed, abort current loop, update config and restart
				if (isConfigurationChanged()) {
					log.debug("Exiting village loop for configuration update");
					break;
				}
				String id = Village.getIdFromConfig(villageConfig);
				Village village = villages.get(id);
				if (village == null) {
					log.warn("Configuration file not aligned to village map; ignoring village " + villageConfig.getString("/@desc", "") + " " + villageConfig.getString("/url", ""));
					continue;
				}
				try {
					// getTimeWhenRunnable may update to check for triggering resources so can change page
					TimeWhenRunnable currentTimeWhenRunnable = village.getTimeWhenRunnable();
					// log.debug("currentTimeWhenRunnnable:"+currentTimeWhenRunnable);
					if (currentTimeWhenRunnable.before(new Date())) {
						EventLog.log("Processing village \"" + village.getDesc() +"\"");
						village.execute(); // Execute strategies
						currentTimeWhenRunnable = village.getTimeWhenRunnable();
						// log.debug("currentTimeWhenRunnnable after execute:"+currentTimeWhenRunnable);
					} else {
						EventLog.log("Village \"" + village.getDesc() +"\" sleeping until " + currentTimeWhenRunnable);
					}
					// use current here and only reread after execute, then use new below
					if ((firstTimeWhenRunnable == null) || firstTimeWhenRunnable.after(currentTimeWhenRunnable)) {
						log.trace("Earlier Village "+village.getDesc()+" "+currentTimeWhenRunnable);
						firstTimeWhenRunnable = currentTimeWhenRunnable;
					}
					// check for earliest sharp strategy in village, return NEVER if none- if is store earliest sharp one
					TimeWhenRunnable newTimeWhenRunnable = village.getTimeWhenSharp();
					if (newTimeWhenRunnable.isSharp()) {
						if (sharpTimeWhenRunnable == null || sharpTimeWhenRunnable.after(newTimeWhenRunnable)) {
							// log.trace("Earlier Village "+village.getDesc()+" "+newTimeWhenRunnable);
							sharpTimeWhenRunnable = newTimeWhenRunnable;
							log.trace("Earlier sharp Village is "+village.getDesc()+" "+sharpTimeWhenRunnable);
						}
					}
				} catch (SkipVillageRequested e) {
					log.debug("Village skipped");
					continue;
				} catch (SkipRequested e) {
					// Just keep going
					log.debug("Action skipped");
				} catch (ServerFatalException e) {
					throw e; // Will catch below
				} catch (Exception e) {
					String s = "Village \"" + village.getDesc() + "\" error (retrying later): ";
					log.error(s, e);
					EventLog.log(s + e.getMessage());
					util.shortestPause(false); // Just to be safe
					firstTimeWhenRunnable = null; // Retry after userPause
				}
			}
		} catch (ConcurrentModificationException e) {
			// This shouldn't happen anymore
			// Just ignore
			log.debug("Village list was modified while server running (ConcurrentModificationException): skipping and repeating");
		}
		// log.debug("returning firstTimeWhenRunnable:"+firstTimeWhenRunnable);
		return firstTimeWhenRunnable;
	}

	public void login() throws ConversationException {
		util.login(false);
	}

	public synchronized boolean isSuspended() {
		// Must be synchronized because the attribute is accessed from different threads
		return suspended;
	}
	public synchronized boolean suspend(boolean state) {
		// Must be synchronized because the attribute is accessed from different threads
		log.debug(loginUrl+"suspend("+state+") was "+suspended);
		suspended = state;
		return suspended;
	}

	public synchronized boolean isEnabled() {
		// Must be synchronized because the attribute is accessed from different threads
		return enabled;
	}

	public synchronized void setEnabledAndStartStop(boolean enabled) {
		// Must be synchronized because the attribute is accessed from different threads
		boolean previousState = this.enabled;
		this.enabled = enabled;
		if (enabled==false && previousState==true && startedAndRunning==true) {
			terminate();
		} else if (enabled==true && previousState==false) {
			begin();
		}
	}
	
	public synchronized void begin() {
		// Must be synchronized because the attribute is accessed from different threads
		if (enabled) {
			EventLog.log("evt.serverEnabled", this.getClass(), this.getServerDesc());
			start();
			util.getConsole().addServerThread(this);
		}
	}
	
	public synchronized void terminate() {
		// Must be synchronized because the attribute is accessed from different threads
		EventLog.log("evt.serverStopped", this.getClass(), this.getServerDesc());
		startedAndRunning = false;
		this.interrupt(); // interrupt the server thread
		util.getConsole().removeServerThread(this);
	}

	public static String idFromConfig(SubnodeConfiguration serverConfig) {
		String loginUrl = serverConfig.getString("/loginUrl", null);
		String user = serverConfig.getString("/user", null);
		return user + "@" + loginUrl;
	}
	
	public String getServerId() {
		return user + "@" + loginUrl;
	}
	public PropertiesConfiguration getValleyConfig() {
		// log.debug("valleyConfig="+valleyConfig.getFileName());
		return valleyConfig;
	}
	public void setValleyConfig(PropertiesConfiguration value) {
		valleyConfig = value;
		log.debug("valleyConfig="+valleyConfig.getFileName());
	}
	public String getLoginUrl() {
		return loginUrl;
	}
	
	public String getUserName() {
		return user ;		
	}

	private void updateConfig(SubnodeConfiguration newServerConfig) throws InvalidConfigurationException {
		log.debug("Updating server config for " + getServerId());
		this.config = newServerConfig;
		this.util.setServerConfig(newServerConfig);
		this.util.setServer(this);
		this.util.setServerId(user, getServerId());			// set server id for use in report processing
		// check if reading reports from overall server
		reportMode = newServerConfig.getString("/reports", null);					// simple format to just read
		reportMode = newServerConfig.getString("/reports/@enabled", reportMode);	// complex config with processing
		if (reportMode != null) {
			SubnodeConfiguration reportConfig = newServerConfig.configurationAt("/reports");
			reportMode = reportConfig.getString("/output/@format", reportMode);	// complex config with processing
			EventLog.log(loginUrl+" reportMode="+reportMode);
			reportReader.setReportsMode(reportMode, reportConfig);			
		}
		messageMode = newServerConfig.getString("/messages", null);
		messageMode = newServerConfig.getString("/messages/@enabled", messageMode);
		// note once enabled have to turn off by setting false or will not call to disable
		if (messageMode != null) {
			SubnodeConfiguration messageConfig = newServerConfig.configurationAt("/messages");
			String messageText = messageConfig.getString("/@commands", null);
			EventLog.log(loginUrl+" messageMode="+messageMode+" Command Text ("+messageText+")");
			// use same as mode or a separate param?
			messageReader.setMessagesMode(messageMode, messageConfig);			
		}
		setTribeType(newServerConfig);
		//
		// Update villages
		// When a village is disabled or removed from the configuration, it is deleted from this.villages
		List<Village> deletableVillages = new ArrayList<Village>(villages.values());
		// Loop all enabled villages
		List<SubnodeConfiguration> villageConfigs = newServerConfig.configurationsAt("/village[@enabled='true']");
		for (SubnodeConfiguration villageConfig : villageConfigs) {
			String id = Village.getIdFromConfig(villageConfig); // Either uid or url
			Village village = this.villages.get(id);
			if (village == null) {
				// New village
				village = new Village(util, villageConfig, strategyStatus);
				this.villages.put(id, village);
			} else {
				// Village already exists
				village.updateConfig(villageConfig, util);
				deletableVillages.remove(village); // This village is still enabled			
			}
		}
		// Removing deleted or disabled villages
		for (Village village : deletableVillages) {
			this.villages.remove(village.getId());
			village.terminate();
		}
		//
		// Update server strategies
		serverStrategyList = updateStrategies("/serverStrategy[@enabled='true']", newServerConfig, serverStrategies, null);
	}

	/**
	 * Set the new configuration after a reload.
	 * @param serverConfigurationCopy
	 */
	public synchronized void setNextConfiguration(SubnodeConfiguration serverConfigurationCopy) {
		// Must be synchronized because the attribute is accessed from different threads
		NDC.push(getServerDesc());
		try {
			EventLog.log("evt.configReloadScheduled", this.getClass());
			nextConfig = serverConfigurationCopy;
		} finally {
			NDC.pop();
		}
	}

	public synchronized boolean isKeepRunning() {
		// Must be synchronized because the attribute is accessed from different threads
		return startedAndRunning;
	}
	
	private boolean isConfigurationChanged() {
		return getNextConfig() != null;
	}

	public synchronized SubnodeConfiguration getNextConfig() {
		// Must be synchronized because the attribute is accessed from different threads
		return nextConfig;
	}
	
	private synchronized void setStartedAndRunning(boolean value) {
		startedAndRunning = value;
	}
	
	public List<Strategy> updateStrategies(String tagPath, SubnodeConfiguration config, Map<String, Strategy> strategies, Village village) {
		List<Strategy> deletableStrategies = new ArrayList<Strategy>(strategies.values());
		List<Strategy> addedStrategies = new ArrayList<Strategy>();
		List<Strategy> strategyList = new ArrayList<Strategy>();
		List<SubnodeConfiguration> strategyConfigs = config.configurationsAt(tagPath);
		for (SubnodeConfiguration strategyConfig : strategyConfigs) {
			// Console.getInstance().checkFlags(); // This is wrong
			// Create a candidate strategy
			Strategy candidate = createStrategy(strategyConfig);
			// Check if it is already listed
			String idFromConfig = Strategy.getIdFromConfig(strategyConfig);
			Strategy oldStrategy = strategies.get(idFromConfig);
			if (oldStrategy != null) {
				if (addedStrategies.contains(candidate)) {
					// Strategy just added
					EventLog.log("evt.duplicateStrategy", this.getClass(), candidate.getId());
					continue;
				}
				// Reconfigure existing
				oldStrategy.updateConfig(strategyConfig, util);
				deletableStrategies.remove(oldStrategy);
				strategyList.add(oldStrategy);
			} else {
				// Add new
				addedStrategies.add(candidate);
				candidate.init(strategyConfig, util, village);
				strategies.put(candidate.getId(), candidate);
				strategyList.add(candidate);
			}
		}
		// Delete disabled or removed strategies
		for (Strategy strategy : deletableStrategies) {
			strategy.setDeleted(true);
			strategies.remove(strategy.getId());
			EventLog.log("evt.strategyRemoved", this.getClass(), strategy.getId());
		}
		return strategyList;
	}
	
	private static Strategy createStrategy(SubnodeConfiguration strategyConfig) {
		Strategy result;
		String desc = "?";
		try {
			String className = strategyConfig.getString("/@class");
			desc = strategyConfig.getString("/@desc", className);
			String fullClassName = Strategy.class.getPackage().getName() + "." + className;
			result = (Strategy) Class.forName(fullClassName).newInstance();
		} catch (Exception e) {
			throw new FatalException("Invalid strategy \"" + desc + "\"", e);
		}
		return result;
	}

	public Village getVillage(String uid) {
		return villages.get(uid);
	}
}

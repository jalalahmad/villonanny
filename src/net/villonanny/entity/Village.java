package net.villonanny.entity;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.StrategyStatus;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.TranslationException;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.Util.Pair.IntPair;
import net.villonanny.strategy.Strategy;
import net.villonanny.type.BuildingType;
import net.villonanny.type.BuildingTypeToBuildingMap;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.ResourceTypeToFieldMap;
import net.villonanny.type.TribeType;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class Village {
	private static final Logger log = Logger.getLogger(Village.class);
/*	private static final String P_WHOLETAG = "<[^>]*>"; // Match a whole html tag, from < to >
	private static final String P_CLOSETAG = "[^>]*>"; // Match up to the closing element of a tag, i.e. to >
	private static final String P_NONDIGITS = "\\D*";
	private static final String P_NOQUOTES = "[^\"]*?";
	private static final String P_ANYWHITESPACE = "\\s*";*/
	private String id; // Village id in the configuration file (uid)
	private SubnodeConfiguration config;
	private String villageUrlString;		// from config file
	private String villageMainUrlString;	// Overview page - constructed from config url
	private String villageSecondUrlString;	// Centre page - from travian
	private String villageName; 			// From travian
	private String villageUrlId = null; 	// constructed from config url - get from travian?
	// the following should all be common
	// private String mapUrlString = null;
	// private String statsUrlString = null;	
	// private String reportsUrlString = null;	
	// private String messagesUrlString = null;	
	private Util util;		// 1 instance of util per server
	private ResourceTypeMap availableResources = new ResourceTypeMap();
	private ResourceTypeMap maxResources = new ResourceTypeMap();
	private ResourceTypeMap production = new ResourceTypeMap();
	// private int[] resourcesAtLastRun = new int[availableResources.length];
	private Map<String, Field> fields=null; // travianSlotId -> Field
	private Map<String, Building> buildings;
	private BuildingTypeToBuildingMap buildingMap;
//	private Map<String, EmptySite> emptySites;
	private Translator translator;
	private Map<String, Strategy> strategies;
	private List<Strategy> strategyList; // Ordered list
	private boolean hasRallyPoint = true;
	private static final String RALLYPOINT_ID = "39";
//	private String RALLYPOINT_URL_SUFFIX = "build.php?id=" + RALLYPOINT_ID;
	private static final String WALL_ID = "40";
//	private String WALL_URL_SUFFIX = "build.php?id=" + WALL_ID;
	private Point position;
	
	public StrategyStatus strategyDone; // = new StrategyStatus();
	public boolean  forceOnReload;
	// basic setup for the queue system
	private  List<Date> mFieldQueue = new ArrayList<Date>();
	private  List<Date> mBuildingQueue = new ArrayList<Date>();
	private int mMaxJobs = 1;
	private int mFieldQueueLength = 1;
	private int mBuildingQueueLength = 1;
	private	List<Integer> troopMoveNumber = new ArrayList<Integer>();
	private	List<String> troopMoveType = new ArrayList<String>();
	private	List<Integer> troopMoveNextSecs = new ArrayList<Integer>();
	private List<Date> troopMoveNextDate = new ArrayList<Date>();
	// private Date nextAttackDate = TimeWhenRunnable.NEVER;
	private Date nextIncomingAttackTime = null;
	private Date nextIncomingReinfTime = null;
	private Date nextOutgoingAttackTime = null;
	private Date nextOutgoingReinfTime = null;
	/**
	 * Load village main page and fetch resources and next urls
	 * @param tribeType 
	 * 
	 * @param config
	 * @param client
	 * @param serverName
	 * @param villageId
	 * @param navigator
	 * @param translator
	 * @throws InvalidConfigurationException 
	 * @throws ConversationException
	 */
	public Village(Util util, SubnodeConfiguration villageConfig, StrategyStatus strategyStatus) throws InvalidConfigurationException {
		this.villageUrlString = villageConfig.getString("/url");
		this.villageMainUrlString = Util.getFullUrl(villageUrlString, "dorf1.php");
		this.villageSecondUrlString = Util.getFullUrl(villageUrlString, "dorf2.php");		// set for now should update later from page
		// check for village id
		Pattern p = Pattern.compile(Util.P_FLAGS+"newdid=(\\d+)");
		Matcher m = p.matcher(villageUrlString);
		if (m.find()) {
			this.villageUrlId = m.group(1);
			log.trace("Configured with newdid="+villageUrlId);
		}
		this.id = getIdFromConfig(villageConfig);
		this.translator = util.getTranslator();
		// Create strategies
		this.strategies = new HashMap<String, Strategy>();
		this.strategyList = new ArrayList<Strategy>();
		this.strategyDone = strategyStatus;
		updateConfig(villageConfig, util);
	}

	public String gotoConfigUrl(boolean sharp) throws ConversationException {
		return util.httpGetPage(villageUrlString, sharp);
	}

	public String gotoConfigUrl() throws ConversationException {
		return gotoConfigUrl(false);
	}
	
	public String gotoMainPage(boolean sharp) throws ConversationException {
		// return util.httpGetPage(villageMainUrlString, sharp);
		return getCorrectVillagePage(villageMainUrlString, sharp);
	}

	public String gotoMainPage() throws ConversationException {
		return gotoMainPage(false);
	}

	public String gotoSecondPage(boolean sharp) throws ConversationException {
		// return util.httpGetPage(villageSecondUrlString, sharp);
		return getCorrectVillagePage(villageSecondUrlString, sharp);
	}

	public String gotoSecondPage() throws ConversationException {
		return gotoSecondPage(false);
	}
	
	public String getCorrectVillagePage(String urlString, boolean sharp)  throws ConversationException {
	    // check if position set
		if (position == null) {
			// always use config url if not, should be safe as SecondPage should not be set without having set position
			log.trace("first access - use Config Url:"+villageUrlString);
			return util.httpGetPage(villageUrlString, sharp);
		} else {
			// get requested page
			String page = util.httpGetPage(urlString, sharp);
			// check on correct village
			Pattern p = util.getPattern("village.activeVillage");
			Matcher m = p.matcher(page);
			if (m.find()) {
				String v = m.group(1);
				int x = Integer.parseInt(m.group(2));
				int y = Integer.parseInt(m.group(3));
				log.trace("Player has more than one village.  This is ("+x+"|"+y+") "+v);
				// check correct
				if ((x != position.x) || (y != position.y)) {
					String newUrl = urlString+"?newdid="+villageUrlId;
					log.debug("Switching Village from ("+x+"|"+y+") to ("+position.x+"|"+position.y+") ["+newUrl+"]");
					page = util.httpGetPage(newUrl, sharp);
					// check again!
					p = util.getPattern("village.activeVillage");
					m = p.matcher(page);
					if (m.find()) {
						v = m.group(1);
						x = Integer.parseInt(m.group(2));
						y = Integer.parseInt(m.group(3));
						// check correct
						if ((x != position.x) || (y != position.y)) {
							log.error("Failed to Switch from ("+x+"|"+y+") to ("+position.x+"|"+position.y+")");
							// make fatal?
						} else {
							log.trace("Switched ok to ("+x+"|"+y+") "+v);							
						}
					} else {
						log.error("Player only has 1 Village after trying to Switch from ("+x+"|"+y+") to ("+position.x+"|"+position.y+")");				
					}
				}
			} else {
				log.trace("Player only has one village.");				
			}
			return page;
		}
	}

	/**
	 * Update village values from online page
	 * 
	 * @throws ConversationException
	 */
	public void update() throws ConversationException {
		Pattern p;
		Matcher m;
		Boolean	firstUpdate = false;
		// Fetch village page
		String page = gotoMainPage();
	    initQueueSystem(page);
	    // check if position already set
		if (position == null) { // above code may run without any errors too, but assume, it was not the original intention of the code writer to get those results it gives actually
			// set flag to show this is first update
			firstUpdate = true;
			// Fetch active village for several villages
			// 1 - Village name
			// 2 - Village x
			// 3 - village y
			p = util.getPattern("village.activeVillage");
			m = p.matcher(page);
			if (m.find()) {
				villageName = m.group(1);
				int x = Integer.parseInt(m.group(2));
				int y = Integer.parseInt(m.group(3));
				position = new Point(x, y);
				log.debug("Player has more than one village.");
			} else {
				log.debug("Player only has one village.");
				p = util.getPattern("village.villageUid");
				m = p.matcher(page);
				String profilePage = "";
				if (m.find()) {
					profilePage = Util.getFullUrl(villageUrlString, "spieler.php?uid=" + m.group(1));
				} else {
					util.saveTestPattern("village.villageUid", p, page);
					throw new ConversationException("Can't find village uid");
				}

				String page2 = util.httpGetPage(profilePage, false);

				// Fetch active village for one village
				// 1 - Village name
				// 2 - Village x
				// 3 - village y
				// p = Pattern.compile(Util.P_FLAGS + "<tr><td [^>]*><a [^>]*>([^<]*)</a>[^<]*<span [^>]*>[^<]*</span></td>[^<]*<td>[^<]*</td>[^<]*<td>\\((-?\\d+)\\|(-?\\d+)\\)</td>[^<]*</tr>");
				p = util.getPattern("village.villageList");
				m = p.matcher(page2);

				if (m.find()) {
					villageName = m.group(1);
					int x = Integer.parseInt(m.group(2));
					int y = Integer.parseInt(m.group(3));
					position = new Point(x, y);
				} else {
					util.saveTestPattern("village.VillageList", p, page2);
					throw new ConversationException("Can't find village name");
				}
				// GAC go back to main page as next tests assume it, position is now set
				page = gotoMainPage();
			}
			// create a valley to store info on this village
			Valley v = new Valley(util, position.x, position.y);
			// temp.fetch(util.getServerId(), position.x, position.y);
			v.setName(villageName);
			// save it
			v.save();
		} else {
			// check on correct village
			p = util.getPattern("village.activeVillage");
			m = p.matcher(page);
			if (m.find()) {
				String v = m.group(1);
				int x = Integer.parseInt(m.group(2));
				int y = Integer.parseInt(m.group(3));
				log.trace("Player has more than one village.  This is ("+x+"|"+y+") "+v);
				// check correct
				if ((x != position.x) || (y != position.y)) {
					log.warn("Switching Village AGAIN from ("+x+"|"+y+") to ("+position.x+"|"+position.y+")");
					page = gotoConfigUrl(true);
					// check again!
					p = util.getPattern("village.activeVillage");
					m = p.matcher(page);
					if (m.find()) {
						v = m.group(1);
						x = Integer.parseInt(m.group(2));
						y = Integer.parseInt(m.group(3));
						log.trace("Now ("+x+"|"+y+") "+v);
						// check correct
						if ((x != position.x) || (y != position.y)) {
							log.error("Failed to switching Village to ("+position.x+"|"+position.y+")");
							// make fatal?
						}
					} else {
						log.error("Player Now only has 1 Village!");				
					}
				}
			} else {
				log.trace("Player only has one village.");				
			}
		}
		

		log.trace("Village "+config.getString("/@desc", "")+" at "+positionString()+" is called \"" + villageName + "\"");

	    // GAC - check for troop movements - do early in checks so timing is as accurate as possible 
		updateTroopMovements(page);
		
		// Check if construction undergoing and fetch end time
		// p = Pattern.compile(Util.P_FLAGS + "<img src=\".*?img/un/a/del.gif\".*?<td>(\\D+?) \\(.*?<span id=timer.*?>(\\d?\\d:\\d?\\d:\\d?\\d)</span> ");
		p = util.getPattern("village.completionTime");
		m = p.matcher(page); // fofo edited and removed h. at the end of pattern string to make it work on .no servers
		mFieldQueue.clear();
		mBuildingQueue.clear();
		while (m.find()) {
			String type = m.group(1);
			String timeTaken = m.group(2);
			Date localCompletionTime = util.getCompletionTime(timeTaken);
			if (localCompletionTime == null || localCompletionTime.before(new Date())) {
				log.debug("Invalid localCompletionTime (ignored): " + localCompletionTime);
				continue;
			}
			if (translator.isField(type)) {
				log.debug("Field on queue: " + type);
				mFieldQueue.add(localCompletionTime);
			} else {
				log.debug("Building on queue: " + type);
				mBuildingQueue.add(localCompletionTime);    
			}
			log.debug("localCompletionTime = " + localCompletionTime);
		}

		log.debug("Queues available at: " + (mFieldQueue.size()>0?mFieldQueue:"now") + " / " + (mBuildingQueue.size()>0?mBuildingQueue:"now"));
	    // Available resources & resource production
	    for (ResourceType resourceType : ResourceType.values()) {
	         String imageClassOrPath = resourceType.getImageClassOrPath();
	         p = util.getPattern("village.availableResources", imageClassOrPath);
	         m = p.matcher(page);
	         if (m.find()) {
	            availableResources.put(resourceType, Integer.parseInt(m.group(1)));
	            maxResources.put(resourceType, Integer.parseInt(m.group(2)));
	            // log.debug("availableResources " + resourceType.name() + " " + m.group(1) + "/" + m.group(2));
	         } else {
	            util.saveTestPattern("village.AvailableResources", p, page);
	            throw new ConversationException("Can't find resources");
	         }
	         if (resourceType == ResourceType.FOOD) {
		            continue;
		         }
		         // <td><img class="res" src="img/un/r/4.gif"></td><td>Cereais:</td><td align="right"><b>-8&nbsp;</b></td><td>por hora</td>
		         p = util.getPattern("village.production", imageClassOrPath);
		         m = p.matcher(page);
		         if (m.find()) {
		            production.put(resourceType, Integer.parseInt(m.group(1)));
		            // log.debug("production " + resourceType.name() + " " + m.group(1));
		         } else {
		            util.saveTestPattern("village.production", p, page);
		            throw new ConversationException("Can't find resource production for \"" + resourceType + "\"");
		         }
	    }

	    // GAC report first update
	    if (firstUpdate) {
	    	EventLog.log(config.getString("/@desc", "")+" \""+villageName+"\""+" "+positionString()+
		    		  " Resource Production: "+production.toStringNoFood()+" Available: "+availableResources.toStringNoFood());	    	  
	    } else {
		      log.debug(config.getString("/@desc", "")+" \""+villageName+"\""+" "+positionString()+
		    		  " Resource Production: "+production.toStringNoFood()+" Available: "+availableResources.toStringNoFood());	    	
	    }
	    
		buildingMap = new BuildingTypeToBuildingMap();
		// Find fields
		this.fields = new HashMap<String, Field>();
		String levelPattern = translator.getMatchingPattern(Translator.LEVEL);
		// <area href="build.php?id=1" coords="101,33,28" shape="circle" title="Segheria livello 11">
		// <area href="build.php?id=1"	coords="101,33,28" shape="circle"title="Woodcutter level 6"/>
		// gac v35  p = Pattern.compile(Util.P_FLAGS + "<area *href=\"build\\.php\\?id=(\\d+)\"[^>]*?shape=\"circle\"\\s*title=\"(" + P_NOQUOTES + ")\\s" + levelPattern + "\\s(\\d+)");		
		// guest v35 p = Pattern.compile(Util.P_FLAGS + "<area\\s*href=\"build\\.php\\?id=(\\d+)\"[^>]*?shape=\"circle\"\\s*title=\"(" + P_NOQUOTES + ") " + levelPattern + " (\\d+)\"/?>");
		// p = Pattern.compile(Util.P_FLAGS + "<area *href=\"build\\.php\\?id=(\\d+)\"[^>]*?shape=\"circle\" *title=\"(" + P_NOQUOTES + ") " + levelPattern + " (\\d+)\">");
		p = util.getPattern("village.fields", levelPattern);
		m = p.matcher(page);
		int fieldsFound = 0;
		while (m.find()) {
			String itemIdString = m.group(1);
			String fieldTypeString = m.group(2);
			String fieldLevel = m.group(3);
			String name = fieldTypeString + " level " + fieldLevel;
			// log.debug("GAC Field Info " + name );
			String urlEnd = "build.php?id=" + itemIdString;
			String url = Util.getFullUrl(villageUrlString, urlEnd);
			BuildingType fieldType = BuildingType.fromKey(translator.getKeyword(fieldTypeString));
			if (fieldType!=null) {
				Field item = new Field(name, url, translator);
				item.setCurrentLevel(fieldLevel);
				item.setType(fieldType);
				this.fields.put(itemIdString, item);
				this.buildingMap.put(fieldType, item);
				fieldsFound++;
			} else {
				throw new ConversationException("Field \"" + fieldTypeString + "\" not found in language config file");
			}
		}
		if (fieldsFound < 18) {
			util.saveTestPattern("village.fields (" + fieldsFound + ")", p, page);
			String msg = Util.getLocalMessage("msg.fieldsNotFound", this.getClass());
			// dont give up if a survey page
			if (!util.isSurveyPage(page)) {
				throw new ServerFatalException(msg);
			}
		}

		// Find village center url
		// V35 p = Pattern.compile("(?s)area\\s*href=\"(dorf2.*?)\"\\s*coords=\".*?\"\\s*shape=\"circle\"");
		// p = Pattern.compile("(?s)area *href=\"(dorf2.*?)\" *coords=\".*?\" *shape=\"circle\"");
		p = util.getPattern("village.centreUrl");
		m = p.matcher(page);
		if (m.find()) {
			villageSecondUrlString = Util.getFullUrl(villageUrlString, m.group(1));
		} else {
			util.saveTestPattern("village.centreUrl", p, page);
			throw new ConversationException("Can't find village center");
		}
		// Fetch village center page
		page = util.httpGetPage(villageSecondUrlString);

		// Find buildings
		this.buildings = new HashMap<String, Building>();
		// <area href="build.php?id=20" title="Magazzino livello 2" coords="136,66,136,12,211,12,211,66,174,87" shape="poly">
		// <area href="build.php?id=19" title="spazio edificabile"
		// coords="53,91,91,71,127,91,91,112" shape="poly">
		// I'm using (?:xxxxx)? to match empty sites as well
		// p = Pattern.compile(Util.P_FLAGS + "<area *href=\"build\\.php\\?id=(\\d+)\" *title=\"(" + P_NOQUOTES + ")(?: " + levelPattern + " (\\d+))?\"" + P_ANYWHITESPACE + "coords=" + P_CLOSETAG);
		// v35 p = Pattern.compile(Util.P_FLAGS + "<area\\s*href=\"build\\.php\\?id=(\\d+)\"[^>]*?title=\"(" + P_NOQUOTES + ")(?: " + levelPattern + " (\\d+))?\"");
		p = util.getPattern("village.buildings", levelPattern);
		m = p.matcher(page);
		int sitesFound = 0;
		hasRallyPoint = true;
		while (m.find()) {
			String itemIdString = m.group(1); // e.g. "34"
			String buildingTypeString = m.group(2); // e.g. "Caserma", "Spazio edificabile", "altro spazio edificabile"
			String buildingLevel = m.group(3); // e.g. "3" or null
			String name = buildingTypeString + (buildingLevel != null ? " level " + buildingLevel : "");
			// log.debug("GAC building info " + m.group(1) + " " + m.group(2) + " " + m.group(3) );
			String urlEnd = "build.php?id=" + itemIdString;
			String url = Util.getFullUrl(villageUrlString, urlEnd);
			// Make instance
			if (itemIdString.equals(RALLYPOINT_ID) && (buildingLevel == null || "0".equals(buildingLevel))) {
				this.buildings.put(itemIdString, new RallyPointEmptySite(name, url, translator));
				hasRallyPoint = false; // Still empty
			} else if (itemIdString.equals(WALL_ID) && (buildingLevel == null || "0".equals(buildingLevel))) {
				if (this.buildings.containsKey(itemIdString)) {
					continue; // happens 3 times in total
				}
				this.buildings.put(itemIdString, new WallEmptySite(name, url, translator));
			} else {
				String buildingKey = translator.getKeyword(buildingTypeString);
				if (buildingKey != null) {
						BuildingType buildingType = BuildingType.fromKey(buildingKey);
						if (buildingType != null) {
							Building item = (Building) buildingType.getInstance(name, url, translator);
							item.setCurrentLevel(buildingLevel); // This works fine when buildingLevel=null for EmptySite because EmptySite ignores it
							item.setType(buildingType);
							this.buildings.put(itemIdString, item);
							this.buildingMap.put(buildingType, item);
						} else {
							throw new ConversationException("Building \"" + buildingTypeString + "\" not implemented yet");
						}
					}
			}
			sitesFound++;
		}
		if (sitesFound < 22) {
			util.saveTestPattern("village.buildings", p, page);
			String msg = Util.getLocalMessage("msg.buildingsNotFound", this.getClass());
			// dont give up if a survey page
			if (!util.isSurveyPage(page)) {
				throw new ServerFatalException(msg);
			}
		}
		// System.exit(0);
	}

	public Util getUtil() {
		return util;
	}

	public String getDesc() {
		String s = villageName != null ? villageName : "?";
		return config.getString("/@desc", s);
	}

	public Collection<Field> getFields() {
		return fields.values();
	}

	public Collection<Building> getBuildings() {
		return buildings.values();
	}

	public int getAvailableFood() {
		// if this was another resource type just return available value
		// but food stores rates, so max - available gives consumption rate per hour
		return maxResources.get(ResourceType.FOOD) - availableResources.get(ResourceType.FOOD);
	}

	public ResourceTypeMap getProduction() {
		return production;
	}

	public ResourceTypeMap getAvailableResources() {
		return availableResources;
	}

	public ResourceTypeMap getMaxResources() {
		return maxResources;
	}

	public ResourceTypeMap getSpareResources(SubnodeConfiguration config) {
		ResourceTypeMap spareResources = new ResourceTypeMap();
        /* int keepWood = config.getInt("/keepResources/@wood", 0);
        int keepClay = config.getInt("/keepResources/@clay", 0);
        int keepIron = config.getInt("/keepResources/@iron", 0);
        int keepCrop = config.getInt("/keepResources/@crop", 0); */
        ResourceTypeMap available = this.getAvailableResources();
        for (ResourceType res : available.keySet()) {
            if (res == ResourceType.FOOD) {
                continue;
            }
            // as a loop use name from resource map but also try lower case 
            int keep = config.getInt("/keepResources/@"+res.name(), 0);
            if (keep == 0) {
                keep = config.getInt("/keepResources/@"+res.name().toLowerCase(), 0);            	
            }
            if (keep < available.get(res)) {
            	spareResources.put(res, available.get(res) - keep);
            } else {
            	spareResources.put(res, 0);            	
            }
            log.debug("/keepResources/"+res.name()+
            		" got:"+available.get(res)+
            		" keep:"+keep+
            		" spare:"+spareResources.get(res));            
        }
		return spareResources;
	}
	
	public boolean checkSpareResources(SubnodeConfiguration config) {
		// ResourceTypeMap spareResources = new ResourceTypeMap();
		// spareResources = getSpareResources(config);
		// EventLog.log("Spare Resources:"+spareResources);
        ResourceTypeMap available = this.getAvailableResources();
        for (ResourceType res : available.keySet()) {
            if (res == ResourceType.FOOD) {
                continue;
            }
            // as a loop use name from resource map but also try lower case 
            int keepr = config.getInt("/keepResources/@"+res.name(), 0);
            if (keepr == 0) {
                keepr = config.getInt("/keepResources/@"+res.name().toLowerCase(), 0);            	
            }
            if (keepr < available.get(res)) {
                log.trace("/keepResources/"+res.name()+"="+keepr+" got:"+available.get(res));
            } else {
                log.debug("Not enough /keepResources/"+res.name()+"="+keepr+" got:"+available.get(res));
            	return false;
            }
        }
		return true;
	}
	
	public Translator getTranslator() {
		return translator;
	}

	public synchronized void execute() throws ConversationException {
		NDC.push("(" + config.getString("/@desc", "?") + ")");
		boolean needsUpdate = true;
		log.trace("Start village.execute, needsUpdate is "+needsUpdate);
		this. forceOnReload=config.getBoolean("/@ forceOnReload", true);
		try {
			// Run through each strategy
			for (Strategy strategy : this.strategyList) {
				if (strategy.isDeleted() || util.getConsole().isQuitting()) {
					continue;
				}
				try {
					util.getConsole().checkFlags();
					if (needsUpdate) { //needs to be above getTimeWhenRunnable for the resource wait tests to function properly. fofo
						this.update(); // Refresh village properties and position to this village
						log.trace("Village updated for strategy "+strategy.getDesc());
					}
					TimeWhenRunnable timeWhenRunnable = strategy.getTimeWhenRunnable();
					if (needsUpdate) {
						update(); // Refresh village properties and position to this village
						needsUpdate = false;
					}
					
					// check if ready - NEVER normally set when finished, not in the near future if just waiting
					/* if ((timeWhenRunnable == null) ||
							((timeWhenRunnable.isOpportunist()) && (timeWhenRunnable.getTime() < Util.NotInTheNearFuture())) || 
							timeWhenRunnable.before(new Date()) || 
							((timeWhenRunnable.getTime() < Util.NotInTheNearFuture()) && resourcesAvailable(strategy.getTriggeringResources()) && !strategy.isWaiting())) {
					*/
					// change from complex test to set of simple if statements with tracing
					boolean execute = false;
					if (timeWhenRunnable == null) {
						execute = true;
					} else if ((timeWhenRunnable != TimeWhenRunnable.NEVER) && !strategy.isWaiting()) {
						if (timeWhenRunnable.isOpportunist()) {
							execute = true;
						} else if (timeWhenRunnable.before(new Date())) {
							execute = true;
						} else if (resourcesAvailable(strategy.getTriggeringResources()) ) {
							execute = true;
							if (log.isDebugEnabled()) {
								EventLog.log("Strategy triggered by resources");
							}
						}
					} else {
						log.trace("Waiting "+strategy.isWaiting()+" or Finished "+timeWhenRunnable);
					}
					if (execute) {
						EventLog.log("Executing strategy \"" + strategy.getDesc() + "\"");

						if (needsUpdate) {
							update(); // Refresh village properties and position to this village
							needsUpdate = false;
						} else {
							// always force back to local dorf1 before selecting this strategy
							// is this needed with update above?
							gotoMainPage();
						}
						
						TimeWhenRunnable nextTime = strategy.execute();
						if (nextTime == null) { // Just to be safe
							EventLog.log("Timewhenrunnable = null returned");
							log.warn("(Internal Error) Shouldn't return null");
							nextTime = new TimeWhenRunnable(System.currentTimeMillis());
						}
						log.debug(String.format("Strategy %s will be run after %s ", strategy.getDesc(), nextTime.getTimeWhenRunnable()));
						strategy.setTimeWhenRunnable(nextTime);
						needsUpdate = strategy.modifiesResources();
					}
				} catch (ConversationException e) {
					String s = "Error while executing strategy \"" + strategy.getDesc() + "\" (skipping)";
					Util.log(s, e);
					EventLog.log(s);
					util.shortestPause(false); // Just to be safe
					//throw e;
					// Just keep going to the next strategy
				} catch (SkipRequested e) {
					// Skip strategy: just keep going
					log.debug("Skipped");
				} catch (SkipVillageRequested e) {
					// Propagate
					log.debug("Village skipped");
					throw e;
				} catch (TranslationException e) {
					EventLog.log(e.getMessage());
					log.error("Translation error", e);
					// Keep going
				} catch (ServerFatalException e) {
					throw e; // Need to quit all villages
				} catch (Exception e) {
					// Any other exception skips the strategy but keeps the village going
					// so that bugs in one strategy don't prevent other strategies from executing
					String message = EventLog.log("msg.strategyException", this.getClass(), strategy.toString());
					log.error(message, e);
				}
			}
		} finally {
			NDC.pop();
		}
	}

	public boolean resourcesAvailable(ResourceTypeMap resMap) {
		if (resMap == null) {
			return false;
		}
		for (ResourceType resource : availableResources.keySet()) {
			if (availableResources.get(resource) < resMap.get(resource)) {
				return false;
			}
		}
		return true;
	}

	public TimeWhenRunnable getTimeWhenRunnable() throws ConversationException {
		// Find the earliest strategy date
		TimeWhenRunnable result = TimeWhenRunnable.NEVER; // Runnable false
		boolean updateDone = false;
		for (Strategy strategy : this.strategies.values()) {
			TimeWhenRunnable timeWhenRunnable = strategy.getTimeWhenRunnable();
			ResourceTypeMap triggeringRes = strategy.getTriggeringResources();
			if ((timeWhenRunnable != null) && (timeWhenRunnable != TimeWhenRunnable.NEVER) && (timeWhenRunnable.after(new Date()))
					&& (triggeringRes != null && !updateDone)) {
				// Update resources if they might trigger
				// log.trace("village "+this.getDesc()+" strategy "+strategy.getId()+" checking resources");
				update();
				updateDone = true;
			}
			if ((timeWhenRunnable == null) || ((timeWhenRunnable != TimeWhenRunnable.NEVER) && resourcesAvailable(triggeringRes))) {
				return new TimeWhenRunnable(System.currentTimeMillis() - 1); // run now
			} else if (timeWhenRunnable.before(result)) {
				// log.trace("village "+this.getDesc()+" Earlier strategy "+strategy.getId()+" at "+timeWhenRunnable);				
				result = timeWhenRunnable;
			}
			
		}
		// log.debug(this.getDesc()+" returning timeWhenRunnable:"+result);		
		return result;
	}
	public TimeWhenRunnable getTimeWhenSharp() throws ConversationException {
		// Find the earliest sharp strategy date
		TimeWhenRunnable result = TimeWhenRunnable.NEVER; // Runnable false
		for (Strategy strategy : this.strategies.values()) {
			TimeWhenRunnable timeWhenRunnable = strategy.getTimeWhenRunnable();
			if ((timeWhenRunnable != null) && timeWhenRunnable.isSharp()) {
				if (timeWhenRunnable.before(result)) {
					result = timeWhenRunnable;
					log.trace("village "+this.getDesc()+" strategy "+strategy.getId()+"sharp @"+result);
				}
			}			
		}
		Boolean opportunist = this.config.getBoolean("/@opportunist", false);
		// EventLog.log("strategy ." + this.getDesc() + ". opportunist " + Boolean.toString(opportunist));
		if (result != null) {
			result.setOpportunist(opportunist);
		}
		return result;
	}

	private int after(List<Date> coll, Date now) {
        int after = 0;
        for (Date date : coll) {
            if (date.after(now)) {
                after++;
            }
        }
        return after;
    }

	public boolean fieldQueueAvailable() {
	    Date now = new Date();
	    int ongoingFields = after(mFieldQueue, now);
	    int ongoingBuildings = after(mBuildingQueue, now);
	    return mFieldQueueLength - ongoingFields > 0 && ongoingFields + ongoingBuildings < mMaxJobs; 
	}

	public boolean buildingQueueAvailable() {
	    Date now = new Date();
        int ongoingFields = after(mFieldQueue, now);
        int ongoingBuildings = after(mBuildingQueue, now);
        return mBuildingQueueLength - ongoingBuildings > 0 && ongoingFields + ongoingBuildings < mMaxJobs;
	}
	
	private Date getSmallestTimeStamp(Date now) {
        Date building = mBuildingQueue.isEmpty() ? null : mBuildingQueue.get(0);
        Date field = mFieldQueue.isEmpty() ? null : mFieldQueue.get(0);
        if (field == null && building == null) {
            log.error("Both queue are emtpy, should never get here!");
            return now;
        } else if (field == null && building != null) {
            return building;
        } else if (building == null && field != null) {
            return field;
        } else if (building.before(field)) {
            return building;
        } else {
            return field;
        }
	}
	
	public Date getBuildingQueueAvailableTime() {
	    Date now = new Date();
	    if (mBuildingQueue.size() < mBuildingQueueLength) {
	        // the queue is not full check the free slots.
	        if (mBuildingQueue.size() + mFieldQueue.size() < mMaxJobs) {
	            // we have free slots
	            return now;
	        } else {
	            // no free slots return the smallest time stamp
	            return getSmallestTimeStamp(now);
	        }
	    } else {
	        // building queue is full the queue will be available when the
	        // first entry is done.
	        return mBuildingQueue.get(0);
	    }
	}

	public Date getFieldQueueAvailableTime() {
        Date now = new Date();
        if (mFieldQueue.size() < mFieldQueueLength) {
            // the queue is not full check the free slots.
            if (mBuildingQueue.size() + mFieldQueue.size() < mMaxJobs) {
                // we have free slots
                return now;
            } else {
                // no free slots return the smallest time stamp
                return getSmallestTimeStamp(now);
            }
        } else {
            // building queue is full the queue will be available when the
            // first entry is done.
            return mFieldQueue.get(0);
        }
	}

	public Date getFirstAvailableQueueTime() {
	    Date field = getFieldQueueAvailableTime();
	    Date building = getBuildingQueueAvailableTime();
	    if (field.before(building)) {
	        return field;
	    }
	    return building;
	}

	public Date getQueueAvailableTime(UpgradeableSite item) {
	    if (item instanceof Field) {
	       return getFieldQueueAvailableTime();
	    } else {
	       return getBuildingQueueAvailableTime();
	    }
	}

	public boolean constructionQueueAvailable(UpgradeableSite item) {
		Date now = new Date();
		return getQueueAvailableTime(item).before(now);
	}

	public RallyPoint getRallyPoint() {
		// check if buildings set
		if (buildings != null) {
			return (RallyPoint) (hasRallyPoint ? buildings.get(RALLYPOINT_ID) : null);			
		}
		return null;
	}

	public String getVillageName() {
		return villageName;
	}

	public String getVillageUrlString() {
		return villageUrlString;
	}

	public UpgradeableSite getItem(String itemId) {
		UpgradeableSite item = fields.get(itemId);
		return item != null ? item : buildings.get(itemId);
	}

	public void terminate() {
		EventLog.log("evt.villageTerminated", this.getClass(), getDesc());
	}

	public synchronized void updateConfig(SubnodeConfiguration villageConfig, Util newUtil) {
		// Need to pass util as well, because it has the new serverConfig inside
		this.util = newUtil;
		this.config = villageConfig;
		log.debug("Updating village config for " + getDesc());
		strategyList = util.getServer().updateStrategies("/strategy[@enabled='true']", config, strategies, this);
	}



	public Collection<Strategy> getStrategies() {
		return strategies.values();
	}

	public ResourceTypeToFieldMap getLowestFieldPerType() throws InvalidConfigurationException {
		Collection<Field> fields = getFields();
		// For each type, find field at lowest level
		ResourceTypeToFieldMap candidatesPerType = new ResourceTypeToFieldMap();
		for (Field item : fields) {
			ResourceType resourceType = item.getResourceType();
			Field candidate = candidatesPerType.get(resourceType);
			if (candidate == null || item.getCurrentLevel() < candidate.getCurrentLevel()) {
				candidatesPerType.put(resourceType, item);
			}
		}
		return candidatesPerType;
	}

	// This was @deprecated but I can't see why [xtian]
	public MarketSite getMarket() {
		return (MarketSite) buildingMap.getOne(BuildingType.MARKETPLACE);
	}

	/**
	 * get current minimum level for named building or resource field
	 * @param buildingName
	 * @param nameString
	 * @return current minimum level
	 */
	public int getMinLevel(String nameString) {
		int currentLevel = 0;
		// problem as key.name does not match building type, eg key.clay != CLAY_PIT("clayPit", Field.class)
		// note need to handle resource differently anyway and always check for resource first even if fixed 
		// as if treating resource as building getOne returns first field which may not be lowest
		try {
			String keyString = translator.getKeyword(nameString); // TranslationException if buildingName not found
			try {
				ResourceType	resType = ResourceType.fromString(keyString);
				// check info available
				if (fields != null) {
					ResourceTypeToFieldMap resPerType = getLowestFieldPerType();
					if (resPerType != null) {
						currentLevel = (resPerType.get(resType)).getCurrentLevel();				
					}					
				}
				log.trace("min Resource "+resType.name()+" Level="+currentLevel);
			} catch (IllegalArgumentException iae){
				// assume not a field
				BuildingType buildingType = BuildingType.fromKey(keyString); // null if no /@building or name not a building
				/* if (translator.isField(waitFor)) {
					log.trace(waitForKey+" Field Translated to Building ok");
				} */
				// check info available
				if ((buildings != null) && (buildingMap != null) && (buildingType != null)) {
					List<Building>	waitForBuildings = getBuildingMap().getAll(buildingType);
					// Building	waitForBuilding = getBuildingMap().getOne(buildingType);
					// currentLevel = waitForBuilding.getCurrentLevel();								
					if (waitForBuildings != null) {
						for (int i=0 ; i < waitForBuildings.size() ; i++) {
							int	bLevel = waitForBuildings.get(i).getCurrentLevel();
							// log.trace(buildingType.name()+" + level="+bLevel);
							// add all together - useful if want to wait for 2 warehouses to build something 
							currentLevel += bLevel;
							/* store largest - if want to make sure one is built
							if (bLevel > currentLevel) {
								currentLevel = bLevel;
							} */
							/* store lowest - true min but not a lot of use for buildings
							if ((currentLevel = 0) || (bLevel < currentLevel)) {
								currentLevel = bLevel;
							} */
						}
					}
					log.trace("min Building "+buildingType.name()+" level="+currentLevel);					
				} else {
					log.trace("Cannot find "+nameString+"/"+keyString+" level "+currentLevel+" assumed");					
				}
			} catch (InvalidConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (TranslationException e) {
			// just report and treat as level 0
			EventLog.log("Cannot find "+nameString+" in Language File, level 0 assumed");
			log.error("Cannot find "+nameString+" in Language File, level 0 assumed");
		}
		return currentLevel;
	}

	
	/**
	 * The BuildingTypeToBuildingMap can be used to retrieve any available building/field in the village.
	 * 
	 * @return
	 */
	public BuildingTypeToBuildingMap getBuildingMap() {
		return buildingMap;
	}

	public Point getPosition() {
		return position;
	}
	
	public String positionString() {
		return "(" + position.x + "|" + position.y + ")";
	}

	/**
	 * @return the village id taken from the configuration
	 * @throws InvalidConfigurationException 
	 */
	public static String getIdFromConfig(SubnodeConfiguration villageConfig) throws InvalidConfigurationException {
		try {
			String villageUrlString = villageConfig.getString("/url");
			return villageConfig.getString("/@uid", villageUrlString); // If uid is missing, use villageUrlString
		} catch (NoSuchElementException e) {
			String villageDesc = villageConfig.getString("/@desc", "(nodesc)");
			String message = EventLog.log("msg.villageUrlMissing", Village.class, villageDesc);
			throw new InvalidConfigurationException(message);
		}
	}

	public String getId() {
		return id;
	}
	

	public	Date	updateTroopMovements() {
		try {
			// get page quickly
			return 	updateTroopMovements(gotoMainPage(true));
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// is it safe to return now - may be better to return a long time
			return	new Date();
		}
	}
	/** 
	 * read latest troop movement information from main page
	 * @param page
	 * @return
	 */
	public	Date	updateTroopMovements(String page) {
	    // GAC - check for troop movements - do early in checks so timing is as accurate as possible 
		// store info so can be accessed by a strategy or just check in strategy, better if do here and can run it?
	    // possibly by setting something that returns check resources, also need to use quick waits
		Date	earliestTime = new Date();
		Pattern p = util.getPattern("village.troopMovement");
		Matcher m = p.matcher(page); 
		troopMoveNumber.clear();
		troopMoveType.clear();
		troopMoveNextSecs.clear();
		// clear all types
		nextIncomingAttackTime = null;
		nextIncomingReinfTime = null;
		nextOutgoingAttackTime = null;
		nextOutgoingReinfTime = null;
		while (m.find()) {
			// String dInfo = "";
			// for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
			// log.debug("village.troopMovement"+": "+dInfo);

            // parse page and action time quickly
			String timeTaken = m.group(7);
			Date localCompletionTime = util.getCompletionTime(timeTaken);
			if (localCompletionTime == null || localCompletionTime.before(new Date())) {
				log.warn("Invalid localCompletionTime (ignored): " + localCompletionTime);
				continue;
			}
			String movementType = m.group(4);
			troopMoveType.add(m.group(6));
			troopMoveNumber.add(Integer.parseInt(m.group(5)));
			troopMoveNextSecs.add(util.timeToSeconds(timeTaken));
			troopMoveNextDate.add(localCompletionTime);
			log.trace("Troop Movement:"+movementType+": "+troopMoveNumber.get(troopMoveNumber.size()-1)+" "+troopMoveType.get(troopMoveType.size()-1)+" in "+timeTaken+" at " + localCompletionTime);
			// store type
			if (movementType.equals("a1")) {
				// incoming attack
				// EventLog.log("Incoming Attack in "+timeTaken+" at " + localCompletionTime);
				log.debug("Incoming Attack in "+timeTaken+" at " + localCompletionTime);
				nextIncomingAttackTime = localCompletionTime;
			} else if (movementType.equals("a2")) {
				nextOutgoingAttackTime = localCompletionTime;
			} else if (movementType.equals("a3")) {
				// incoming attack on oasis
				log.debug("Incoming Attack on Oasis in "+timeTaken+" at " + localCompletionTime);
				// not something to dodge
				// nextIncomingAttackTime = localCompletionTime;
			} else if (movementType.equals("d1")) {
				// incoming defence
				nextIncomingReinfTime = localCompletionTime;
			} else if (movementType.equals("d2")) {
				nextOutgoingReinfTime = localCompletionTime;
			} else if (movementType.equals("d3")) {
				// incoming to oasis
				nextIncomingReinfTime = localCompletionTime;
			} else {
				log.warn("Unknown Troop Movement Type: "+movementType);
			}
			// update earliest
			if (localCompletionTime.before(earliestTime)) {
				earliestTime = localCompletionTime;
			}
		    // System.exit(0);
		}
		return earliestTime;
	}
	/**
	 * 
	 * @return the time of first incoming attack on village or null if none
	 */
	public	Date	getNextAttackTime() {
		return nextIncomingAttackTime;
	}
	
	private void initQueueSystem(String page) {
	    Map<TribeType, IntPair> queueConfigNoPlus = new TreeMap<TribeType, IntPair>();
        queueConfigNoPlus.put(TribeType.ROMANS, new IntPair(1, 2));
        queueConfigNoPlus.put(TribeType.GAULS, new IntPair(1, 1));
        queueConfigNoPlus.put(TribeType.TEUTONS, new IntPair(1, 1));
        Map<TribeType, IntPair> queueConfigPlus = new TreeMap<TribeType, IntPair>();
        queueConfigPlus.put(TribeType.ROMANS, new IntPair(2, 3));
        queueConfigPlus.put(TribeType.GAULS, new IntPair(2, 2));
        queueConfigPlus.put(TribeType.TEUTONS, new IntPair(2, 2));

        boolean hasTravianPlus = doesTravianPlusAvailable(page);
        IntPair queueConfig = new IntPair(1, 1);
        if (hasTravianPlus) {
        	log.info("Travian plus active.");
            queueConfig = queueConfigPlus.get(util.getTribeType());
        } else {
        	log.info("Travian plus inactive.");
            queueConfig = queueConfigNoPlus.get(util.getTribeType());
        }
        mBuildingQueueLength = queueConfig.first;
        mFieldQueueLength = queueConfig.first;
        mMaxJobs = queueConfig.second;
        log.debug("Tribe queue (field/build)[max]: (" + mFieldQueueLength + "/" + mBuildingQueueLength + ")[" + mMaxJobs + "]");
    }

    private boolean doesTravianPlusAvailable(String page) {
        // Pattern p = Pattern.compile("img/.*?/a/travian1.gif");
		Pattern p = util.getPattern("village.travianPlus");
        Matcher m = p.matcher(page);
        return m.find();
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	if (this.villageName!=null) {
    		result.append("\"" + villageName + "\""); 
    	}
    	result.append("[" + this.id + "]");
    	return result.toString();
    }
    public double getAverageFieldLevel (BuildingType buildingType)  {
    	//finds the average level for the field type set in buildingType
    	int count=0;
    	int lvlSum=0;
    	double avgLvl=0;
    	UpgradeableSite mySite=null; 
    	if (fields==null) {
    		try {
				this.update();
			} catch (ConversationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	if (fields != null) {
           	int i=1;
        	while (i<=18)
        	{
        		i++;
        		mySite=this.getItem(Integer.toString(i));
        		//log.trace("average level loop " + mySite.toString());
        		if (mySite != null) {
            		if (mySite.getType()==buildingType) {
                		log.trace("item match " + mySite.toString());
            			lvlSum=lvlSum+mySite.getCurrentLevel();
            			count++;
            		}   			
        		}
        	}
    	}
    	else {
    		log.debug("village.fields is null");
    	}
    	if (count>0) {
    	   	avgLvl = (double) lvlSum / (double) count;   		
    	}
    	
    	return avgLvl;

    	}
}

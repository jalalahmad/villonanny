package net.villonanny.strategy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.ReportMessageReader;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.ReportMessageReader.EventPage;
import net.villonanny.ReportMessageReader.Message;
import net.villonanny.ReportMessageReader.MessageType;
import net.villonanny.ReportMessageReader.Report;
import net.villonanny.ReportMessageReader.ReportType;
import net.villonanny.Util.Pair;
import net.villonanny.Util.Pair.IntPair;
import net.villonanny.entity.Building;
import net.villonanny.entity.EmptySite;
import net.villonanny.entity.Field;
import net.villonanny.entity.RallyPointEmptySite;
import net.villonanny.entity.UpgradeableSite;
import net.villonanny.entity.Village;
import net.villonanny.entity.WallEmptySite;
import net.villonanny.type.BuildingType;
import net.villonanny.type.BuildingTypeToBuildingMap;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.ResourceTypeToFieldMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 * QuestSolver. Solves the initial quests.
 * 
 * <strategy class="QuestSolver" desc="QuestSolver" enabled="true">
 * <villageName>rndVillage 1</villageName>
 * ...
 * <villageName>rndVillage n</villageName>
 *    <cranny id="38"/>
 *    <marketplace id="23"/> 
 *    <granary id="31"/> 
 *    <warehouse id="32"/> 
 *    <barracks id="34"/>
 *    <productionBonus>false</productionBonus>
 *</strategy>
 * 
 * @see http://travian.wikia.com/wiki/Quests
 * 
 * @author biminus
 * 
 */
public class QuestSolver extends Strategy {

    private static final Logger log = Logger.getLogger(QuestSolver.class);
    // no level, no count
    private static final IntPair L0_C0 = new IntPair(0, 0);
    // level 1, count [1246]
    private static final IntPair L1_C1 = new IntPair(1, 1);
    private static final IntPair L1_C2 = new IntPair(1, 2);
    private static final IntPair L1_C4 = new IntPair(1, 4);
    private static final IntPair L1_C6 = new IntPair(1, 6);
    // level 2, count [146]
    private static final IntPair L2_C1 = new IntPair(2, 1);
    private static final IntPair L2_C4 = new IntPair(2, 4);
    private static final IntPair L2_C6 = new IntPair(2, 6);

    private static final int QUEST_1 = 1;
    private static final int QUEST_2 = 2;
    private static final int QUEST_3 = 3;
    private static final int QUEST_4 = 4;
    private static final int QUEST_5 = 5;
    private static final int QUEST_6 = 6;
    private static final int QUEST_7 = 7;
    private static final int QUEST_8 = 8;
    private static final int QUEST_9 = 9;
    private static final int QUEST_10 = 10;
    private static final int QUEST_11 = 11;
    private static final int QUEST_12 = 12;
    private static final int QUEST_13 = 13;
    private static final int QUEST_14 = 14;
    private static final int QUEST_15 = 15;
    private static final int QUEST_16 = 16;
    private static final int QUEST_17 = 17;
    private static final int QUEST_18 = 18;
    private static final int QUEST_19 = 19;
    private static final int QUEST_20 = 20;
    private static final int QUEST_21 = 21;
    private static final int QUEST_22 = 22;
    
    private boolean	alternateQuests = false;		// indicate using simple resouce fetch not full quest system 
    
    
    private Map<Integer, Mission> mId2Mission = new TreeMap<Integer, Mission>();

    public QuestSolver() {
        super();
    }

    public void init(SubnodeConfiguration strategyConfig, Village village) {
        super.init(strategyConfig, village);
        initMissions();
    }

    public void updateConfig(SubnodeConfiguration strategyConfig, Util util) {
        super.updateConfig(strategyConfig, util);
        initMissions();
    }

    private void getConfigData(Map<BuildingType, Integer> cfg) {
        List<SubnodeConfiguration> itemNodes = config.configurationsAt("/item");
        for (SubnodeConfiguration itemNode : itemNodes) {
            String buildingName = itemNode.getString("/@building", null);
            String buildingKey = translator.getKeyword(buildingName);
            BuildingType desiredBuildingType = BuildingType.fromKey(buildingKey);
            if (desiredBuildingType == null) {
                log.error("Invalid building name: " + buildingName + ", or key: " + buildingKey);
                continue;
            }
            if (cfg.containsKey(desiredBuildingType)) {
                log.error("The building is already present, skipping this entry: " + desiredBuildingType + ", id: " + id);
                continue;
            }
            int id = itemNode.getInt("/@id", -1);
            if (id < 19 || id >40) {
                log.error("The specified id of " + buildingName + "is out of range: " + id);
                continue;
            }
            if (cfg.containsValue(id)) {
                log.error("This id(" + id + ") is already specified, skipping this entry: " + desiredBuildingType);
                continue;
            }
            log.debug("Adding entry to cfg " + desiredBuildingType + "(" + id + ")");
            cfg.put(desiredBuildingType, id);
        }
    }

    private void initMissions() {
        log.debug("QuestSolver starting");
        Map<BuildingType, Integer> cfg = new TreeMap<BuildingType, Integer>();
        getConfigData(cfg);
        mId2Mission.clear();
        mId2Mission.put(0, new SimpleMission(new QuestData(0), "val", "2"));
        // Quest 1: Woodcutter
        // Your reward: Woodcutter instantly complete
        mId2Mission.put(-QUEST_1, new BuildMine(new QuestData(-QUEST_1,
                "Quest 1: Woodcutter",
                "Your reward: Woodcutter instantly complete"), L1_C1, L0_C0,
                L0_C0, L0_C0));
        // Quest 2: Crop
        // Your reward: 1 Day Travian Plus
        mId2Mission
                .put(-QUEST_2, new BuildMine(new QuestData(-QUEST_2, "Quest 2: Crop",
                        "Your reward: 1 Day Travian Plus"), L0_C0, L0_C0,
                        L0_C0, L1_C1));
        // Quest 3: Your village's name
        // Your reward: Wood 30, Clay 60, Iron 30, Wheat 20
        mId2Mission.put(-QUEST_3, new VillageNameMission(new QuestData(-QUEST_3,
                "Quest 3: Your village's name",
                "Your reward: Wood 30, Clay 60, Iron 30, Wheat 20"), Arrays
                .asList(config.getStringArray("/villageName"))));
        // Quest 4: Other players
        // Your reward: Wood 40, Clay 30, Iron 20, Wheat 30
        mId2Mission.put(-QUEST_4, new RankMission(new QuestData(-QUEST_4,
                "Quest 4: Other players",
                "Your reward: Wood 40, Clay 30, Iron 20, Wheat 30")));
        // Quest 5: Two Building Orders
        // Your reward: Wood 50, Clay 60, Iron 30, Wheat 30
        mId2Mission.put(-QUEST_5, new BuildMine(new QuestData(-QUEST_5,
                "Quest 5: Two Building Orders",
                "Your reward: Wood 50, Clay 60, Iron 30, Wheat 30"), L0_C0,
                L1_C1, L1_C1, L0_C0));
        // Quest 6: Messages
        // Your reward: 20 Gold
        mId2Mission.put(-QUEST_6, new ReadMessageMission(new QuestData(-QUEST_6,
                "Quest 6: Messages", "Your reward: 20 Gold")));
        // Quest 7: Neighbors
        // Your reward: Wood 60, Clay 30, Iron 40, Wheat 90
        mId2Mission.put(-QUEST_7, new MacroMission(new QuestData(-QUEST_7,
                "Quest 7: Neighbors",
                "Your reward: Wood 60, Clay 30, Iron 40, Wheat 90"),
                new SearchOnTheMapMission(new QuestData(-QUEST_7)),
                new TravianPlusIncreaseProduction(new QuestData(-QUEST_7),
                		config.getBoolean("/productionBonus", true))));		// control in config - default to increase
        // Quest 8: Huge Army
        // Your reward: Huge army (A single rat)
        mId2Mission.put(-QUEST_8, new SimpleMission(new QuestData(-QUEST_8,
                "Quest 8: Huge Army", "Your reward: Huge army (A single rat)"),
                "val", "set"));
        // Quest 9: One each!
        // Your reward: 100 80 40 40
        mId2Mission.put(-QUEST_9, new BuildMine(new QuestData(-QUEST_9,
                "Quest 9: One each!", "Your reward: 100 80 40 40"), L1_C2,
                L1_C2, L1_C2, L1_C2));
        // Quest 10: Coming Soon!
        // Your reward: 2 Days Travian Plus
        mId2Mission.put(-QUEST_10, new MacroMission(new QuestData(-QUEST_10,
                "Quest 10: Coming Soon!", "Your reward: 2 Days Travian Plus"), 
                false /** do not wait if NEVER returned by any of missions */,
                new BuildMine(new QuestData(-QUEST_10, "", ""), L1_C4, L1_C4, L1_C4, L1_C6),
                new IncomingMission(new QuestData(-QUEST_10, "", ""))));
        // Quest 11: Reports
        // Your reward: 75 140 40 230
        mId2Mission.put(-QUEST_11, new ReadReportMission(new QuestData(-QUEST_11,
                "Quest 11: Reports", "Your reward: 75 140 40 230")));
        // Quest 12: Everything to 1!
        // Your reward: 75 80 30 50
        mId2Mission.put(-QUEST_12, new BuildMine(new QuestData(-QUEST_12,
                "Quest 12: Everything to 1!", "Your reward: 75 80 30 50"),
                L1_C4, L1_C4, L1_C4, L1_C6));
        // Quest 13: Dove of Peace
        // Your reward: 120 200 140 100
        mId2Mission.put(-QUEST_13, new VillageNameMission(new QuestData(-QUEST_13,
                "Quest 13: Dove of Peace", "Your reward: 120 200 140 100"),
                Arrays.asList(config.getStringArray("/villageName"))));
        // Quest 14: Cranny
        // Your reward: 150 180 30 130
        mId2Mission.put(-QUEST_14, new BuildBuildingMission(new QuestData(-QUEST_14,
                "Quest 14: Cranny", "Your reward: 150 180 30 130"),
                BuildingType.CRANNY, cfg));
        // Quest 15: To two!
        // Your reward: 60 50 40 30
        mId2Mission.put(-QUEST_15, new BuildMine(new QuestData(-QUEST_15,
                "Quest 15: To two!", "Your reward: 60 50 40 30"), L2_C1, L2_C1,
                L2_C1, L2_C1));
        // Quest 16: Instructions
        // Your reward: 50 30 60 20
        mId2Mission.put(-QUEST_16, new SimpleMission(new QuestData(-QUEST_16,
                "Quest 16: Instructions", "Your reward: 50 30 60 20"), "val",
                "210" /*
                         * wood for barrack (210)
                         */));
        // Quest 17: Main Building
        // Your reward: 75 75 40 40
        mId2Mission.put(-QUEST_17, new UpgradeBuildingMission(new QuestData(-QUEST_17,
                "Quest 17: Main Building", "Your reward: 75 75 40 40"), 26 /*building id*/, 3/* level 3 */));
        // Quest 18: Advanced!
        // Your reward: 100 90 100 60
        mId2Mission.put(-QUEST_18, new RankMission(new QuestData(-QUEST_18,
                "Quest 18: Advanced!", "Your reward: 100 90 100 60")));
        // Quest 19: Weapons or Dough
        mId2Mission.put(-QUEST_19,
                new MacroMission(new QuestData(-QUEST_19,
                        "Quest 19: Weapons or Dough", ""),
                        new SimpleMission(new QuestData(-QUEST_19, "", ""), "val",
                                "1" /* selecting Economy */),
                        new BuildBuildingMission(new QuestData(-QUEST_19, "", ""),
                                BuildingType.GRANARY, cfg)));
        // (E) Quest 20: Warehouse
        // Your reward: 70 120 90 50
        mId2Mission.put(-QUEST_20, new BuildBuildingMission(new QuestData(-QUEST_20,
                "(E) Quest 20: Warehouse", "Your reward: 70 120 90 50"),
                BuildingType.WAREHOUSE, cfg));
        // (E) Quest 21: Marketplace
        // Your reward: 200 200 700 450
        mId2Mission.put(-QUEST_21, new BuildBuildingMission(new QuestData(-QUEST_21,
                "(E) Quest 21: Marketplace", "Your reward: 200 200 700 450"),
                BuildingType.MARKETPLACE, cfg));
        // Quest 22: Everything to 2!
        // Your reward: 15 Gold
        mId2Mission.put(-QUEST_22, new BuildMine(new QuestData(-QUEST_22,
                "Quest 22: Everything to 2!", "Your reward: 15 Gold"), L2_C4,
                L2_C4, L2_C4, L2_C6));
        log.debug("QuestSolver Initialised "+mId2Mission.size()+" missions");
    }

    private Integer analyzeMisson(String page) {
        //Pattern p = Pattern
        //        .compile("<script type=\"text/javascript\">\\s*quest.number = (\\S*);\\s*quest.last = (\\S*);");
        Pattern p = util.getPattern("questSolver.questNumber");
        Matcher m = p.matcher(page);

        if (m.find() && m.groupCount() > 1) {
            String mission = m.group(1);
            log.debug("Current mission: " + mission);
            if (mission.equals("null")) {
                // this is the first mission
                // set it to 0;
                mission = "0";
            }
            try {
            	// positive when completed, negative when mission set
                return Integer.valueOf(mission);
            } catch (NumberFormatException nfe) {
                log.error("Not a number: " + mission);
            }
        } else {
        	// check for alternative to quests
            p = util.getPattern("questSolver.questAlt");
            m = p.matcher(page);
            if (m.find() && (m.groupCount() > 2)) {
            	
                String mission = m.group(2);
                log.debug("Current fetch: "+mission+" missions "+m.group(1)+" of "+m.group(3));
                alternateQuests = true;
                if (mission.equals("null")) {
                    // this is the first mission
                    // set it to 0;
                    mission = "0";
                }
                try {
                	// positive when completed, negative when mission set
                    return Integer.valueOf(mission);
                } catch (NumberFormatException nfe) {
                    log.error("Not a number: " + mission);
                }
            }
        }
        
        return null;
    }

    public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
        	// check missions are setup
    		if (mId2Mission.size() < 1) {
    			// not so fix it here!
    			log.warn("Missions Not Initialised at Startup");
    			initMissions();
    		}

            String page = util.httpGetPage(village.getVillageUrlString());
            Integer level = analyzeMisson(page);
            if (level != null) {
            	// http://serverurl/ajax.php?f=qst&cr=MJQ4
                util.httpPostPage(Mission.getAjaxUrl(this.village, level),
                        new ArrayList<String>(), new ArrayList<String>(), true);
                page = util.httpGetPage(village.getVillageUrlString());
                level = analyzeMisson(page);
            }
            if (level == null) {
                EventLog.log("Disable strategy. Quests are completed (or not recognized).");
                setDeleted(true);
                village.strategyDone.setFinished(getId(), true);
            } else if (alternateQuests) {
                EventLog.log("QuestSolver Alternatve Quest "+level);
                // System.exit(0);
            } else if (level > 0) {
                // here you get the reward for completed missions
                Mission m = mId2Mission.get(-1 * level);
                log.info("Getting Reward for quest: " + level
                        + m.getQuestReward());
                String x = util.httpPostPage(Mission.getAjaxUrl(this.village,
                        level), new ArrayList<String>(),
                        new ArrayList<String>(), true);
                return TimeWhenRunnable.NOW;

            } else if (mId2Mission.containsKey(level)) {
                Mission m = mId2Mission.get(level);
                EventLog.log(m.getQuestTask());
                log.info("available: " + village.getAvailableResources().toString()
                        + ", production: " + village.getProduction());
                return m.solve(this);
            } else {
                log.info("Mission id is unknown: " + level);
            }
            log.error("Should not get here");
            return TimeWhenRunnable.NEVER;
        } finally {
            NDC.pop();
        }
    }

    public boolean modifiesResources() {
        return true;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------
// helper classes
// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

/**
 * Execute more missions sequentially
 */
class MacroMission extends Mission {
    private Mission[] missions;
    private boolean waitFlag;
    /**
     * 
     * @param qd output messages
     * @param wait wait if TimeWhenRunnable.NEVER returned by any missions in the sequence
     * @param m the mission sequence
     */
    public MacroMission(QuestData qd, boolean wait, Mission... m) {
        super(qd);
        missions = m;
        waitFlag = wait;
    }
    public MacroMission(QuestData qd, Mission... m) {
       this(qd, true, m);
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException, InvalidConfigurationException {
        TimeWhenRunnable retTwr = TimeWhenRunnable.NEVER;
        for (Mission m : missions) {
            log.info("Solving Macro part: " +  m.getClass().getName());
            TimeWhenRunnable twr = m.solveMission(strategy);
            log.info("Macro part done: " +  m.getClass().getName() + " " + twr);
            if (twr == TimeWhenRunnable.NOW) {
                log.info("Macro finished with now, getting next one");
                retTwr = twr;
                continue;
            } else if (twr == TimeWhenRunnable.NEVER && !waitFlag) {
                log.info("Macro finished with never, waitflag is false, getting next one");
                retTwr = twr;
                continue;
            } else {
                if (retTwr.after(twr)) {
                    log.info("Setting new twr: " + twr.toString());
                    retTwr = twr;
                } 
            }
        }
        return retTwr;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class TravianPlusIncreaseProduction extends Mission {
	private	boolean	increaseProduction = true;
	
    public TravianPlusIncreaseProduction(QuestData qd, boolean productionBonus) {
        super(qd);
        increaseProduction = productionBonus ;
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
    	// @author gac
    	// make gold production increase controlled by config
    	log.debug("increaseProduction: "+increaseProduction);
    	if (increaseProduction) {
            Village village = strategy.village;
            Util util = strategy.util;
            String url = village.getVillageUrlString();
            String page = util.httpGetPage(url);
            String plusString = "plus.php?id=3";
            util.httpGetPage(Util.getFullUrl(url, plusString));
            String woodPlus = plusString + "&a=1";
            String clayPlus = plusString + "&a=2";
            String ironPlus = plusString + "&a=3";
            String cropPlus = plusString + "&a=4";
            int uid = getUserId(page, util);
            String uidString = "&uid=" + Integer.toString(uid);

            // TODO verify if those links are really available, and check the
            // currently available gold,
            util.httpGetPage(Util.getFullUrl(url, woodPlus + uidString));
            util.httpGetPage(Util.getFullUrl(url, clayPlus + uidString));
            util.httpGetPage(Util.getFullUrl(url, ironPlus + uidString));
            util.httpGetPage(Util.getFullUrl(url, cropPlus + uidString));    		
    	}
        return TimeWhenRunnable.NOW;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class IncomingMission extends Mission {
    private static final Logger log = Logger.getLogger(IncomingMission.class);

    public IncomingMission(QuestData qd) {
        super(qd);
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        Village village = strategy.village;
        Util util = strategy.util;

        String page = util.httpGetPage(village.getVillageUrlString());
        log.trace(page);

        //Pattern p = Pattern.compile(Util.P_FLAGS
        //        + "<div id=\\\"ltbw1\\\">(.*)<span id=timer1>([\\d:]+)</span>");
        Pattern p = util.getPattern("questSolver.reinforcement");
        Matcher m = p.matcher(page);

        if (m.find() && m.groupCount() == 2) {
            String group1 = m.group(1);
            String remainingTime = m.group(2);
            log.info("group1: " + group1 + ", Detected time: " + remainingTime);
            return new TimeWhenRunnable(System.currentTimeMillis()
                    + Util.timeToSeconds(remainingTime) * Util.MILLI_SECOND);
        }
        log.error("Could not detect reinforcement time!");
        EventLog.log("Could not detect reinforcement time!");
        return TimeWhenRunnable.NEVER;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------
class ReadReportMission extends Mission {

    public ReadReportMission(QuestData data) {
        super(data);

    }

    @Override
    protected TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        Village village = strategy.village;
        Util util = strategy.util;
    	ReportMessageReader reader = new ReportMessageReader();
        // EventPage<Report> reports = ReportMessageReader.getInstance().getReportPage(
        EventPage<Report> reports = reader.getReportPage(
                util, village.getVillageUrlString(), ReportType.REINFORCEMENT);
        for (Report report : reports.getEvents()) {
            if (report.hasNewFlag()) {
                report.read(util, village.getVillageUrlString());
            }
        }
        return TimeWhenRunnable.NOW;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------
class UpgradeBuildingMission extends Mission {
    private int buildingId;
    private int level;

    public UpgradeBuildingMission(QuestData qd, int building, int blevel) {
        super(qd);
        buildingId = building;
        level = blevel;
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        if (!strategy.village.buildingQueueAvailable()) {
            return new TimeWhenRunnable(strategy.village
                    .getBuildingQueueAvailableTime());
        }
        Village village = strategy.village;
        Util util = strategy.util;

        UpgradeableSite todo = village.getItem(Integer.toString(buildingId));
        todo.fetch(util);
        if (todo.getCurrentLevel() < level && todo.isSubmittable() && todo.isUpgradeable()) {
         
            return new TimeWhenRunnable(todo.upgrade(util));
        } else {
            EventLog.log("Waiting to " + todo.toString());
            return time2wait(village, util, todo.getNeededResources());
        }
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class BuildBuildingMission extends Mission {
    private BuildingType building;
    private int buildingPosition;
    private static final int RALLYPOINT_ID = 39;
    private static final int WALL_ID = 40;
    

    public BuildBuildingMission(QuestData qd, BuildingType btype, Map<BuildingType, Integer> cfg) {
        super(qd);
        building = btype;
        if (cfg.containsKey(btype)) {
            buildingPosition = cfg.get(btype);
        } else {
            buildingPosition = -1;
        }
        if (btype == BuildingType.RALLY_POINT && buildingPosition != RALLYPOINT_ID) {
            buildingPosition = RALLYPOINT_ID;
        } else if (btype == BuildingType.CITY_WALL && buildingPosition != WALL_ID) {
            buildingPosition = WALL_ID;
        } else if (buildingPosition == RALLYPOINT_ID || buildingPosition == WALL_ID) {
            buildingPosition = -1;
        }
    }

    private EmptySite getRandomEmptySite(BuildingTypeToBuildingMap bttbm) {
        
        List<Building> emptysites = bttbm.get(BuildingType.EMPTYSITE);
        List<Building> ownEmptySites = new ArrayList<Building>();
        for (Building bldg : emptysites) {
            if (!(bldg instanceof RallyPointEmptySite) && !(bldg instanceof WallEmptySite)) {
                ownEmptySites.add(bldg);
            }
        }
        EmptySite random = (EmptySite) ownEmptySites.get((int) (Math.random() * ownEmptySites.size()));
        return random;
    }
    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        if (!strategy.village.buildingQueueAvailable()) {
            return new TimeWhenRunnable(strategy.village
                    .getBuildingQueueAvailableTime());
        }

        Village village = strategy.village;
        Util util = strategy.util;
        BuildingTypeToBuildingMap bttbm = village.getBuildingMap();

        Building newBuilding = bttbm.getOne(building);
        if (newBuilding == null) {
            UpgradeableSite us = village.getItem(Integer.toString(buildingPosition));
            EmptySite emptySite = null;
            if (us instanceof EmptySite) {
                emptySite = (EmptySite) us;
            } else {
                 emptySite = getRandomEmptySite(bttbm);    
            }
            emptySite.setDesiredBuildingType(building);
            emptySite.fetch(util);
            if (emptySite.isUpgradeable() && emptySite.isSubmittable()) {
                Date doneTime = emptySite.upgrade(util);
                log.info("Start building: " + emptySite.toString());
                return new TimeWhenRunnable(doneTime);
            } else {
                EventLog.log("Waiting to " + emptySite.toString());
                return time2wait(village, util, emptySite.getNeededResources());
            }
        }
        return TimeWhenRunnable.NEVER;
    }

}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class SearchOnTheMapMission extends Mission {
    private static final Logger log = Logger
            .getLogger(SearchOnTheMapMission.class);

    public SearchOnTheMapMission(QuestData qd) {
        super(qd);
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        Util util = strategy.util;
        Village village = strategy.village;
        String popup = util.httpPostPage(getAjaxUrl(village, missionId),
                new ArrayList<String>(), new ArrayList<String>(), true);
        //Pattern p = Pattern
        //        .compile("<div class=\\\\\"rew\\\\\">.*<b>(.*)<\\\\/b>");
        Pattern p = util.getPattern("questSolver.villageName");
        Matcher m = p.matcher(popup);
        if (m.find() && m.groupCount() == 1) {
            String villageName = m.group(1);
            String map = util.httpGetPage(Util.getFullUrl(village
                    .getVillageUrlString(), "karte.php"));
            //p = Pattern.compile("title=\\\"" + villageName
            //        + "\\\" href=\\\"karte.php\\?d=(\\S*)&c=");
            p = util.getPattern("questSolver.villageId", villageName);
            m = p.matcher(map);
            if (m.find() && m.groupCount() == 1) {
                String position = m.group(1);
                try {
                    int id = Integer.parseInt(position);
                    String[] postNames = { "x", "y" };
                    IntPair coord = Util.id2coord(id);
                    String[] postValues = { Integer.toString(coord.first),
                            Integer.toString(coord.second) };
                    util
                            .httpPostPage(getAjaxUrl(village, missionId),
                                    Arrays.asList(postNames), Arrays
                                            .asList(postValues), true);
                    return TimeWhenRunnable.NOW;
                } catch (NumberFormatException nfe) {
                    log.error("nfe: " + nfe);
                }
            }
        }
        return TimeWhenRunnable.NEVER;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------
class ReadMessageMission extends Mission {

    public ReadMessageMission(QuestData data) {
        super(data);
    }

    @Override
    protected TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        Village village = strategy.village;
        Util util = strategy.util;
    	ReportMessageReader reader = new ReportMessageReader();
        EventPage<Message> msgs = reader
                .getYellowMessagePage(util, village.getVillageUrlString(),
                        MessageType.INCOMING);
        for (Message msg : msgs.getEvents()) {
            if (msg.hasNewFlag()) {
                msg.read(util, village.getVillageUrlString());
            }
        }
        return TimeWhenRunnable.NOW;
    }
}


// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class RankMission extends Mission {
    private static final Logger log = Logger.getLogger(RankMission.class);

    public RankMission(QuestData qd) {
        super(qd);
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        Village village = strategy.village;
        Util util = strategy.util;

        String page = util.httpGetPage(village.getVillageUrlString());
        int uid = getUserId(page, util);
        String ranks = util.httpGetPage(Util.getFullUrl(village
                .getVillageUrlString(), "statistiken.php"));
        log.trace(ranks);
//        Pattern p = Pattern
//                .compile(Util.P_FLAGS
//                        + "<tr>.*<td.*>(\\S*)\\.\\&nbsp;</td>.*<td.*><a href=\\\"spieler.php\\?uid="
//                        + Integer.toString(uid) + "\\\">");
        Pattern p = util.getPattern("questSolver.rank", Integer.toString(uid));
        Matcher m = p.matcher(ranks);
        List<String> names = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        String url = getAjaxUrl(village, missionId);
        if (m.find() && m.groupCount() == 1) {
            String myRank = m.group(1);
            names.add("val");
            values.add(myRank);
            String x = util.httpPostPage(url, names, values, true);
            return TimeWhenRunnable.NOW;
        }
        return TimeWhenRunnable.NEVER;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------
class VillageNameMission extends Mission {
    private static final Logger log = Logger
            .getLogger(VillageNameMission.class);
    private List<String> names;
    private String villageName;

    public VillageNameMission(QuestData qd, List<String> villageNames) {
        super(qd);
        names = villageNames;
        villageName = names.size() > 0 ? names.get((int) (Math.random() * names
                .size())) : "Hmm";
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        Village village = strategy.village;
        Util util = strategy.util;

        String page = util.httpGetPage(village.getVillageUrlString());
        int uid = getUserId(page, util);
        String uidString = Integer.toString(uid);

        String profilePage = util.httpGetPage(Util.getFullUrl(village
                .getVillageUrlString(), "spieler.php?uid=" + uidString));
        log.trace(profilePage);

        String profilePage2 = util.httpGetPage(Util.getFullUrl(village
                .getVillageUrlString(), "spieler.php?s=1"));
        log.trace(profilePage2);

        String[] postNames = { "e", "uid", "tag", "monat", "jahr", "be1", "mw",
                "ort", "dname", "be2" };
        String[] postValues = { "1", uidString, "", "0", "", "", "0", "",
                villageName, "[#0]" };
        List<String> pNames = new ArrayList<String>(Arrays.asList(postNames));
        List<String> pValues = new ArrayList<String>(Arrays.asList(postValues));
        Util.addButtonCoordinates("s1", 70, 20, pNames, pValues);

        String resultPage = util.httpPostPage(Util.getFullUrl(village
                .getVillageUrlString(), "spieler.php"), pNames, pValues, true);
        log.trace(resultPage);
        return TimeWhenRunnable.NOW;
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class BuildMine extends Mission {
    private static final Logger log = Logger.getLogger(BuildMine.class);
    private Map<ResourceType, IntPair> mExpected = new TreeMap<ResourceType, IntPair>();;

    public BuildMine(QuestData qData, IntPair wood, IntPair clay, IntPair iron,
            IntPair crop) {
        super(qData);

        mExpected.put(ResourceType.WOOD, wood);
        mExpected.put(ResourceType.CLAY, clay);
        mExpected.put(ResourceType.IRON, iron);
        mExpected.put(ResourceType.CROP, crop);
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException, InvalidConfigurationException {
        Village village = strategy.village;
        Util util = strategy.util;
        if (!village.fieldQueueAvailable()) {
            EventLog.log("Time: " + village.getFieldQueueAvailableTime());
            return new TimeWhenRunnable(village.getFieldQueueAvailableTime());
        }
        Map<ResourceType, Integer> current = new TreeMap<ResourceType, Integer>();
        for (ResourceType rt : mExpected.keySet()) {
            current.put(rt, -1 * mExpected.get(rt).second);
        }
        Collection<Field> resources = village.getFields();
        for (Field field : resources) {
            ResourceType rt = field.getResourceType();
            if (field.getCurrentLevel() >= mExpected.get(rt).first) {
                current.put(rt, current.get(rt) + 1);
            }
        }
        // remove those resourcetypes which are not requested to upgrade.
        Iterator<Entry<ResourceType, Integer>>  a = current.entrySet().iterator();
        while (a.hasNext()) {
            if (a.next().getValue() >= 0) {
                a.remove();
            }
        }
        TreeMap<Integer, ResourceType> bestChoice = new TreeMap<Integer, ResourceType>();
        ResourceTypeToFieldMap candidatesPerType = village
        .getLowestFieldPerType();
        List<Pair<Integer, ResourceType>> avail = min(village.getAvailableResources());
        ResourceType candidateType = null;

        for (Pair<Integer, ResourceType> entry : avail) {
            ResourceType key = entry.second;
            if (current.containsKey(key)) {
                candidateType = key;  
                break;
            }
        }

        Field candidate = candidatesPerType.get(candidateType);
        if (candidate == null) {
            log.error("No candidate returning never");
            return TimeWhenRunnable.NEVER;
        }
        EventLog.log("Candidate: " + candidateType.toString() 
                + " " + village.getAvailableResources().toStringNoFood());
        candidate.fetch(util);
        if (candidate.isUpgradeable() && candidate.isSubmittable()) {
            TimeWhenRunnable twr = new TimeWhenRunnable(candidate.upgrade(util));

            return twr;
        } else {
            EventLog.log("Waiting to " + candidate.toString());
            return time2wait(village, util, candidate.getNeededResources());
        }
    }
    private List<Pair<Integer, ResourceType>> min(ResourceTypeMap rtm) {
         List<Pair<Integer, ResourceType>> o = new ArrayList<Pair<Integer, ResourceType>>();
         for (ResourceType rt : rtm.keySet()) {
            o.add(new Pair<Integer, ResourceType>(rtm.get(rt), rt));
        }
         Collections.sort(o, new PairKeyComparator());
         return o;
    }

    private static class PairKeyComparator implements Comparator<Pair<Integer, ResourceType>> {
        public int compare(Pair<Integer, ResourceType> o1,
                Pair<Integer, ResourceType> o2) {
            return o1.first - o2.first;
        }
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

class SimpleMission extends Mission {
    private Map<String, String> postArgs = new TreeMap<String, String>();

    /**
     * Ctor with quest id only.
     * 
     * @param id
     *            the quest id.
     */
    public SimpleMission(QuestData qd) {
        super(qd);
    }

    /**
     * Ctor with quest id and one key-value pair
     * 
     * @param id
     *            the quest id.
     * @param key
     *            the post key.
     * @param value
     *            the post value.
     */
    public SimpleMission(QuestData qd, String key, String value) {
        super(qd);
        postArgs.put(key, value);
    }

    /**
     * Ctor with quest id and one several key-value pairs
     * 
     * @param id
     *            the quest id.
     * @param postNamesValues
     *            the post key-values in a Map.
     * 
     */
    public SimpleMission(QuestData qd, Map<String, String> postNamesValues) {
        super(qd);
        postArgs.putAll(postNamesValues);
    }

    @Override
    public TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException {
        List<String> names = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        for (String key : postArgs.keySet()) {
            names.add(key);
            values.add(postArgs.get(key));
        }
        String url = getAjaxUrl(strategy.village, missionId);
        String result = strategy.util.httpPostPage(url, names, values, true);
        return TimeWhenRunnable.NOW;
    }

}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------

abstract class Mission {
    protected static final Logger log = Logger.getLogger(Mission.class);
    private static final String MISSION_BASE_URL = "/ajax.php?f=qst&cr=";
    private static int MISSION_CHECKSUM_LENGTH = 4;
    private static int LAST_MISSION = 22;
    protected int missionId;
    protected String mQuestTask;
    protected String mQuestReward;

    public Mission(QuestData qData) {
        missionId = qData.id;
        mQuestTask = qData.questTask;
        mQuestReward = qData.questReward;
    }

    public String getQuestTask() {
        return mQuestTask;
    }

    public String getQuestReward() {
        return mQuestReward;
    }

    abstract protected TimeWhenRunnable solveMission(Strategy strategy)
            throws ConversationException, InvalidConfigurationException;

    private TimeWhenRunnable checkCrop(Strategy strategy, int minCrop)
            throws ConversationException, InvalidConfigurationException {
        if (!strategy.village.fieldQueueAvailable()) {
            return new TimeWhenRunnable(strategy.village
                    .getFieldQueueAvailableTime());
        }
        if (strategy.village.getProduction().getCrop() < minCrop) {
            EventLog.log("Hourly crop production is under mincrop(" + minCrop + ")");
            Field cropField = strategy.village.getLowestFieldPerType().get(
                    ResourceType.CROP);
            cropField.fetch(strategy.util);
            if (cropField.isSubmittable() && cropField.isUpgradeable()) {
                return new TimeWhenRunnable(cropField.upgrade(strategy.util));
            } else {
                return time2wait(strategy.village, strategy.util, cropField.getNeededResources());
            }
        }
        return TimeWhenRunnable.NOW;
    }

    public final TimeWhenRunnable solve(Strategy strategy)
            throws ConversationException, InvalidConfigurationException {
        /*
         * hourly crop production shall be at least 5 all the time
         */
        TimeWhenRunnable cropUpgrade = checkCrop(strategy, 5);
        if (cropUpgrade == TimeWhenRunnable.NOW) {
            return solveMission(strategy);
        } else {
            return cropUpgrade;
        }
    }

    // function hg(length,ig){if(length===undefined){length=8;}
    // if(ig===undefined){ig=0.5;}
    // var
    // jg='0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz';var
    // hg='';for(var i=0;i<length;i++){var
    // kg=Math.floor((Math.random()+ig)*0.5*jg.length);hg+=jg.substring(kg,kg+1);}
    // return hg;}
    private static String hg(int length, Double ig) {
        if (length == 0) {
            length = 8;
        }
        if (ig == null) {
            ig = 0.5;
        }
        String jg = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
        String hg = "";
        for (int i = 0; i < length; i++) {
            int kg = (int) Math.floor((Math.random() + ig) * 0.5 * jg.length());
            hg += jg.substring(kg, kg + 1);
        }
        return hg;
    }

    public static String getAjaxUrl(Village village, int level) {
        URL server = null;
        try {
            server = new URL(village.getVillageUrlString());

            server = new URL("http", server.getHost(), "");
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String url = server.toString() + MISSION_BASE_URL;
        if (level == 0) {
            url += hg(MISSION_CHECKSUM_LENGTH, null);
        } else {

            url += hg(MISSION_CHECKSUM_LENGTH, new Double((Math.abs(level) + 1)
                    / (Math.abs(LAST_MISSION) + 1)));
        }
        return url;
    }

    public final int getUserId(String page, Util util) {
        //Pattern p = Pattern.compile("href=\\\"spieler.php\\?uid=(\\S*)\\\"");
        Pattern p = util.getPattern("questSolver.userId");
        Matcher m = p.matcher(page);
        int uid = -1;
        if (m.find() && m.groupCount() == 1) {
            String uidString = m.group(1);
            log.debug("UID : " + uidString);
            try {
                uid = Integer.parseInt(uidString);
            } catch (NumberFormatException nfe) {
                log.error("Number format exception: " + uidString);
            }
        }
        return uid;
    }

    protected TimeWhenRunnable time2wait(Village village, Util util,
            ResourceTypeMap neededResources) {
        ResourceTypeMap availableResources = village.getAvailableResources();
        ResourceTypeMap production = village.getProduction();

        return new TimeWhenRunnable(util.calcWhenAvailable(production,
                availableResources, neededResources));
    }
}

// ---------------------------------------------------------------
// ---------------------------------------------------------------
// ---------------------------------------------------------------
class QuestData {
    public QuestData(int qId) {
        id = qId;
        questTask = "";
        questReward = "";
    }

    public QuestData(int qId, String qTask, String qReward) {
        id = qId;
        questTask = qTask;
        questReward = qReward;
    }

    int id;
    String questTask;
    String questReward;
}
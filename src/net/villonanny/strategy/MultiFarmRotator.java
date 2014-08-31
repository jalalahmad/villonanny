package net.villonanny.strategy;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.ReportMessageReader;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.RallyPoint;
import net.villonanny.entity.Valley;
import net.villonanny.type.BuildingType;
import net.villonanny.type.TroopTransferType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 *
 */
public class MultiFarmRotator extends Strategy {

    private class Target {

        private final TroopTransferType movement;
        private TroopTransferType spy;
        private final String target1, target2;
        private final String village;
        private final int x;
        private final int y;
        private final double rate;

        public Target(String village, int x, int y, String movement, String spy, String target1, String target2) {
            this(village, x, y, movement, spy, target1, target2, 1);
        }

        public Target(String village, int x, int y, String movement, String spy, String target1, String target2, double rate) {
            this.village = village;
            this.x = x;
            this.y = y;
            String fullkey = "movement." + translator.getKeyword(movement, "movement"); // movement.attack
            this.movement = TroopTransferType.fromKey(fullkey);
            if (spy != null) {
                fullkey = "movement." + translator.getKeyword(spy, "movement"); // movement.spy.resources
                this.spy = TroopTransferType.fromKey(fullkey);
            }
            this.target1 = target1;
            this.target2 = target2;
            this.rate = rate;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Target) {
                Target target = (Target) o;
                if (target.x == x && target.y == y) {
                    return true;
                }
                if (village != null && !village.equals("") && village.equals(target.village)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class TargetSearcher {

        final static String WORLD_ANALYZER = "http://www.travian.ws/analyser.pl";
        int userId;
        int radius;
        int minPop, maxPop;
        int maxVillages;
        int daysInactive;
        String serverName;
        String movement;
        List<Target> targets;
        // UserId, VillageId, Population
        Map<Integer, Map<Integer, Integer>> farmList = new HashMap<Integer, Map<Integer, Integer>>();

        public TargetSearcher(String movement, int userId, int radius, int minPop, int maxPop, int maxVillages, int daysInactive, String serverName, List<Target> targets) {
            this.userId = userId;
            this.radius = radius;
            this.minPop = minPop;
            this.maxPop = maxPop;
            this.maxVillages = maxVillages;
            this.daysInactive = daysInactive;
            this.serverName = serverName;
            this.targets = targets;
            this.movement = movement;
            listPlayers();
        }

        private Target findFarm() {
            Target result = null;
            while (result == null && this.farmList.size() > 0) {
                List<String> postNames = new ArrayList<String>(), postValues = new ArrayList<String>();
                Integer userId = null;
                // Take the first user from the list
                for (Integer user : this.farmList.keySet()) {
                    userId = user;
                    break;
                }

                int daysInactive = 10;
                Map<Integer, Integer> farmVillages = this.farmList.remove(userId);
                log.debug("POSSIBLE FARM ID " + userId.toString());
                log.debug("Already found " + farmVillages.size() + " vilages.");

                int farmPop = 0;
                if (farmPopulation.get(userId) != null) {

                    farmVillages = farmPopulation.get(userId);
                    for (Integer population : farmVillages.values()) {
                        farmPop += population;
                    }
                    log.debug("Farm already parsed: " + farmPop + " population.");
                    if (farmPop > this.maxPop) {
                        log.debug(userId.toString() + " have more pop than we are searching.");
                        continue;
                    }
                    if (farmPop < this.minPop) {
                        log.debug(userId.toString() + " have less pop than we are searching.");
                        continue;
                    }
                    if (farmVillages.size() > maxVillages) {
                        log.debug(userId.toString() + " have more villages than we are searching.");
                        continue;
                    }
                } else {
                    // Get the pop from villages in radius
                    for (Integer population : farmVillages.values()) {
                        farmPop += population;
                    }
                    if (farmPop > this.maxPop) {
                        log.debug(userId.toString() + " have more pop than we are searching.");
                        continue;
                    }

                    // Start pop count again
                    farmPop = 0;
                    postNames.add("s");
                    postValues.add(this.serverName);
                    postNames.add("uid");
                    postValues.add(userId.toString());

                    // Load user page
                    String page = "";
                    try {
                        page = util.httpPostPage(WORLD_ANALYZER, postNames, postValues, true);
                    } catch (Exception e) {
                        continue;
                    }
                    postNames.clear();
                    postValues.clear();

                    // 1 - Karte ID
                    // 2 - 7 days ago pop
                    // 3 - 7 days ago growth
                    // 4 - 6 days ago pop
                    // 5 - 6 days ago growth
                    // 6 - 5 days ago pop
                    // 7 - 5 days ago growth
                    // 8 - 4 days ago pop
                    // 9 - 4 days ago growth
                    // 10 - 3 days ago pop
                    // 11 - 3 days ago growth
                    // 12 - 2 days ago pop
                    // 13 - 2 days ago growth
                    // 14 - 1 days ago pop
                    // 15 - 1 days ago growth
                    Pattern p = util.getPattern("multiFarmRotator.userActivity");
                    Matcher m = p.matcher(page);
                    log.debug("Verifying villages and days inactive.");

                    while (m.find()) {
                        int villageInactiveDays = 0;
                        String stringNumber1 = m.group(1);
                        // String stringNumber2 = m.group(2);
                        String stringNumber3 = m.group(3);
                        // String stringNumber4 = m.group(4);
                        String stringNumber5 = m.group(5);
                        // String stringNumber6 = m.group(6);
                        String stringNumber7 = m.group(7);
                        // String stringNumber8 = m.group(8);
                        String stringNumber9 = m.group(9);
                        // String stringNumber10 = m.group(10);
                        String stringNumber11 = m.group(11);
                        // String stringNumber12 = m.group(12);
                        String stringNumber13 = m.group(13);
                        String stringNumber14 = m.group(14);
                        String stringNumber15 = m.group(15);
                        try {
                            int karteId = Integer.parseInt(stringNumber1);
                            int yesterdayPop = Integer.parseInt(stringNumber14);
                            farmVillages.put(karteId, yesterdayPop);
                            log.debug(yesterdayPop + " villagers in village " + karteId);
                            farmPop += yesterdayPop;
                            if ((stringNumber3 != null) && (Integer.parseInt(stringNumber3) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            if ((stringNumber5 != null) && (Integer.parseInt(stringNumber5) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            if ((stringNumber7 != null) && (Integer.parseInt(stringNumber7) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            if ((stringNumber9 != null) && (Integer.parseInt(stringNumber9) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            if ((stringNumber11 != null) && (Integer.parseInt(stringNumber11) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            if ((stringNumber13 != null) && (Integer.parseInt(stringNumber13) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            if ((stringNumber15 != null) && (Integer.parseInt(stringNumber15) <= 0)) {
                                villageInactiveDays++;
                            } else {
                                villageInactiveDays = 0;
                            }
                            log.debug("Grow " + stringNumber15 + " villagers 1 days ago");
                            log.debug("Grow " + stringNumber13 + " villagers 2 days ago");
                            log.debug("Grow " + stringNumber11 + " villagers 3 days ago");
                            log.debug("Grow " + stringNumber9 + " villagers 4 days ago");
                            log.debug("Grow " + stringNumber7 + " villagers 5 days ago");
                            log.debug("Grow " + stringNumber5 + " villagers 6 days ago");
                            log.debug("Grow " + stringNumber3 + " villagers 7 days ago");
                            log.debug("Village inactive for " + villageInactiveDays + " days");
                        } catch (NumberFormatException nfe) {
                        }
                        daysInactive = Math.min(villageInactiveDays, daysInactive);
                    }
                }

                farmPopulation.put(userId, farmVillages);
                log.debug("Villages " + farmVillages.size());
                log.debug("Population " + farmPop);
                log.debug("Days inactive " + daysInactive);

                if ((farmVillages.size() <= this.maxVillages) && (farmPop <= this.maxPop) && (farmPop >= this.minPop) && (daysInactive >= this.daysInactive)) {
                    log.debug("Adding villages within radius to farm list.");
                    for (Integer village : farmVillages.keySet()) {
                        Point myPosition = MultiFarmRotator.this.village.getPosition();
                        Point farmPosition = getCoordFromMapID(village);
                        log.debug("Distance to " + village + " village is " + myPosition.distance(farmPosition));
                        if (myPosition.distance(farmPosition) <= radius) {
                            Target villageTarget = new Target("", farmPosition.x, farmPosition.y, this.movement, null, "", "");
                            // GAC - add dump so can be used in manual routines
                            log.info(String.format("<target movement=\"%s\" x=\"%s\" y=\"%s\" desc=\"%s Pop:%s Dist:%s\" rate=\"%s\"/>",
                                    this.movement, farmPosition.x, farmPosition.y, village, farmPop, myPosition.distance(farmPosition), 1));
                            return villageTarget;
                        }
                    }
                }
            }
            return null;
        }

        private Point getCoordFromMapID(int id) {
            int x, y;
            y = 400 - (id / 801);
            x = (id % 801) - 401;
            return new Point(x, y);
        }

        public List<Target> listFarms(int quantity) {
            List<Target> farmList = new ArrayList<Target>();
            while (!this.farmList.isEmpty()) {
                Target farm = findFarm();
                if (farm != null && !farmList.contains(farm)) {
                    farmList.add(farm);
                }
                if (farmList.size() >= quantity) {
                    log.debug(farmList.size() + " farms found.");
                    return farmList;
                }
            }
            log.debug(farmList.size() + " farms found.");
            return farmList;
        }

        private void listPlayers() {
            if (lastUpdate.before(new Date(System.currentTimeMillis() - 12 * Util.MILLI_HOUR))) {
                log.debug("Reset farm population list");
                farmPopulation.clear();
                lastUpdate = new Date();
            }

            try {
                List<String> postNames = new ArrayList<String>(), postValues = new ArrayList<String>();
                postNames.add("s");
                postValues.add(this.serverName);
                postNames.add("q");
                Point myPosition = village.getPosition();
                postValues.add(myPosition.x + ", " + myPosition.y + ", " + radius);
                postNames.add("sort");
                postValues.add("d");
                // Load page
                String page = util.httpPostPage(WORLD_ANALYZER, postNames, postValues, true); // PageNoLogin(farmPage,
                // true);
                postNames.clear();
                postValues.clear();

                // 1 - User Id
                // 2 - name
                // 3 - Village Id
                // 4 - name
                // 5 - location x
                // 6 - location y
                // 7 - Alliance Id
                // 8 - name
                // 9 - Population
                Pattern p = util.getPattern("multiFarmRotator.userVillagePop");
                Matcher m = p.matcher(page);
                while (m.find()) {
                    String stringNumber1 = m.group(1);
                    String stringNumber2 = m.group(3);
                    String stringNumber3 = m.group(9);

                    // EventLog.log("Found ("+m.group(2)+ ") (" + m.group(4)+ ") (" + m.group(8)+") (" + m.group(9));

                    try {
                        Integer uid = Integer.parseInt(stringNumber1);
                        Integer karteid = Integer.parseInt(stringNumber2);
                        Integer pop = Integer.parseInt(stringNumber3);
                        log.debug("Found a probably farm id " + uid + " village " + karteid + " population " + pop);
                        Map<Integer, Integer> farmPop = this.farmList.get(uid);
                        if (uid == this.userId) {
                            continue;
                        }
                        if (farmPop == null) {
                            Map<Integer, Integer> kartePop = new HashMap<Integer, Integer>();
                            kartePop.put(karteid, pop);
                            this.farmList.put(uid, kartePop);
                        } else {
                            farmPop.put(karteid, pop);
                        }
                    } catch (NumberFormatException nfe) {
                    }
                }
            } catch (Exception e) {
                return;
            }
        }
    }
    private Date lastUpdate = new Date();
    // Holds UserId/VillageId/Population
    private static Map<Integer, Map<Integer, Integer>> farmPopulation = new HashMap<Integer, Map<Integer, Integer>>();
    private final static Logger log = Logger.getLogger(MultiFarmRotator.class);
    private int totErrors = 0;
    private final int MAX_ERRORS = 50;
    private final double RANDOMISE_RATIO = 0.1; // +-10%
    private Random random = new Random();
    private TargetSearcher searcher = null;
    private int searchTargetsQuantity = 10;
    private final Map<Integer, TimeWhenRunnable> attackTimeList = new HashMap<Integer, TimeWhenRunnable>();
    private List<Target> targets = new ArrayList<Target>();
    private int targetListSize = 0;

    public TimeWhenRunnable execute() throws ConversationException {
        // <strategy class="MultiFarmRotator" desc="DESCRIPTION" enabled="BOOLEAN" movement="REINFORCE|RAID|attack" spy="RESOURCES|DEFENSES" attacks="Integer" maxAttacks="Integer" minAttacks="Integer" targetsPerAttack="Integer" minPause="Integer" randomOrder="BOOLEAN">
        // <troops type="TROOP_NAME" allowLess="ALLOWLESS" min="MINIMUM_TROOPS" randomise="RANDOMISE">TROOP_AMOUNT</troops>
        // <target x="XCOORD" y="YCOORD" village="TARGET_VILLAGE" item="CATA_TARGET1, CATA_TARGET2" rate="1"/>
        // <searchTargets radius="Integer" minPop="Integer" maxPop="Integer" maxVillages="Integer" daysInactive="Integer less than 8">
        // 		<excludeTarget x="XCOORD" y="YCOORD" village="EXCLUDE_TARGET_VILLAGE">
        // 		<excludePlayer name="PlayerName" id="PlayerId">
        // 		<excludeAlliance name="AllianceName" id="AllianceId">
        // </searchTargets>
        // </strategy>

        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());

        try {
            int minPauseMinutes = super.config.getInt("/@minPause", 5);
            RallyPoint rallyPoint = (RallyPoint) village.getBuildingMap().getOne(BuildingType.RALLY_POINT);
            if (rallyPoint == null) {
                log.debug("No rally point");
                return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
            }

            int targetsPerAttack = 0;

            int attacks = super.config.getInt("/@attacks", 0);
            int maxAttacks = attacks;
            int minAttacks = attacks;
            if (attacks == 0) {
                maxAttacks = super.config.getInt("/@maxAttacks", 1000);
                minAttacks = super.config.getInt("/@minAttacks", 1);
            }
            boolean randomOrder = super.config.getBoolean("/@randomOrder", true);
            targetsPerAttack = super.config.getInt("/@targetsPerAttack", 0);

            // Get all targets and searched targets and put in a list
            if (targets.isEmpty()) {
                List<SubnodeConfiguration> searchTargetsNodes = super.config.configurationsAt("/searchTargets");
                for (SubnodeConfiguration searchTarget : searchTargetsNodes) {
                    int radius = searchTarget.getInt("/@radius", 14);
                    int minPop = searchTarget.getInt("/@minPop", 0);
                    int maxPop = searchTarget.getInt("/@maxPop", 9);
                    int maxVillages = searchTarget.getInt("/@maxVillages", 1);
                    int daysInactive = searchTarget.getInt("/@daysInactive", 3);
                    String movement = super.config.getString("/@movement", "RAID");

                    String serverName = "";

                    String[] urlString = this.village.getVillageUrlString().replace("http://", "").split("/")[0].split("\\.");

                    String startUrl = urlString[0];
                    String endUrl = urlString[urlString.length - 1];

                    if ((startUrl.indexOf("speed") != -1) && endUrl.equals("se")) {
                        // for swedish speed servers
                        serverName = endUrl + "z";
                    } else if (startUrl.equals("speed1") && endUrl.equals("ae")) {
                        // for ae speed server 1
                        serverName = endUrl + "z";
                    } else if (startUrl.equals("speed2") && endUrl.equals("ae")) {
                        // for ae speed server 2
                        serverName = endUrl + "y";
                    } else if (startUrl.equals("speed") || startUrl.equals("speedserver")) {
                        // for all other speed servers
                        serverName = endUrl + "x";
                    } else if (endUrl.equals("org")) {
                        // for the org server
                        serverName = "org";
                    } else {
                        if ("asia".equals(endUrl)) {
                            // for Thailand server
                            endUrl = "th";
                        }

                        // for all other normal servers
                        // Pattern p = Pattern.compile("(\\d+)");
                        Pattern p = util.getPattern("multiFarmRotator.serverName");
                        Matcher m = p.matcher(startUrl);
                        if (m.find()) {
                            serverName = endUrl + m.group();
                        }
                    }
                    log.debug("Server name is " + serverName);
                    log.debug("Searching in a radius of " + radius);
                    log.debug("Players between " + minPop + " and " + maxPop + " population.");
                    log.debug("With at most " + maxVillages + " villages.");
                    log.debug("Being inactive for " + daysInactive + " days.");

                    Pattern p;
                    Matcher m;
                    String page = village.gotoMainPage();
                    // p = Pattern.compile(Util.P_FLAGS + "<a href=\"spieler[^=]*=(\\d+)\">[^<]*</a>");
                    p = util.getPattern("multiFarmRotator.userId");
                    m = p.matcher(page);
                    int userId = 0;
                    if (m.find()) {
                        userId = Integer.parseInt(m.group(1));
                    } else {
                        util.saveTestPattern("Player uid", p, page);
                        throw new ConversationException("Can't find village uid");
                    }
                    searcher = new TargetSearcher(movement, userId, radius, minPop, maxPop, maxVillages, daysInactive, serverName, targets);
                }

                List<SubnodeConfiguration> targetsNodes = this.config.configurationsAt("/target");
                for (SubnodeConfiguration targetNode : targetsNodes) {
                    String villageName = targetNode.getString("/@village", "");
                    /* Travian Style Coordinates in configuration
                     * @author Czar
                     */
                    int x;
                    int y;
                    String coordsTravianStyle = super.config.getString("/@coords", null);
                    if (null != coordsTravianStyle) { // using travian-style configuration, if exists
                        EventLog.log("Coords travian style...");
                        String[] coords = coordsTravianStyle.split("[(|)]");
                        x = Integer.valueOf(coords[1]);
                        y = Integer.valueOf(coords[2]);
                    } else { // if not reverting to old style
                        x = targetNode.getInt("/@x");
                        y = targetNode.getInt("/@y");
                    }
                    // end of travian-style modification
                    String movement = super.config.getString("/@movement", "attack");

                    String itemString1 = targetNode.getString("/@item[1]", null);
                    String itemString2 = targetNode.getString("/@item[2]", null);
                    String spy = super.config.getString("/@spy", null);
                    double rate = targetNode.getDouble("/@rate", 1);
                    Target target = new Target(villageName, x, y, movement, spy, itemString1, itemString2, rate);
                    if (!targets.contains(target)) {
                        targets.add(target);
                    }
                }

                targetListSize = targets.size();
            }

            if (searcher != null) {
                List<Target> farms = searcher.listFarms(searchTargetsQuantity);
                targets.addAll(farms);
                if (farms.size() < searchTargetsQuantity) {
                    searcher = null;
                }
                targetListSize += farms.size();
            }

            if (targets.size() == 0) {
                totErrors++;
                if (totErrors <= MAX_ERRORS) {
                    EventLog.log("No targets in list yet; retrying later.");
                    return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
                }
                EventLog.log("Strategy could not find any target; disabling.");
                return TimeWhenRunnable.NEVER;
            }
            if (randomOrder) {
                List<Target> randomTargets = new ArrayList<Target>();
                for (Target target : targets) {
                    int pos = (int) (Math.random() * (randomTargets.size() + 1));
                    randomTargets.add(pos, target);
                }
                targets.clear();
                targets.addAll(randomTargets);
            }

            attacks = minAttacks;
            if (targetsPerAttack != 0) {
                attacks = Math.max(minAttacks, Math.min(maxAttacks, targetListSize / targetsPerAttack));
            }

            log.debug(attacks + " multiple attacks ");

            for (int i = attackTimeList.size() - 1; i >= 0; i--) {
                if (attackTimeList.get(i) != null && attackTimeList.get(i).before(new Date())) {
                    attackTimeList.remove(i);
                }
            }

            for (int i = 0; i < attacks; i++) {
                if (attackTimeList.get(i) != null && attackTimeList.get(i).after(new Date())) {
                    continue;
                }

                village.gotoMainPage();

                TroopTypeMap availablePerType = rallyPoint.fetchSendableTroops(super.util, false);
                TroopTypeMap toSend = new TroopTypeMap();
                boolean sendable = true;
                int totTroops = 0;

                List<SubnodeConfiguration> troopsNodes = super.config.configurationsAt("/troops");
                for (SubnodeConfiguration troopsNode : troopsNodes) {
                    boolean enabled = troopsNode.getBoolean("/@enabled", true); // Enabled by default
                    if (!enabled) {
                        continue;
                    }
                    String type = troopsNode.getString("/@type");
                    String fullkey = util.getTranslator().getKeyword(type); // romans.troop1
                    String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
                    TroopType troopType = TroopType.fromString(typeKey);
                    int val = troopsNode.getInt("/");
                    boolean allowLess = troopsNode.getBoolean("/@allowLess", false);
                    Integer available = availablePerType.get(troopType);
                    // Check if we have enough troops
                    if (val > available && !allowLess) {
                        sendable = false;
                        break;
                    }
                    // Check if we can send at least min troops
                    String minimum = troopsNode.getString("/@min", "0");
                    int min = 0;
                    boolean percent = false;
                    if (minimum.endsWith("%")) {
                        percent = true;
                        minimum = minimum.replace("%", "");
                    }
                    try {
                        min = Integer.parseInt(minimum);
                    } catch (NumberFormatException e) {
                        throw new FatalException(String.format("Invalid numeric value for %s: \"%s\"", type, minimum));
                    }
                    if (percent) {
                        min = (val * min) / 100;
                    }
                    if (available < min) {
                        sendable = false;
                        break;
                    }
                    // Randomise
                    // Accept both "randomise" and "randomize", with "true" as default
                    boolean randomise = troopsNode.getBoolean("/@randomise", troopsNode.getBoolean("/@randomize", false));
                    if (randomise) {
                        int maxDelta = (int) (val * RANDOMISE_RATIO);
                        if (!allowLess) {
                            // value can't be less than what specified
                            val = val + random.nextInt(maxDelta + 1);
                        } else {
                            // value can be +- randVal
                            val = val - maxDelta + random.nextInt(2 * maxDelta + 1);
                        }
                    }
                    // Add troops to send
                    val = val > available ? available : val; // Upper limit
                    val = val < min ? min : val; // Lower limit
                    toSend.put(troopType, val);
                    totTroops += val;
                }

                if (sendable == false || totTroops == 0) {
                    TimeWhenRunnable nextRun = TimeWhenRunnable.NEVER;
                    for (TimeWhenRunnable attackTime : attackTimeList.values()) {
                        if (nextRun.after(attackTime)) {
                            nextRun = attackTime;
                        }
                    }
                    TimeWhenRunnable sleepTime = new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE);
                    if (nextRun.after(sleepTime)) {
                        nextRun = sleepTime;
                    }
                    EventLog.log("Not enough troops");
                    return nextRun;
                }
                int secondsToArrive = 0;

                Target target = null;
                boolean notSentTroop = true;

                super.village.gotoMainPage(); // Ensure you do it from the right village (someone might have clicked to a different village meanwhile)
                while (notSentTroop && targets.size() > 0) {
                    notSentTroop = false;
                    target = targets.remove(0);
                    EventLog.log("Selected target position: (" + target.x + "|" + target.y + ")");

                    try {
                        String x = String.valueOf(target.x);
                        String y = String.valueOf(target.y);
                        String village = target.village;
                        TroopTransferType transferType = target.movement;
                        String itemString1 = target.target1;
                        String itemString2 = target.target2;
                        TroopTransferType spyType = target.spy;

                        // log.debug("Average Bounty " + ReportMessageReader.getInstance().getAverageBounty(util, x, y));
                        // EventLog.log("Last Bounty "+ReportMessageReader.getInstance().getLastBounty(util.getServerId(),util.getMapIdFromPosition( x, y)));
                        if ((!x.equals("")) && (!y.equals(""))) {
                        	// get average bounty, returns -1 if never attacked
                        	// if 0 the 
                        	Valley v = new Valley(util, x, y);
                            log.debug("Average Bounty " + v.getAverageBounty());
                        }

                        secondsToArrive = rallyPoint.sendTroops(util, x, y, village, toSend, transferType, new String[]{itemString1, itemString2}, spyType);
                        totErrors = 0;
                    } catch (ConversationException e) {
                        notSentTroop = true;
                        log.error(e);
                        totErrors++;
                        if (totErrors <= MAX_ERRORS) {
                            EventLog.log("Probably the target is banned or in protection; Trying another target.");
                        } else {
                            log.error("Strategy has too many errors; disabling", e);
                            EventLog.log("Strategy has too many errors; disabling");
                            return TimeWhenRunnable.NEVER;
                        }
                    }
                }
                if (target == null || notSentTroop) {
                    totErrors++;
                    log.error("Strategy was not able to send the attack; Trying later.");
                    return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
                }

                // *****bncache start
                double rawRate = target.rate;
                double rate = Math.max(rawRate, Double.MIN_VALUE);
                double raidTime = (2 * secondsToArrive * Util.MILLI_SECOND);
                long waitMillis = (long) (raidTime / rate);
                long timeTemp = waitMillis / 60000;
                EventLog.log(String.format("Strategy %s done for now: Next run in %s minutes at rate %s", getDesc(), Long.toString(timeTemp), Double.toString(rate)));
                TimeWhenRunnable MyTimeToRun = new TimeWhenRunnable(System.currentTimeMillis() + waitMillis);
                attackTimeList.put(i, MyTimeToRun);
                // *****bncache end
            }
            if (searcher != null) {
                TimeWhenRunnable nextRun = TimeWhenRunnable.NEVER;
                for (TimeWhenRunnable attackTime : attackTimeList.values()) {
                    if (nextRun.after(attackTime)) {
                        nextRun = attackTime;
                    }
                }
                TimeWhenRunnable sleepTime = new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE);
                if (nextRun.after(sleepTime)) {
                    nextRun = sleepTime;
                }
                EventLog.log("Sleeping for some minutes to find more targets later.");
                return nextRun;
            }
            TimeWhenRunnable nextRun = TimeWhenRunnable.NEVER;
            for (TimeWhenRunnable attackTime : attackTimeList.values()) {
                if (nextRun.after(attackTime)) {
                    nextRun = attackTime;
                }
            }
            return nextRun;
        } finally {
            NDC.pop();
        }
    }

    public boolean modifiesResources() {
        return false;
    }
}

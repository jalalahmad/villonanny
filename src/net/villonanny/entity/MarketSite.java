package net.villonanny.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.log4j.Logger;

public class MarketSite extends Building {
    private final static Logger log = Logger.getLogger(MarketSite.class);
    private int merchantsFree = -1;
    private int merchantCapacity = -1;
    public final static int RC_NORESOURCES = -1;
    public final static int RC_NOMERCHANTS = -2;
	private String localPage;
	private long pageAge=0;
	boolean postPageInBuffer=false; 

    private Map<TimeWhenRunnable, ResourceTypeMap> returningMerchants;

    public MarketSite(String name, String urlString, Translator translator) {
        super(name, urlString, translator);
    }

	private void setPage(String newPage) {
		this.localPage = newPage;
		this.pageAge=System.currentTimeMillis();
	}
	
	private String getPage(Util util, String urlString) throws ConversationException {
		long now = System.currentTimeMillis();
		long ageSeconds=(now-this.pageAge)/1000;
		if (ageSeconds < 15) { //if page is not older than 15 seconds, use it
			log.debug("MarketSite reuse old page, page age "+(int)(ageSeconds));
		} else {
			log.debug("MarketSite get fresh page");
			this.localPage=util.httpGetPage(urlString);
			this.pageAge=now; //pageAge needs to be set after httpGetPage because of pause in httpGetPage
			postPageInBuffer=false; //we post with util.httpPostPage, not this routine
		}
		return this.localPage;
	}
	
    public ResourceTypeMap getAvailableResources (Util util) throws ConversationException {
    	Pattern p;
    	Matcher m;
    	ResourceTypeMap availableResources=new ResourceTypeMap();
    	String page = this.getPage(util, getUrlString());
		for (ResourceType resourceType : ResourceType.values()) {
			String imageClassOrPath = resourceType.getImageClassOrPath();
			p = util.getPattern("village.availableResources", imageClassOrPath);
			m = p.matcher(page);
			if (m.find()) {
				availableResources.put(resourceType, Integer.parseInt(m.group(1)));
				// log.debug("availableResources " + resourceType.name() + " " + m.group(1) + "/" + m.group(2));
			} else {
				util.saveTestPattern("MarketSite: village.AvailableResources", p, page);
				throw new ConversationException("Can't find resources");
			}
			if (resourceType == ResourceType.FOOD) {
				continue;
			}
		}
		return availableResources;
    }
    public void marketFetch(Util util) throws ConversationException {
        //super.fetch(util); no need after namechange to marketFetch. fofo
        returningMerchants = new HashMap<TimeWhenRunnable, ResourceTypeMap>();

        merchantsFree = -1;  
        //merchantCapacity = -1; //can't set this here, since we're reusing it on the second send in the list. fofo

        Pattern p;
        Matcher m;
        String page = this.getPage(util, getUrlString());

        // p = Pattern.compile("(?s)(?i)<tr><td colspan=\"2\">[^\\d<]+
        // (\\d+)/(\\d+)<br><br></td></tr>");
        p = util.getPattern("marketSite.merchantsFree");
        // 1 - Free
        // 2 - Total
        m = p.matcher(page);
        while (m.find()) {
            String stringNumber = m.group(1).trim();
            try {
                merchantsFree = Integer.parseInt(stringNumber);
            } catch (NumberFormatException nfe) {
                throw new ConversationException(
                        "Error parsing free merchants in " + this.getName());
            }
        }
        if (merchantsFree < 0) {
            String message = EventLog.log("msg.merchantsNotFound", this
                    .getClass());
            util.saveTestPattern("Merchants free", p, page);
            throw new ConversationException(message);
        }

        if (postPageInBuffer==false) {   //merchantCapacity don't show up on the page we get from posting, so reuse old merchantCapacity. fofo
			// p = Pattern.compile(Util.P_FLAGS + "<p>[^<]*<b>(\\d+)</b>[^<]*</p>");
			p = util.getPattern("marketSite.merchantCapacity");
			m = p.matcher(page);
			while (m.find()) {
				String stringNumber = m.group(1).trim();
				try {
					merchantCapacity = Integer.parseInt(stringNumber);
				} catch (NumberFormatException nfe) {
					throw new ConversationException(
							"Error parsing merchant capacity in "
									+ this.getName());
				}
			}
		}
		if (merchantCapacity < 0) {
            String message = EventLog.log("msg.merchantCapacityNotFound", this
                    .getClass());
            util.saveTestPattern("Merchant Capacity", p, page);
            throw new ConversationException(message);
        }

        // Returning merchants
        // 1 - User ID
        // 2 - User Name
        // 3 - Karte ID
        // 4 - Village Name
        // 5 - Arrival time
        // 6 - Time to arrive
        // 7 - Incoming
        // 7 - Wood
        // 8 - Clay
        // 9 - Iron
        // 10 - Crop
        // p = Pattern.compile("(?s)(?i)<td width=\"21%\"><a
        // href=\"spieler\\.php\\?uid=(\\d+)\"><span
        // class=\"c0\">([^<]+)</span></a></td>\\s*<td colspan=\"\\d+\">[^<]+<a
        // href=\"karte\\.php\\?d=(\\d+)\\&c=[^\"]+\"><span
        // class=\"c0\">([^<]+)</span></a></td>\\s*</tr>\\s*<tr><td>[^<]+</td><td><span
        // id=timer\\d+>([\\d:]+)</span>[^<]+</td><td>[^\\d]+([\\d:]+)</td></tr>\\s*<tr
        // class=\"cbg1\"><td>[^<]+</td><td class=\"s7\" colspan=\"2\"><span
        // class=\"c f10\"><img.*src=\"img/un/r/1\\.gif\".*>(\\d+) \\|
        // <img.*src=\"img/un/r/2\\.gif\".*>(\\d+) \\|
        // <img.*src=\"img/un/r/3\\.gif\".*>(\\d+) \\|
        // <img.*src=\"img/un/r/4\\.gif\".*>(\\d+)</td>");
        p = util.getPattern("marketSite.resources");
        m = p.matcher(page);
        while (m.find()) {
            String timeString = m.group(6).trim();
            String stringNumber1 = m.group(7).trim();
            String stringNumber2 = m.group(8).trim();
            String stringNumber3 = m.group(9).trim();
            String stringNumber4 = m.group(10).trim();
            try {
                ResourceTypeMap resources = new ResourceTypeMap();
                resources.put(ResourceType.WOOD, Integer
                        .parseInt(stringNumber1));
                resources.put(ResourceType.CLAY, Integer
                        .parseInt(stringNumber2));
                resources.put(ResourceType.IRON, Integer
                        .parseInt(stringNumber3));
                resources.put(ResourceType.CROP, Integer
                        .parseInt(stringNumber4));
                TimeWhenRunnable arrivalTime = new TimeWhenRunnable(System
                        .currentTimeMillis()
                        + Util.timeToSeconds(timeString) * Util.MILLI_SECOND);
                returningMerchants.put(arrivalTime, resources);
            } catch (NumberFormatException nfe) {
                throw new ConversationException(
                        "Error parsing incoming merchants in " + this.getName());
            }
        }
    }

    public int sendResource(Util util, ResourceTypeMap resources, String x,
            String y, String targetVillage, boolean allowLess, Village village)
            throws ConversationException {
        return sendResource(util, resources, x, y, targetVillage,
                allowLess, village, "", false);
    }

    /**
     * 
     * @param util
     * @param resources
     * @param resourceToKeep
     * @param x
     * @param y
     * @param targetVillage
     * @param allowLess
     * @param village
     * @return the time for merchants to come back, or a negative value for
     *         errors
     * @throws ConversationException
     */
    public int sendResource(Util util, ResourceTypeMap resources,
            String x, String y,
            String targetVillage, boolean allowLess, Village village, 
            String requestedFrom, boolean fullLoadOnly)
            throws ConversationException {
        if (merchantsFree == 0) {
            log.debug("No merchants available");
            return RC_NOMERCHANTS;
        }
        //****** start move to ResourceSender
        // add check of total resources after limit
        resources.put (ResourceType.FOOD, 0); //just in case
		if (resources.getSumOfValues() <= 0) {
            log.debug("No Resources to Send");
            return RC_NORESOURCES;
        }
        
		int neededMerchants = (int) Math.ceil(resources.getSumOfValues() / (double) merchantCapacity);
        int merchantsToSend=0;
        if (fullLoadOnly) {
        	allowLess=true; //need true with fullLoadOnly. fofo
        	merchantsToSend = (int) resources.getSumOfValues() /  merchantCapacity; //round down to nearest neededMerchants. fofo
        }
        else {
        	merchantsToSend=neededMerchants;
        }
        if (merchantsToSend==0) {
            return RC_NORESOURCES;
        }
        log.debug("neededMerchants = " + neededMerchants
                + ", available merchants = " + merchantsFree);
        if (merchantsToSend > merchantsFree) {
        	merchantsToSend=merchantsFree;
        }
        double factorDown=1.0;
		if (resources.getSumOfValues() > merchantsToSend*merchantCapacity){
			factorDown=(double)merchantsToSend * (double)merchantCapacity/(double)resources.getSumOfValues();
		}
		if (neededMerchants > merchantsToSend) { //the old routine didn't remove resources evenly, so I made this. fofo
			if (!allowLess) {
				EventLog.log("Not enough merchants. need "+Integer.toString(neededMerchants)+" got "+Integer.toString(merchantsFree));
				return RC_NOMERCHANTS;
			} 
			else {
				int sumToSend=0;
				log.debug("Sendresources before rounding to merchant "+ resources.toStringNoFood()+" sum is "+ Integer.toString(resources.getSumOfValues()) );
				for (ResourceType aresource : resources.keySet()) {
						int origAmount = resources.get(aresource);
						if (origAmount <= 0 || aresource == ResourceType.FOOD) {
							continue;
						}
						int newAmount = (int)((double)origAmount * factorDown); //since we round down every time, we end up with 1-4 resources too little
						sumToSend = sumToSend+newAmount;                        
						resources.put(aresource, newAmount);
					
				}
				log.debug("Sendresources after rounding to merchant "+ resources.toStringNoFood()+" sum is "+ Integer.toString(resources.getSumOfValues()) );
				for (ResourceType aresource : resources.keySet()) { //add the lost single resources, if any. fofo
					if (aresource == ResourceType.FOOD) {           //I just assume sending 1497 resources will look strange
						continue;
					}
					if (((resources.getSumOfValues()) < merchantsToSend*merchantCapacity) &&
							resources.get(aresource) > 0) {
						resources.put(aresource, resources.get(aresource)+1);
					}
				}			
				log.debug("Sendresources after fixing rounding errors "+ resources.toStringNoFood()+" sum is "+ Integer.toString(resources.getSumOfValues()) );
				
			}
		}

        String page;
        Pattern p;
        Matcher m;
        String destinationVillage = null;
        page = this.getPage(util, getUrlString());
        List<String> postNames = new ArrayList<String>();
        List<String> postValues = new ArrayList<String>();

        // util.addHiddenPostFields(page, "<form method=\"POST\" name=\"snd\"
        // action=\"build.php\">", postNames, postValues);
        util.addHiddenPostFields(page, "marketSite.hiddenPostFields",
                postNames, postValues);
        Util.addButtonCoordinates("s1", 50, 20, postNames, postValues);

        for (ResourceType resource : resources.keySet()) {
            int amount = resources.get(resource);
            postNames.add("r" + (resource.toInt() + 1));
            postValues.add(Integer.toString(amount));
        }

        postNames.add("dname");
        postValues.add(targetVillage == null ? "" : targetVillage);
        postNames.add("x");
        postValues.add(x == null ? "" : x);
        postNames.add("y");
        postValues.add(y == null ? "" : y);

        // First post
        util.shortestPause(false); //the below is too fast
        page = util.httpPostPage(getUrlString(), postNames, postValues, false);

        postNames.clear();
        postValues.clear();

        // util.addHiddenPostFields(page, "<form method=\"POST\" name=\"snd\"
        // action=\"build.php\">", postNames, postValues);
        util.addHiddenPostFields(page, "marketSite.hiddenPostFields",
                postNames, postValues);
        Util.addButtonCoordinates("s1", 50, 20, postNames, postValues);

        for (ResourceType resource : resources.keySet()) {
            if (resource == ResourceType.FOOD) {
                break;
            }
            int amount = resources.get(resource);
            postNames.add("r" + (resource.toInt() + 1));
            postValues.add(Integer.toString(amount));
        }

        // p = Pattern.compile("(?s)(?i)<p class=\"f135\">([^<]*)</p>");
        p = util.getPattern("marketSite.destinationVillage");
        m = p.matcher(page);
        if (m.find()) {
            destinationVillage = m.group(1);
        }
        if (destinationVillage == null) {
            // How come? will be null if m.find=false. fofo
            this.fetch(util);
            log.debug("Available merchants = " + merchantsFree);
            log.debug("Available resources = " + village.getAvailableResources());
            log.debug("Resources to send = " + resources);
            util.saveTestPattern("No village after first post", p, page);
            throw new ConversationException(
                    "Can't send resources: no village after first post");
        }

        EventLog.log("Sending merchants to " + destinationVillage);

        // Trip time
        // p = Pattern.compile("(?s)(?i)</td><td>(\\d+:\\d+:\\d+)</td></tr>");
        p = util.getPattern("marketSite.tripTime");
        m = p.matcher(page);
        String tripTimeString;
        if (m.find()) {
            tripTimeString = m.group(1).trim();
        } else {
            util.saveTestPattern("marketSite.tripTime", p, page);
            throw new ConversationException("Can't find trip time");
        }
        //second post
		page = util.httpPostPage(getUrlString(), postNames, postValues, false);
        this.setPage(page);
        postPageInBuffer=true; //merchantCapacity dont show up after the first post
        if (requestedFrom!="") {
        	ResourceTypeMap logMap = village.strategyDone.getRequestedResources(requestedFrom);
        	if (logMap!=null) {
        		log.debug("Resources ("+logMap.toStringNoFood()+") was requested before send");
        	}
        }
        EventLog.log("Resources (" + resources.toStringNoFood() + ") Sum="+resources.getSumOfValues()+" is sent to "
                + destinationVillage + ", will arrive in " + tripTimeString);
        if (requestedFrom!="") {
            village.strategyDone.updateRequestedResources(requestedFrom, resources);
            
        	ResourceTypeMap logMap = village.strategyDone.getRequestedResources(requestedFrom);
        	if (logMap!=null) {
        		EventLog.log("Resources ("+logMap.toStringNoFood()+") is requested after send");
        	} else {
        		EventLog.log("Request served fully");
        	}
        }
        return Util.timeToSeconds(tripTimeString);
    }
    
    public int getMerchantsFree() {
        return merchantsFree;
    }

    public TimeWhenRunnable getWhenNextMerchantFree() {
        TimeWhenRunnable nextMerchantFree = TimeWhenRunnable.NEVER;
        for (TimeWhenRunnable freeMerchant : returningMerchants.keySet()) {
            if (nextMerchantFree.after(freeMerchant)) {
                nextMerchantFree = freeMerchant;
            }
        }
        return nextMerchantFree;
    }

    /**
     * Gives back the available gold count. This method makes a http request to
     * the travian plus page. TODO this method could be placed into Server.java
     * instead of this class
     * I would suggest making a new strategy or/and entity or/and net for gold use. fofo
     * @param util
     *            used to make http request
     * @return the available gold
     * @throws ConversationException -
     */
    public int getGoldCount(Util util) throws ConversationException {
        String plusPage = "plus.php?id=3";
        String page = util.httpGetPage(Util
                .getFullUrl(getUrlString(), plusPage));
        // String p_end = "</p>";
        // String b_begin = "<b>";
        // String b_end = "</b>";
        // Pattern p = Pattern.compile(Util.P_FLAGS
        // + "<p class=\"txt_menue\"(.*?)" + p_end + "(.*?)" + b_begin
        // + "(\\d+)" + b_end);
        Pattern p = util.getPattern("marketSite.gold");
        Matcher m = p.matcher(page);
        if (m.find() && m.groupCount() == 3) {
            int gold = Integer.parseInt(m.group(3));
            return gold;
        }
        log.info("No golds are available");
        return 0;
    }

    /**
     * Trading with the NPC merchant.
     * 
     * @param util
     *            needed for accessing httpGetPage(...)
     * @param condition
     *            a user defined condition for npc trade and resource
     *            distribution.
     * @throws ConversationException
     */
    public void npcTrade(Util util, Condition condition)
            throws ConversationException {
        int gold = getGoldCount(util);
        final int minGold = 3;
        if (gold < minGold) {
            throw new ConversationException(
                    "NPC trade is not possible. Not enough gold (" + gold + "<"
                            + minGold + ")");
        }
        String url = getUrlString();
        String npcSuffix = "&t=3";
        String npcPage = util.httpGetPage(url + npcSuffix);
        int wood = getValueOfSpan(npcPage, "org0", util);
        int clay = getValueOfSpan(npcPage, "org1", util);
        int iron = getValueOfSpan(npcPage, "org2", util);
        int crop = getValueOfSpan(npcPage, "org3", util);
        ResourceTypeMap available = new ResourceTypeMap(wood, clay, iron, crop,
                -1);
        if (condition == null || !condition.accept(available)) {
            log.error("The condition is not acceptable: " + condition);
            return;
        }
        ResourceTypeMap expected = condition.getExpectedResources(available);
        if (expected.getSumOfValues() - expected.getFood() != available
                .getSumOfValues()
                - available.getFood()) {
            throw new ConversationException(
                    "Expected does not match to available"
                            + expected.toStringNoFood() + " "
                            + available.toStringNoFood());
        }
        List<String> postNames = new ArrayList<String>();
        List<String> postValues = new ArrayList<String>();
        addPostField(postNames, postValues, "id", url.substring(url
                .indexOf("id=") + 3));
        addPostField(postNames, postValues, "t", "3");
        addPostField(postNames, postValues, "a", "6");
        addPostField(postNames, postValues, "m2[]", expected.getWood());
        addPostField(postNames, postValues, "m1[]", wood);
        addPostField(postNames, postValues, "m2[]", expected.getClay());
        addPostField(postNames, postValues, "m1[]", clay);
        addPostField(postNames, postValues, "m2[]", expected.getIron());
        addPostField(postNames, postValues, "m1[]", iron);
        addPostField(postNames, postValues, "m2[]", expected.getCrop());
        addPostField(postNames, postValues, "m1[]", crop);
        util.httpPostPage(url + npcSuffix, postNames, postValues, true);
        EventLog.log("NPC trade " + available.toStringNoFood() + " => "
                + expected.toStringNoFood());
    }

    public void sellResources(Util util, SellConfig sconf) {
        String message = sconf.toString();
        EventLog.log(message);
        String url = getUrlString();
        String sellSuffix = "&t=2";
        String offerPage = null;
        try {
            offerPage = util.httpGetPage(url + sellSuffix);
        } catch (ConversationException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        List<String> postNames = new ArrayList<String>();
        List<String> postValues = new ArrayList<String>();
        addPostField(postNames, postValues, "m1", sconf.sellQuantity);
        addPostField(postNames, postValues, "m2", sconf.lookQuantity);
        addPostField(postNames, postValues, "rid1", sconf.sellType
                .getImageClassOrPath());
        addPostField(postNames, postValues, "rid2", sconf.lookType
                .getImageClassOrPath());
        addPostField(postNames, postValues, "d1", sconf.hour > 0 ? "1" : "0"); // hour
        // limit
        // flag
        addPostField(postNames, postValues, "d2", sconf.hour > 0 ? Integer
                .toString(sconf.hour) : "2"); // hour value
        addPostField(postNames, postValues, "ally", sconf.clan ? "1" : "0"); // clan
        Util.addButtonCoordinates("s1", 80, 20, postNames, postValues);
        try {
            util.addHiddenPostFields(offerPage,
                    "marketSite.sellHiddenPostFields", postNames, postValues);
        } catch (ConversationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // GAC - this seems to use all merchants but caller in Offer implements count look!
        /*
        for (int i = 0; i < getMerchantsFree(); ++i) {
            try {
                util.httpPostPage(url + sellSuffix, postNames, postValues,
                        false);
            } catch (ConversationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        */
        // just try once
        try {
            util.httpPostPage(url + sellSuffix, postNames, postValues,
                    false);
        } catch (ConversationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void addPostField(List<String> postNames, List<String> postValues,
            String name, int value) {
        addPostField(postNames, postValues, name, Integer.toString(value));
    }

    private void addPostField(List<String> postNames, List<String> postValues,
            String name, String value) {
        postNames.add(name);
        postValues.add(value);
    }

    private int getValueOfSpan(String page, String spanName, Util util) {
        // Pattern p = Pattern.compile("<span id=\"" + spanName
        // + "\">(\\S+)</span>");
        Pattern p = util.getPattern("marketSite.valueOfSpan", spanName);
        Matcher m = p.matcher(page);
        int value = -1;
        if (m.find() && m.groupCount() == 1) {
            value = Integer.parseInt(m.group(1));
        }
        return value;
    }

    // -----------------------------------------------------------------------------
    // HELPER CLASSES
    // -----------------------------------------------------------------------------

    // -----------------------------------------------------------------------------
    // Condition
    // -----------------------------------------------------------------------------
    /**
     * A condition, users of this class shall implement the
     * {@link #accept(ResourceTypeMap)} and
     * {@link #getExpectedResources(ResourceTypeMap)} methods.
     * 
     * 
     * @author biminus
     * 
     */
    public static abstract class Condition {
        /**
         * The accept method shall return true if the sum of the available
         * resources are fine for the user to make an NPC trade.
         * 
         * @param available
         * @return true if the NPC trade is allowed.
         */
        abstract boolean accept(ResourceTypeMap available);

        /**
         * Returns the wished resource distribution after an NPC trade.
         * 
         * @param available
         *            the currently available resources.
         * @return the expected resources
         */
        abstract ResourceTypeMap getExpectedResources(ResourceTypeMap available);
    }

    // -----------------------------------------------------------------------------
    // BalancedCondition
    // -----------------------------------------------------------------------------
    /**
     * A Balanced condition, distributes the resources equally ~(sum/4) Accept
     * when any of the resources are greater than the specified upper limit.
     * 
     * @author biminus
     * 
     */
    public static class BalancedCondition extends Condition {
        private int upperLimit;
        /**
         * default upper limit if any resource reaches this limit the NPC trade
         * is allowed.
         */
        public static final int DEFAULT_UPPER_LIMIT = 800;

        /** Ctor with default (value 800) upper resource limit */
        public BalancedCondition() {
            this(DEFAULT_UPPER_LIMIT);
        }

        /**
         * Ctor with user specified upper limit.
         * 
         * @param upper
         *            accept shall return true independently of which resource
         *            reaches it. The resources distributed equally ~(sum/4).
         */
        public BalancedCondition(int upper) {
            upperLimit = upper;
        }

        public boolean accept(ResourceTypeMap available) {
            return (available.getWood() > upperLimit
                    || available.getClay() > upperLimit
                    || available.getIron() > upperLimit || available.getFood() > upperLimit);
        }

        @Override
        public ResourceTypeMap getExpectedResources(ResourceTypeMap available) {
            int sum = available.getSumOfValues() - available.getFood();
            int fourth = sum / 4;
            return new ResourceTypeMap(fourth, fourth, fourth,
                    fourth + sum % 4, 0);
        }
    }

    // -----------------------------------------------------------------------------
    // ExpectedCondition
    // -----------------------------------------------------------------------------

    /**
     * Expected condition, distributes the resources according to the expected
     * amount Accept when there are enough resources to make the NPC trade.
     * 
     * @author biminus
     * 
     */
    public static class ExpectedCondition extends Condition {
        private ResourceTypeMap expected;

        /**
         * Ctor with the expected resource distribution.
         * 
         * @param expectedResources
         *            expected resource distribution
         */
        public ExpectedCondition(ResourceTypeMap expectedResources) {
            expected = expectedResources;
        }

        /**
         * Ctor with the expected resource distribution.
         * 
         * @param wood -
         * @param clay -
         * @param iron -
         * @param crop -
         */
        public ExpectedCondition(int wood, int clay, int iron, int crop) {
            this(new ResourceTypeMap(wood, clay, iron, crop, 0));
        }

        public boolean accept(ResourceTypeMap available) {
            int expectedSum = expected.getSumOfValues() - expected.getFood();
            int availableSum = available.getSumOfValues() - available.getFood();
            if (availableSum < expectedSum) {
                return false;
            } else {
                // check each resources
                if (available.getWood() >= expected.getWood()
                        && available.getClay() >= expected.getClay()
                        && available.getIron() >= expected.getIron()
                        && available.getCrop() >= expected.getCrop()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ResourceTypeMap getExpectedResources(ResourceTypeMap available) {
            if (!accept(available)) {
                return null;
            }
            int availableSum = available.getSumOfValues() - available.getFood();
            int rest = availableSum - expected.getSumOfValues()
                    + expected.getFood();
            int restFourth = rest / 4;
            return new ResourceTypeMap(expected.getWood() + restFourth,
                    expected.getClay() + restFourth, expected.getIron()
                            + restFourth, expected.getCrop() + restFourth
                            + rest % 4, 0);
        }
    }

    public static class SellConfig {
        public SellConfig(ResourceType sellType, int sellQuantity,
                ResourceType lookType, int lookQuantity, int hour, boolean clan) {
            this.sellType = sellType;
            this.sellQuantity = sellQuantity;
            this.lookType = lookType;
            this.lookQuantity = lookQuantity;
            this.hour = hour;
            this.clan = clan;
        }

        private ResourceType sellType;
        private ResourceType lookType;
        private boolean clan;
        private int hour;
        private int sellQuantity;
        private int lookQuantity;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Offering ").append(sellQuantity).append(" ").append(
                    sellType).append(" for ").append(lookQuantity).append(" ")
                    .append(lookType).append(" in ").append(hour).append(
                            " hour(s) distance for ").append(
                            clan ? "ownclan." : "anyone.");
            return sb.toString();
        }
    }

    public int getMerchantCapacity() {
        return merchantCapacity;
    }
    /**
     * Returns how many merchants are required for a given amount of resources.
     * @param resources
     * @return merchants count
     */
    public int merchantsForResources(int resources) {
        return resources / merchantCapacity + 1;
    }
}

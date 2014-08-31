
package net.villonanny;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.entity.Field;
import net.villonanny.entity.Server;
import net.villonanny.entity.Village;
import net.villonanny.entity.Valley;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
/**
 * A class which can handle/read ingame messages and reports.
 * @author biminus, gac
 *
 */
public class ReportMessageReader {

    private static final String MESSAGE_SUFFIX = "nachrichten.php";
    private static final String MESSAGE_SCAN = "titles";
    private static final String REPORT_SUFFIX = "berichte.php";
    // private static final String REPORT_FILE = "ReportMessageReader";
    private static final String DEFENDER_TAG = "defender";
    private static final String RESOURCE_TAG = "resources";
    private static final String TROOP_TAG = "troops";
    private static final String INFO_TAG = "info";
    private static final Integer		PAGES_PER_TIME = 3;
    private static final Integer		REPORTS_PER_PAGE = 10;
    // as this is a factory class that exists once all variables are shared!
    // lastRead is therefore not per instance - need to refactor
    private String	storedUrl = null;	// url to use in send/read
    private Util		storedUtil = null;		// for local access
//    private static Util		storedUserName = null;		// for local access
	private int	lastRead = 0;		// number read last time run
	private int	lastMessage = 0;		// number read last time run
    private String storeReports = null;		// store in persistent store only set reports to false
    private String checkMessages = null;		// control how to read and handle
    private String messageCommands = null;		// control if checking for commands in messages or message titles
    private String deleteReports = null;		// store if configured to delete as well as read
    private String deleteName = null;			// store for post information
    private String deleteValue = null;			// store for post information
    private List<String> commandName = new ArrayList<String>();
    private List<String> commandText = new ArrayList<String>();
    private List<String> msgTitle = new ArrayList<String>();
    private List<String> msgText = new ArrayList<String>();
    private List<Boolean> msgSent = new ArrayList<Boolean>();
    private int	noCommands = 0;						// count of how many commands in list

    protected final static Logger log = Logger.getLogger(ReportMessageReader.class);
    // private static ReportMessageReader mInstance = new ReportMessageReader();;

    public ReportMessageReader() {
    }

    /**
     * Gives back the sole instance.
     * 
     * @return the EventReader instance
    public static ReportMessageReader getInstance() {
        return mInstance;
    }
     */

    private static final String[]	matchType = {
    	"all", "attack", "attack", "attack", "attack", "attack", "attack", "attack",
    	"rein", "rein", "rein",
    	"trade", "trade", "trade", "trade",
    	"scout", "scout", "scout", "scout", "scout"		// no separate type - html id = attacker
    	}; 
    
    /**
     * check mode for valid report filters
     * @param reportMode
     * @return true if reportmode contains a report filter
     */
    public static boolean filterMode(String reportMode) {
    	// check for any filter in mode
    	// log.trace("filterMode:"+reportMode);
    	// search all configured reports
    	for (String mType : matchType) {
        	// log.trace("matchType="+mType);
    		if (reportMode.contains(mType)) {
        		// found
            	// log.trace("filterMode:"+reportMode+", found matchType="+mType);
            	return true;
    		}
    	}
    	return false;
    }
    /**
     * check mode for valid report filters
     * @param reportMode
     * @return first report filter found, if string contains all will be 0, -1 if none found
     */
    public static int getFilterBase(String reportMode) {
    	// check for any filter in mode
    	log.trace("mode="+reportMode);
    	int index = 0;
    	// search all configured reports
    	for (String mType : matchType) {
        	log.trace(index+" matchType="+mType);
    		if (reportMode.contains(mType)) {
        		// found
            	EventLog.log("FilterBase mode="+reportMode+", matchType="+mType+", index="+index);
            	return index;
    		}
    		index++;
    	}
    	return -1;
    }
    /**
     * check mode for valid report filters
     * @param filter name
     * @param mode string to search
     * @return first report filter found, if string contains all will be 0, -1 if none found
     */
    public static boolean isFilter(String filter, String reportMode) {
    	// check for any filter in mode
    	if (reportMode.contains(filter)) {
            return true;
    	}
    	return false;
    }
    
    /**
     * Checks for any outstanding reports.
     * 
     * @param util
     *            http request needs
     * @param serverUrl
     *            the base url http://speed.travian.com/
     * @return boolean true if more unread reports, false if all read
     * @throws ConversationException
     */
    public Boolean newReports(Util util, String serverUrl, String reportMode) throws ConversationException {
    	// store util and login string
    	storedUtil = util;
    	storedUrl = serverUrl;
    	// super.getClass().
    	// uses mN.gif rather than any direct indicator in html to show messages waiting
    	// javascript selects based on class
    	// <div id="n5" class="i3">
    	// div.i1{background-image:url(un/l/m1.gif);}div.i2{background-image:url(un/l/m2.gif);}
    	// div.i3{background-image:url(un/l/m3.gif);}div.i4{background-image:url(un/l/m4.gif);}
    	// m3 report m2=i2 message m1 both m4=i4 neither
/*
  		<div id="n5" class="i3">
			<a href="berichte.php" accesskey="5"><img src="img/x.gif" class="l" title="Reports" alt="Reports"/></a>
   	
 */
    	// village.gotoMainPage()
        // first get report type
    	// String page = village.gotoMainPage();
    	String	page =util.httpGetPage(serverUrl);
		Pattern p = util.getPattern("reportMessageReader.status");
		Matcher m = p.matcher(page);
		if (m.find()) {
			String mType = m.group(1);
			log.trace(mType+" storeReports "+reportMode);
			// check for class that indicates messages waiting indicator or test mode to read all
			if (mType.equals("i3") || mType.equals("i1") || ((reportMode != null) && reportMode.contains("all"))){
				return true;
			}
		} else {
			log.warn("Cannot find Report Status");			
		}
		// default to none - does not generate pattern error
    	return false;
    }

    /**
     * sets mode for handling ingame reports starting with the next one.
     * 
     * @param report mode
     */
    public void setReportsMode(String mode, SubnodeConfiguration config) {
        storeReports = mode ;
		// store if any delete flag
		deleteReports = config.getString("/delete/@type", "");
		log.debug("storeReports:"+storeReports+", deleteReports:"+deleteReports);
		log.debug("Adding Events for items");
		setCommands(config);

        // force all mode
        // storeReports += "all" ;
    }

  
    
 
    
    /**
     * Returns a group of the ingame reports starting with the newest one.
     * 
     * @param util
     *            http request needs
     * @param serverUrl
     *            the base url http://speed.travian.com/
     * @param category
     *            the report category
     * @return group of reports
     * @throws ConversationException
     */
    public EventPage<Report> getReportPage(Util util, String serverUrl,
            ReportType category) throws ConversationException {
        return getReportPage(util, serverUrl, category, 0);
    }

    /**
     * Returns a group of ingame reports starting with the <code>offset</code>
     * report.
     * 
     * @param util
     *            http request needs
     * @param serverUrl
     *            base url http://speed.travian.com/
     * @param category
     *            report category
     * @param offset
     *            the report offset
     * @return a group of reports
     * @throws ConversationException
     */
    public EventPage<Report> getReportPage(Util util, String serverUrl,
            ReportType category, int offset) throws ConversationException {
    	
        String reportForm = getForm(util, Util.getFullUrl(serverUrl, category
                .urlSuffix())
                + getOffsetUrl(offset), ReportMessageReader.REPORT_SUFFIX);
        String[] reportRows = reportForm.split("</tr>");
        EventPage<Report> group = new EventPage<Report>(offset);

        for (String report : reportRows) {
            Report reportObj = Report.createReport(util, report);
            if (reportObj != null) {
                group.add(reportObj);
            } else {
            	// check for post information
                String	pattern = "reportMessageReader.button";
    			Pattern p = util.getPattern(pattern, REPORT_SUFFIX);
                Matcher m = p.matcher(report);
                if (m.find() && (m.groupCount() == 2)) {
    				// String dInfo = "";
    	            // for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
    	            // EventLog.log(pattern+":"+dInfo);
                	deleteName = m.group(1);
                	deleteValue = m.group(2);
                } else {
                	if (report.contains("button")) {
                		log.error(pattern+" Button found but not matched:"+report);
                	}
                }
            }
        }
        // System.exit(0);
        return group;
    }

    private String getOffsetUrl(int offset) {
    	// gac - add suffix that is on url if using selector
        return (offset <= 0) ? "" : "?s=" + offset + "&o=0";
    }

    private String getForm(Util util, String url, String formAction)
            throws ConversationException {
    	// store util and login string if getReportPage or getYellowMessagePage called from QuestSolver
    	// also used to return to reports page to emulate back button when each report is read
    	storedUtil = util;
    	storedUrl = url;
    	log.debug("Storing Form Page to emulate Back:"+storedUrl);
    	
        String events = util.httpGetPage(url);
//        Pattern p = Pattern.compile(Util.P_FLAGS + "<form(.*?)action=\""
//                + formAction + "\"(.*?)</form>");
        Pattern p = util.getPattern("reportMessageReader.form", formAction);
        Matcher m = p.matcher(events);
        if (m.find() && (m.groupCount() == 2)) {
            // log.trace("reportMessageReader.form count="+m.groupCount()+","+m.group(1)+","+m.group(2));
            return m.group(2);
        }
        return "";
    }

    /**
     * 
     * Returns the ingame (yellow) messages starting from the newest message.
     * 
     * @param util
     *            needed because of the http request
     * @param serverUrl
     *            the root url eg.: http://speed.travian.com/
     * @param category
     *            the message category
     * @return group of messages
     * @throws ConversationException
     */
    public EventPage<Message> getYellowMessagePage(Util util,
            String serverUrl, MessageType category)
            throws ConversationException {
        return getYellowMessagePage(util, serverUrl, category, 0);
    }

    /**
     * Returns the ingame (yellow) messages starting from <code>offset</code>
     * message position.
     * 
     * @param util
     *            needed because of the http request
     * @param serverUrl
     *            the root url eg.: http://speed.travian.com/
     * @param category
     *            the message category
     * @param offset
     *            the message offset
     * @return a group of messages.
     * @throws ConversationException
     */
    public EventPage<Message> getYellowMessagePage(Util util,
            String serverUrl, MessageType category, int offset)
            throws ConversationException {

    	String form = getForm(util, Util.getFullUrl(serverUrl, category
                .urlSuffix())
                + getOffsetUrl(offset), ReportMessageReader.MESSAGE_SUFFIX);
        String[] messageRows = form.split("</tr>");
        EventPage<Message> group = new EventPage<Message>(offset);
        for (String row : messageRows) {
            Message msg = Message.createMessage(util, row);
            if (msg != null) {
                group.add(msg);
                // check message for instructions
            	if ((messageCommands != null) && (messageCommands != "false")) {
                	// handle message - needs to read it and clear the flag so not handled more than once 
                    checkCommands(msg);
                }
            }
        }
        return group;
    }

    // ------------------------------------------------------------------
    // HELPER CLASSES
    // ------------------------------------------------------------------
    /**
     * This class is designed to store the Messages and Report
     * headers.
     */
    public static class EventPage<T> {
        /** One page contains 10 messages or reports.*/
        private static final int DEFAULT_EVENTS_ON_PAGE = 10;
        /** container for messages and reports.*/
        private List<T> events = new ArrayList<T>();
        /** page offset.*/
        private int offset;

        public EventPage(int iOffset) {
            if (iOffset < 0) {
                offset = 0;
            } else {
                offset = iOffset;
            }
        }

        public List<T> getEvents() {
            return Collections.unmodifiableList(events);
        }

        private void add(T eventObj) {
            events.add(eventObj);
        }
        
        public int previousPageId() {
            if (offset < DEFAULT_EVENTS_ON_PAGE) {
                return 0;
            } else {
                return offset - DEFAULT_EVENTS_ON_PAGE;
            }  
        }
        
        public int nextPageId() {
            if (hasNextPage()) {
                return offset + DEFAULT_EVENTS_ON_PAGE;
            } else {
                return offset;
            }
             
        }
        
        public boolean hasNextPage() {
            return events.size() - DEFAULT_EVENTS_ON_PAGE == 0;
        }
 
        public int getOffset() {
            return offset;
        }
    }

    // ------------------------------------------------------------------
    // REPORT
    // ------------------------------------------------------------------
    /**
     * This class represents a player
     * 
     * TODO move to own entity class but need to decide if Players have list of Valleys or just a Valley has a Player in whic case is a village
     */
    private static class Player {
        private int			playerId;		// uid of attacker or trade sender
        private String		playerName;		// name   	
        private String		playerRole;		// role attacker/defender/sender - if generic, ally/nap/confed/enemy
    }
    /**
     * This class represents a basic Village, an inhabited Valley 
     * should it be a village class inherited from valley and then owned village
     * 
     * TODO move to own entity class?
     */
    private static class AnyVillage {
        private int			villageMapRef;				// id of valley
        private String		villageName;			// name
        // add point and any other references
        private Player		owner;			// optional info on owning player if occupied
    }
    
    /**
     * This class represents an ingame report. The report content is only stored 
     * for specific report modes
     * the page can be loaded with the {@link #read(Util, String)} method.
     * 
     * @author biminus
     * 
     */
    public static class Report {
        /** the identification number of the report.*/
        private int reportId;	
        // June 2010 travian changed format from simple decimal to decimal|hex
        private String reportRef;
        private String reportType;			// iReport type
        private int    reportIndex;			// iReport number
        /** the title of the report.*/
        private String reportTitle;
        /** the read flag true=> unread, false => read.*/
        private boolean reportFlag;
        /** the report event time.*/
        private String reportTime;
        // checkbox info to delete
        private String checkboxName;
        private String checkboxValue;
        // additional fields to store content
        private boolean		reportStored;	// flag to indicate if contents set
        private String		rType;			// type of report attacker/defender/reinforcement/trade
        private String		serverId;		// identifier for server name@loginurl
        private int			playerId;		// identifier of player from spielerid
        // private AnyVillage	sender;			// player info
        // private AnyVillage	defender;		// optional defender info for attack reports
        private Valley		sender;			// player info
        private Valley		defender;		// optional defender info for attack reports
    	private	String		resourceInfo;		// type of resouce info - from report
    	private ResourceTypeMap resourceValues;	// trade amount or bounty
    	private	String		troopNames;		// troop names from report max 10 * 2
    	private String		troopCounts;	// csv list of troops max 10 * 5
    	private String		reportInfo;		// any text info - used for scout defences
    	private Map<String, TroopTypeMap> troopValues;	// binary list of troops

        /**
         * Ctor.
         * 
         * @param id
         *            the report identifier
         * @param title
         *            report title
         * @param flag
         *            new report flag
         * @param time
         *            report time
         */
        public Report(int id, String ref, String type, String title, boolean flag, String time, String dName, String dValue) {
            reportId = id;
            reportType = type;
            // set index from iReport type
            reportIndex = Integer.parseInt(type.replaceAll("\\D", ""));
            reportRef = ref;
            reportTitle = title;
            reportFlag = flag;
            reportTime = time;
            // store checkbox info for delete
            checkboxName = dName;
            checkboxValue = dValue;
            
            // mark no content to start
            reportStored = false;
        }

        /**
         * Loads the report page and returns.
         * 
         * @param util - required for pattern
         * @param url
         *            server base url
         * @return
         * @throws ConversationException
         */
        public String read(Util util, String url) throws ConversationException {
        	return read(util, url, null);
        }
        
        /**
         * Loads the report page and returns.
         * 
         * @param util - required for pattern
         * @param url 	server base url
         * @param mode  mode if want to decode and store
         * 
         * @return
         * @throws ConversationException
         */
        public String read(Util util, String url, String reportMode) throws ConversationException {
        	log.debug("read report "+this.reportId);
            // String page = util.httpGetPage(Util.getFullUrl(url, REPORT_SUFFIX + "?id=" + reportRef));
    		// check for codes - are being used in some return urls
            String urlString = Util.getFullUrl(url, REPORT_SUFFIX + "?id=" + reportRef);
    		if (urlString.contains("|")) {
    			log.trace("Replacing | in "+urlString);
    			urlString = urlString.replaceAll("\\|", "%7C");
    			// EventLog.log("Report url:"+urlString);
    		}
            String page = util.httpGetPage(urlString);
            // check if processing reports
            if (reportMode != null) {
            	// check if already stored - not here, when doing gets or as public getReport that returns Report
            	// if (findReport(reportId) == null) {
            		// dont need to process again 
            	// }
            	// need to think about error handling if not expecting to process report in read
            	String rType = setType(util, page);
            	// check got some data
            	if (rType != null ) {
            		serverId = util.getServerId();
        			// EventLog.log("Report "+serverId+" type="+rType);
                	// get this player info from page - reuse village pattern
        			String pattern = "village.villageUid";
        			Pattern p = util.getPattern(pattern);
        			Matcher m = p.matcher(page);
        			if (m.find()) {
        				setPlayerId(Integer.parseInt(m.group(1)));
        			} else {
        				log.warn(reportId+" ("+rType+") cannot find "+pattern);
        				util.saveTestPattern(pattern, p, page);
        	            return page;
        			}
            		// store any player info - try and use generic format but some need special decoding
            		if (rType.equals("trade")) {
            			setTradeInfo(util, page);
            		} else if (rType.equals("reinforcement")) {
            			// reinforcement
                		setPlayers(util, page);
                		// check for troops
                		setTroopCounts(util, page);
                		// check if any information - includes crop use
    	    			p = util.getPattern("reportMessageReader.info");
        				m = p.matcher(page);
    	    			if (m.find()) {
    						// trim any white space
    	    				reportInfo = m.group(1).trim()+","+m.group(2).trim();
    	    			}
            		} else {
            			// attack
            			// boolean	scout = false;
                		setPlayers(util, page);
                		// get any info
                		/*
            			Valley valley = new Valley(util, defender.villageMapRef);
            			valley.setName(defender.villageName);
            			valley.setPlayer(defender.owner.playerName);
            			*/
                		// check for troops
                		setTroopCounts(util, page);
                		// check for resources
    	    			p = util.getPattern("reportMessageReader.resources");
        				m = p.matcher(page);
    	    			if (m.find()) {
    	    				setResources(util, page, m);
    	    			} else {
                    		// check for scout resources                		
        	    			p = util.getPattern("reportMessageReader.scoutresources");
            				m = p.matcher(page);
        	    			if (m.find()) {
        	    				// scout = true;
        	    				setResources(util, page, m);
        	    			} else {
        	    				// no error if neither as may be scouting for info, or non returned
        	    			}
    	    			}
                		// check if any scout information, also gets info from cat attack
    	    			p = util.getPattern("reportMessageReader.info");
        				m = p.matcher(page);
    	    			if (m.find()) {
    						// trim any white space
    	    				reportInfo = m.group(1).trim()+","+m.group(2).trim();
    	    			}
    	    			// log.debug("Info "+reportInfo);
    	    			// default assumption is this village is attacker
	    				// valley.addBounty(getBounty());
    	    			if (sender != null) {
    	    				sender.addLoss(getBounty());    	    				
    	    			}
	    				// check if defender set - not if attack and none returned
	    				if (defender != null) {
		    				defender.addBounty(getBounty());	    					
	    				}
	    				// processReport here or where called?
            		}
            	}
    			// mark stored, do before output as tests it but also so wont repeat if errors
    			reportStored = true;
            		
            	// write to persistent store
    			// valley.save();
    			if (sender != null) {
        			if (defender != null) {
        				// update first & save both to file
            			sender.store();    				
            			defender.save();    				
        			} else {
        				// update and save
            			sender.save();    				
        			}
    			} else if (defender != null) {
    				// is this possible?
    				// update and save
        			defender.save();    				
    			}
    			// back to top report page
                util.httpGetPage(url);
            }
            reportFlag = false;
            return page;
        }

        /**
         * extract report type
         * 
         * @param util - required for pattern
         * @param page
         * @return
         * @throws ConversationException
         */
        private String setType(Util util, String page)  throws ConversationException 
        {
            // first get report type
            String	pattern = "reportMessageReader.content";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			if (m.find()) {
				/* 
				String dInfo = "";
	            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            log.debug(pattern+": "+dInfo);
				*/
				rType = m.group(1);
				return rType;
			} else {
				log.warn(reportId+" ("+rType+") cannot find "+pattern);
				util.saveTestPattern(pattern, p, page);
			}
        	return null;
        }
        
        /**
         * extract information from trade report
         * 
         * @param util
         * @param page
         * @return true if all ok
         * @throws ConversationException
         */
        private boolean setTradeInfo(Util util, String page)  throws ConversationException 
        {
        	// look for player info in report
			String pattern = "reportMessageReader.trade";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			if (m.find()) {
				/*
				String dInfo = "";
	            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            log.debug(pattern+": "+dInfo);
	            */
	            // check how much info
	            if (m.groupCount() > 4 ) {
	            	/*
		            sender = new AnyVillage();
		            sender.owner = new Player();
	            	sender.owner.playerRole = m.group(1);
	            	sender.owner.playerId = Integer.parseInt(m.group(2));
	            	sender.owner.playerName = m.group(3);
	            	sender.villageMapRef = Integer.parseInt(m.group(4));
	            	sender.villageName = m.group(5);
	            	*/
	            	sender = new Valley(util, Integer.parseInt(m.group(4)));
	            	sender.setOwner(Integer.parseInt(m.group(2)), m.group(3));
	            	sender.setOwnerRole(m.group(1));
	            	sender.setName(m.group(5));	            	
	            }
	            if (m.groupCount() > 5 ) {
	            	resourceInfo = m.group(6);
					resourceValues = new ResourceTypeMap();
	            	// resourceValues.put(ResourceType.WOOD, Integer.parseInt(m.group(2)));
		            for (int i = 7 ; i < 11 ; i++ ) {
		            	// set resource note starts at 0
		            	resourceValues.put(ResourceType.fromInt(i-7), Integer.parseInt(m.group(i)));
		            }	            	
	            }
			} else {
				log.warn(reportId+" ("+rType+") cannot find "+pattern);
				util.saveTestPattern(pattern, p, page);
	        	return false;
			}
            return true;			
        }
        
        /**
         * extract player information from report
         * 
         * @param util - required for pattern
         * @param page
         * @return true if ok, false if no player information found
         * @throws ConversationException
         */
        private boolean setPlayers(Util util, String page)  throws ConversationException 
        {
        	// look for player info in report
        	// maximum of two - reinforcements do not show village info
			String pattern = "reportMessageReader.players";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			if (m.find()) {
				// EventLog.log("Player 1 "+m.start()+" end "+m.end());
				/* String dInfo = "";
	            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            log.debug(pattern+": "+dInfo); */
	            // check how much info
	            if (m.groupCount() > 4 ) {
	            	/*
		            sender = new AnyVillage();
		            sender.owner = new Player();
	            	sender.owner.playerRole = m.group(1);
	            	sender.owner.playerId = Integer.parseInt(m.group(2));
	            	sender.owner.playerName = m.group(3);
	            	sender.villageMapRef = Integer.parseInt(m.group(4));
	            	sender.villageName = m.group(5);
	            	*/
	            	sender = new Valley(util, Integer.parseInt(m.group(4)));
	            	sender.setOwner(Integer.parseInt(m.group(2)), m.group(3));
	            	sender.setOwnerRole(m.group(1));
	            	sender.setName(m.group(5));	            	
	            } 
			} else {
				log.warn(reportId+" ("+rType+") cannot find "+pattern);
				util.saveTestPattern(pattern, p, page);
	        	return false;
			}
			// check for defender
			if (m.find()) {
				// EventLog.log("Player 2 "+m.start()+" end "+m.end());
				/* String dInfo = "";
	            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            log.debug(pattern+": "+dInfo); */
	            // check how much info
	            if (m.groupCount() > 4 ) {
	            	/* defender = new AnyVillage();
	            	defender.owner = new Player();
	            	defender.owner.playerRole = m.group(1);
	            	defender.owner.playerId = Integer.parseInt(m.group(2));
	            	defender.owner.playerName = m.group(3);
	            	defender.villageMapRef = Integer.parseInt(m.group(4));
	            	defender.villageName = m.group(5); */
	            	defender = new Valley(util, Integer.parseInt(m.group(4)));
	            	defender.setOwner(Integer.parseInt(m.group(2)), m.group(3));
	            	defender.setOwnerRole(m.group(1));
	            	defender.setName(m.group(5));	            	
	            }
			}
            return true;			
        }
        
        /**
         * extract resource information from report that has already had find run
         * 
         * @param page
         * @return true if ok, false if no player information found
         * @throws ConversationException
         */
        private boolean setResources(Util util, String page, Matcher m)  throws ConversationException 
        {
			resourceValues = new ResourceTypeMap();
            // check how much info
            if (m.groupCount() > 4 ) {
				// EventLog.log("Resources "+m.start()+" end "+m.end());
            	resourceInfo = m.group(1);
            	// resourceValues.put(ResourceType.WOOD, Integer.parseInt(m.group(2)));
	            for (int i = 2 ; i <= m.groupCount() ; i++ ) {
	            	// set resource note starts at 0
	            	resourceValues.put(ResourceType.fromInt(i-2), Integer.parseInt(m.group(i)));
	            }
	            return true;
            } 
        	return false;
        }
        /**
         * extract resource information from report
         * 
         * @param page
         * @return true if ok, false if no player information found
         * @throws ConversationException
         */
        private boolean setResources(Util util, String page)  throws ConversationException 
        {
        	// look for player info
			String pattern = "reportMessageReader.resources";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			if (m.find()) {
				resourceValues = new ResourceTypeMap();
				setResources(util, page, m);
				/*
	            // check how much info
	            if (m.groupCount() > 4 ) {
	            	resourceInfo = m.group(1);
	            	// resourceValues.put(ResourceType.WOOD, Integer.parseInt(m.group(2)));
		            for (int i = 2 ; i <= m.groupCount() ; i++ ) {
		            	// set resource note starts at 0
		            	resourceValues.put(ResourceType.fromInt(i-2), Integer.parseInt(m.group(i)));
		            }
	            } */
	            return true;
			} else {
				log.warn(reportId+" ("+rType+") cannot find "+pattern);
				util.saveTestPattern(pattern, p, page);
			}
        	return false;
        }
        /**
         * extract player information from report
         * 
         * @param page
         * @return true if ok, false if no player information found
         * @throws ConversationException
         */
        private boolean setTroopCounts(Util util, String page)  throws ConversationException 
        {
        	// look for troop headers
        	// Reinforcement seems to be in order Roman,Gaul,Teuton with id tagged against defender tribe type
        	/*
			String patternH = "reportMessageReader.troopNames";
			Pattern pH = util.getPattern(patternH);
			Matcher mH = pH.matcher(page);
    		// initialise string
    		// troopCounts = "";
    		// troopValues = new HashMap<String, TroopTypeMap>();
    		int countH = 0;
			while (mH.find()) {
				// role for each block - match to sender/defender allocate header
				// a sender must be this tribe
				EventLog.log(countH+" region start "+mH.start()+" end "+mH.end()+ " role "+mH.group(1));
				// String dInfo = "";
	            // for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            // log.debug(pattern+": "+dInfo);
	            // EventLog.log(dInfo);
				// troopCounts = troopCounts.concat(","+m.group(1));
				// troopValues.put(m.group(1), item);
				countH++;
	        	// look for troop info
				String pattern = "reportMessageReader.troopName";
				Pattern p = util.getPattern(pattern);
				Matcher m = p.matcher(page);
				m.region(mH.start(), mH.end());
	    		// initialise string
	    		// troopCounts = "";
	    		// troopValues = new HashMap<String, TroopTypeMap>();
	    		int count = 0;
				while (m.find()) {
					String dInfo = "";
		            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
		            // log.debug(pattern+": "+dInfo);
		            EventLog.log(dInfo);
					// EventLog.log(count+" troops region start "+m.start()+" end "+m.end());
					String troopName = m.group(2).trim();
					// String fullkey = util.getTranslator().getKeyword(troopName); // romans.troop1
					// String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
					int typeIndex = Integer.parseInt(m.group(1)) - 10;
					String typeKey = "troop"+typeIndex;
					TroopType troopType = TroopType.fromString(typeKey);
					// EventLog.log("TroopName "+troopName+","+fullkey+","+typeKey+","+troopType);
					EventLog.log("TroopName "+troopName+","+typeKey+","+troopType);
					count++;
				}
			}
			*/
        	// look for troop info
			String pattern = "reportMessageReader.troops";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
    		// initialise string
    		troopCounts = "";
    		troopValues = new HashMap<String, TroopTypeMap>();
    		int count = 0;
			while (m.find()) {
				// EventLog.log(count+" troops region start "+m.start()+" end "+m.end());
				// String dInfo = "";
	            // for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            // log.debug(pattern+": "+dInfo);
	            // EventLog.log(dInfo);
				// troopCounts = troopCounts.concat(","+m.group(1));
				troopCounts = troopCounts.concat(","+m.group(1)); 
				TroopTypeMap item = new TroopTypeMap();
	            for (Integer i = 2 ; i <= m.groupCount() ; i++ ) {
	            	// EventLog.log(i+"=\""+m.group(i)+"\"");
    				// check for null or optional hero
    				// java 1.6 if (m.group(i).isEmpty()) {
    				if (m.group(i).length()==0) {
        				troopCounts = troopCounts.concat(",0");
        				item.put(TroopType.fromInt(i-2), 0);
    				} else if(m.group(i).contains("<td")) {
    					String s = util.stripTags(m.group(i));
    					// EventLog.log("<td stripped string value "+s);
            			troopCounts = troopCounts.concat(",hero:"+s);					    					
        				item.put(TroopType.fromInt(i-2), Integer.valueOf(s));
    				} else {
        				troopCounts = troopCounts.concat(","+m.group(i));    					
        				// set troop, note starts at 0
        				item.put(TroopType.fromInt(i-2), Integer.valueOf(m.group(i)));
    				}
	            }
	            // store info - does this need to be concatenated with Attacker/Defender?
				// troopValues.put(m.group(1), item);
				count++;
				troopValues.put(Integer.toString(count), item);
			}
			// System.exit(0);
        	return troopCounts != "" ? true : false ;
        }
        
        
        /**
         * Factory method creates a Report object from a table row on the Report
         * page (http://speed.travian.com/berichte.php).
         * 
         * @param tr
         *            a table row
         * @return the object if successfully created or null.
         */
        private static Report createReport(Util util, String tr) {
        	/*
            Pattern p2 = Pattern
                    .compile(Util.P_FLAGS
                            + "<a href=\\\""
                            + REPORT_SUFFIX
                            + "\\?id=(\\d+)\\\">(.*?)</a>(.*?)</td>.*?<td[^>]*?>(.*?)</td>");
                       		*/
            String	pattern = "reportMessageReader.reports";
			Pattern p2 = util.getPattern(pattern, REPORT_SUFFIX);
            Matcher m2 = p2.matcher(tr);
            /* if (m2.find() && (m2.groupCount() == 4)) {
            	// EventLog.log("CreateReport:"+m2.group(1)+" status ("+m2.group(3)+")");
            	int	split = m2.group(1).indexOf("|");
            	int id = 0;
            	if (split > 0) {
            		id = Integer.parseInt(m2.group(1).substring(0,split));
            	} else {
            		id = Integer.parseInt(m2.group(1));
            	}
                return new Report(id, m2.group(1), m2.group(2),
                        m2.group(3).trim().length() != 0, m2.group(4));
            }
            */
            if (m2.find() && (m2.groupCount() == 7)) {
				// String dInfo = "";
	            // for (int i = 0 ; i++ < m2.groupCount() ; ) { dInfo = dInfo.concat(","+m2.group(i)); }
	            // EventLog.log(pattern+":"+dInfo);
            	int	split = m2.group(4).indexOf("|");
            	int id = 0;
            	if (split > 0) {
            		id = Integer.parseInt(m2.group(4).substring(0,split));
            	} else {
            		id = Integer.parseInt(m2.group(4));
            	}
            	String dString = m2.group(7);
            	String tString = Util.getLocalMessage("iReport.today", ReportMessageReader.class);
            	if (dString.contains(tString)) {
    	            // log.debug("Convert Date:"+dString);
    	            SimpleDateFormat dFormat = new SimpleDateFormat(Util.getLocalMessage("iReport.dFormat", ReportMessageReader.class));
    				String nowDateString = dFormat.format(new Date());
    	            dString = dString.replaceAll(tString, nowDateString);
    	            // log.debug("Converted Date:"+dString);
    	            // System.exit(0);
            	}
                return new Report(id, m2.group(4), m2.group(3), m2.group(5),
                        m2.group(6).trim().length() != 0, dString, m2.group(1), m2.group(2));
            } else {
                // log.trace("reportMessageReader.reports not found count="+m2.groupCount()+"tr="+tr);
            }
            // dont error on report list as may be called often
            return null;
        }

        public String toString() {
            return "(id=" + reportId + ", time=" + reportTime + ", newMsg="
                    + reportFlag + ", title=" + reportTitle + ")";
        }

        /**
         * Return all report information in selected format, default csv.
         * currently only supports return csv format 
         * 
         * @return report info
         */
        public String toString(String format) {
        	// Util.this.
    		String output;
    		// log.debug("format "+format+" reportStored "+reportStored+" sender "+sender);
        	if ((format != null) && reportStored) {
        		// header info - depends on mode for how much to include
        		if (format.contains("csv")) {
            		output = serverId + "," + playerId + "," + reportId + "," + reportTime + ","+ reportFlag + "," + reportTitle + "," + rType;
        		} else {
            		output = reportTime + ","+ reportTitle + "," + rType;        			
        		}
        		// always check valid player
				if(sender != null) {
					/* output = output.concat("," + sender.owner.playerRole);
					output = output.concat("," + sender.owner.playerName);
	        		if (format.equals("csv")) {
	    				output = output.concat("," + sender.owner.playerId);
	    				output = output.concat("," + sender.villageMapRef);
				    }
					output = output.concat(",\"" + sender.villageName + "\""); */
					output = output.concat("," + sender.getOwnerRole());
					output = output.concat("," + sender.getOwnerName());
	        		if (format.contains("csv")) {
	    				output = output.concat("," + sender.getOwnerId());
	    				output = output.concat("," + sender.getId());
				    }
					output = output.concat(",\"" + sender.getName() + "\"");
				}
				if(defender != null) {
	        		/* if (format.equals("csv")) {
	        			// mark with indicator present
	    				output = output.concat(",<"+DEFENDER_TAG+">," + defender.owner.playerId);
	    				output = output.concat("," + defender.villageMapRef);
				    }
					output = output.concat("," + defender.owner.playerRole);
					output = output.concat("," + defender.owner.playerName);					
					output = output.concat(",\"" + defender.villageName + "\""); */
	        		if (format.contains("csv")) {
	        			// mark with indicator present
	    				output = output.concat(",<"+DEFENDER_TAG+">," + defender.getOwnerId());
	    				output = output.concat("," + defender.getId());
				    }
					output = output.concat("," + defender.getOwnerRole());
					output = output.concat("," + defender.getOwnerName());					
					output = output.concat(",\"" + defender.getName() + "\"");
				}
				if (resourceInfo != null) {
	        		if (format.contains("csv")) {
						output = output.concat(",<"+RESOURCE_TAG+">," + resourceInfo );
						// store separate
			            for (int i = 0 ; i < 4 ; i++ ) {
			            	// get resource note starts at 0
							output = output.concat("," + resourceValues.get(ResourceType.fromInt(i)));
			            }
				    } else {
				    	// check can spot own id String gained = sender.owner.playerId == playerId ? "My " : "Their ";
						output = output.concat(","  + resourceInfo + "," + resourceValues.getSumOfValues() + "," + resourceValues.toStringNoFood() );			    	
				    }
				}
				if (troopCounts != null) {
	        		if (format.contains("csv")) {
						output = output.concat(",<"+TROOP_TAG+">");					
				    }
					// already has leading ,
					output = output.concat(troopCounts);					
				}
				if (reportInfo != null) {
	        		if (format.contains("csv")) {
						output = output.concat(",<"+INFO_TAG+">");					
				    }
	        		// remove any embedded format tags such as bold text
					output = output.concat(",\"" + reportInfo.replaceAll("\\<.*?>","").trim() + "\"");
				}
				if (reportType != null) {
	            	String	iReport = Util.getLocalMessage("iReport."+reportType, ReportMessageReader.class);
					output = output.concat(",\"" + iReport + "\"");
	            	// log.debug("iReport:"+iReport);
				}
        	} else {
        		output = reportId +","+ reportTime +","+ reportFlag +","+ reportTitle;
        	}
            return output;
        }
        
        public boolean matchesMode(String reportMode) {
        	// check iReport number against type
        	log.trace("id="+reportId+", iReport="+reportIndex);
        	// check index is valid
        	if ((reportIndex > 0) &&(reportIndex < 20)) {
        		// check if any filters
        		/*
            	if (reportMode.contains("attack") ||
                	reportMode.contains("rein") ||
                	reportMode.contains("trade") ||
                	reportMode.contains("scout") ) {
                */
        		if (ReportMessageReader.filterMode(reportMode)) {
            		// check one we want
            		if (!reportMode.contains(ReportMessageReader.matchType[reportIndex])) {
                    	log.trace("filtering id="+reportId+", iReport="+reportIndex+", no match="+matchType[reportIndex]+", mode="+reportMode);
                    	return false;
            		}
                	log.debug("filtering id="+reportId+", iReport="+reportIndex+", match="+matchType[reportIndex]+", mode="+reportMode);
                }
        	}
        	// default is ok
        	return true;
        }
        
        public boolean hasNewFlag() {
           return reportFlag;
        }
        
        /**
         * Return Identity of Report
         * 
         * @return integer unique Id number
         */
        public int getId() {
        	return reportId;
        }
        
        /**
		 * @param playerId the playerId to set
		 */
		public void setPlayerId(int playerId) {
			this.playerId = playerId;
		}

		/**
		 * @return the playerId
		 */
		public int getPlayerId() {
			return playerId;
		}

		/**
		 * @return the defender
		 */
		public Valley getDefender() {
			return defender;
		}
		/**
		 * @return the sender
		 */
		public Valley getSender() {
			return sender;
		}
		
		/**
		 * @return the total resources from report
		 */
		public	int	getBounty() {
        	if (resourceValues != null) {
            	return resourceValues.getSumOfValues();        		
        	}
        	return 0;
        }
        
        public	int	getSumOfTroops() {
            int total = 0;
        	if (troopValues != null) {
                for (TroopTypeMap troops : troopValues.values()) {
                    // troopvalues().get;
                    total += troops.getSumOfValues();
                }
        	}
        	return total;
        }
        
        public	int	getSumOfTroops(String tType) {
        	if ((troopValues != null) && (troopValues.get(tType) != null)) {
                return troopValues.get(tType).getSumOfValues();
        	}
        	return 0;
        }
        public	TroopTypeMap	getTroops(String tType) {
        	if (troopValues != null) {
                return troopValues.get(tType);
        	}
        	return null;
        }
        
        public	String getInfo() {
        	String result = "";
			if (reportInfo != null) {
				result = reportInfo;
			}
			return result;
        }

		/**
		 * @param reportTitle the reportTitle to set
		 */
		public void setReportTitle(String reportTitle) {
			this.reportTitle = reportTitle;
		}

		/**
		 * @return the reportTitle
		 */
		public String getReportTitle() {
			return reportTitle;
		}

		/**
		 * @param reportTime the reportTime to set
		 */
		public void setReportTime(String reportTime) {
			this.reportTime = reportTime;
		}

		/**
		 * @return the reportTime
		 */
		public String getReportTime() {
			return reportTime;
		}

    }

    public enum ReportType {
        /** All report type.*/
        ALL(REPORT_SUFFIX),
        /** Trade report type.*/
        TRADE(REPORT_SUFFIX + "?t=2"),
        /** Reinforcement report type.*/
        REINFORCEMENT(REPORT_SUFFIX + "?t=1"),
        /** Attack report type.*/
        ATTACK(REPORT_SUFFIX + "?t=3"),
        /** Other report type.*/
        OTHER(REPORT_SUFFIX + "?t=4");

        private String urlSuffix;

        private ReportType(String suffix) {
            urlSuffix = suffix;
        }

        public String urlSuffix() {
            return urlSuffix;
        }
    }
    
	public	boolean	processReport(Report report) {
		boolean result = false;
		// check report type
		if (report.rType.equals("trade")) {
		} else if (report.rType.equals("reinforcement")) {
			// reinforcement
		} else {
			// attack - check to send messages
	    	// ReportMessageReader reader = ReportMessageReader.getInstance();
	    	// ReportMessageReader reader = new ReportMessageReader();
			ReportMessageReader reader = this;
			
			// check message mode - send a message if attacked???
			// but how often - once per run, once per day?
			// get persistant valley - update total no attacks, total bounty, time of last report
			// is it worth storing offence & defence troop nos max ever and last
			int eventNo = -1;
			int	troopNos = 0;
			// if (playerId == defender.owner.playerId) {
			if ((report.getDefender() != null) && (report.getPlayerId() == report.getDefender().getOwnerId())) {
				// attack on us
				troopNos = report.getSumOfTroops("1");
				if (report.getSumOfTroops("4") != 0) {
					// we lost some troops
					eventNo = reader.findEvent("attacked");
					troopNos = report.getSumOfTroops("4");
				} else if (report.getBounty() != 0) {
					eventNo = reader.findEvent("farmed");
				} else {
					eventNo = reader.findEvent("dodged");
				}
				// set no troops used
				report.getSender().setTroops(report.getSumOfTroops("1"));
			} else if ((report.getSender() != null) && (report.getPlayerId() == report.getSender().getOwnerId())) {
				// our attack - check for casualty
				troopNos = report.getSumOfTroops("2");
				if (report.getDefender() == null) {
					// total loss - use separate type?
					eventNo = reader.findEvent("casualty");
				} else if (report.getSumOfTroops("2") != 0) {
					eventNo = reader.findEvent("casualty");
				} else if (report.getBounty() != 0) {
					eventNo = reader.findEvent("bounty");
				} else {
					eventNo = reader.findEvent("empty");
				}
			} else {
				// no sender and no defender or odd combintions
				// for example no defender is used in ceasefire as attack on us
				log.debug("Sender "+report.getSender()+" Defender "+report.getDefender());
			}
			log.trace(report.getReportTime()+" Event "+eventNo);
			// check if any message to send
			if (eventNo != -1) {
				reader.notifyEvent(eventNo, report.getReportTime(), report.getReportTitle(), Integer.toString(report.getBounty()), Integer.toString(troopNos), report.getInfo());
			}
		}		
		return result;
	}
    /**
     * read and display reports according to specified mode, start for specified point
     * 
     * @param util		required for http read
     * @param url		server/village url
     * @param reportMode	controls format
     * @param lastRead		counter for last report read latest is 0	
     * @return				update lastRead
     */
    public	int		readReports(Util util, String url, String reportMode, int readOffset) {
        try {
		    int	noReports = 0;
		    int noRead = 0;
        	// ReportMessageReader reader = ReportMessageReader.getInstance();
			ReportMessageReader reader = this;
        	// open file if needed for output
        	PrintWriter out = null;
			// if (reportMode.equals("csv") || reportMode.equals("txt")) {
        	// allow multiple modes csv,log
			try {
				if (reportMode.contains("csv")) {
					out = new PrintWriter(new FileOutputStream(new File("logs" + File.separator + "Reports.csv"), true));
				}
				else if (reportMode.contains("txt")) {
					out = new PrintWriter(new FileOutputStream(new File("logs" + File.separator + "Reports.txt"), true));
				}
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				reportMode.concat(",log");	// log them
			}
			// TODO - change this to be specific type like trade so leaves attack reports unread
			// suggest simple type - if want more than 1 could have multiple server strategies
		    EventPage<Report> reports = reader.getReportPage(
		            util, url, ReportType.ALL, readOffset);
		    for (Report report : reports.getEvents()) {
		    	// check if new - or in test mode to force reading all of them
		    	// add check for specific types 
		        if ((report.hasNewFlag() || reportMode.contains("all")) &&
		        	report.matchesMode(reportMode)) {
		        	/* if (true) {
			        	EventLog.log("Read reportRef="+report.reportRef+" rType="+report.rType+", reportType="+report.reportType+", i="+report.reportIndex);
			        	continue;
		        	} */
		        	// System.exit(0);
	            	// EventLog.log(report.toString());
		        	// String	output = report.toString();
		        	// start with id of report and read page - use stored top report page
		            // String	page = report.read(util, url, reportMode);
		            String	page = report.read(util, storedUrl, reportMode);
		            // check for report
		            if (page != null) {
		            	// get report string
		            	String	output = report.toString(reportMode);
		            	// process it to send messages
		            	processReport(report);       	
		            	// check if writing to a specific file
		    			if (out != null) {
		    				out.println(output);
		    			} 
		    			if (reportMode.contains("event")) {
			    			EventLog.log(output);		    				
		    			}
		    			if (reportMode.equals("true")) {
			    			// dont record at all - still on server anyway	   				
		    			}
		    			// was a series of else ifs - this allows log,event but does not treat anything as log
			    		if (reportMode.contains("log")) {
		    				// default to log file - covers case where output file fails to open
			    			log.info(output);
		    			}
		            }
			    	// increment counter
			    	noRead += 1;
			    	// go back to list
		        }
		    	// increment counter
		    	noReports += 1;
		    }
		    // now check if deleting them and have got post information
	        boolean deleteSome = false;
		    if ((deleteReports != null) && (deleteValue != null)) {
		    	// setup post information
		        List<String> postNames = new ArrayList<String>();
		        List<String> postValues = new ArrayList<String>();
		        // no hiddeen fields  util.addHiddenPostFields(sendMsgPage, "reportMessageReader.hiddenDeleteFields", postNames, postValues);
		        // add x,y - random position against screen size
		        Util.addButtonCoordinates(deleteName, 80, 20, postNames, postValues);
		        postNames.add(deleteName);
		        postValues.add(deleteValue);
		    	
			    // should not need to get again as each read should return to page form.....
			    // reports = reader.getReportPage(util, url, ReportType.ALL, readOffset);
			    for (Report report : reports.getEvents()) {
			    	// just check type dont care if read in this loop or before 
			        if (report.matchesMode(deleteReports)) {
			        	log.debug("Delete reportRef="+report.reportRef+" name="+report.checkboxName+", Value="+report.checkboxValue);
				        // concatenate post checkbox
				        postNames.add(report.checkboxName);
				        postValues.add(report.checkboxValue);
				        // some to delete
				        deleteSome = true;
			        }
			    }
	        	if (deleteSome) {
			        util.httpPostPage(Util.getFullUrl(url, REPORT_SUFFIX), postNames, postValues, false);
	        	}
		    }
	        // System.exit(0);
	        if (noRead > 0) {
	        	log.debug("read "+noRead+"/"+noReports+" reports from offset "+readOffset);
	        }		    
	    	// increment count for next time if full page
		    if (noReports < REPORTS_PER_PAGE) {
		    	// reset to start - provides defensive check
		    	readOffset = 0;
		    } else {
		    	// add count on page unless deleted some, in which case use same offset, same as pressing back button
		    	if (!deleteSome) {
			    	readOffset += noReports;		    	
		    	}
		    }
		    if (out != null ) {
				out.close();
		    }
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(NullPointerException e){
			// add null pointer catch so does not stall server if any problems
			e.printStackTrace(); 
		}
		// return offset
    	return readOffset;
    }
    
    /**
     * read and display reports according to specified mode
     * 
     * @param util		required for http read
     * @param storedUrl		server/village url
     * @param reportMode	controls format
     * @return				next offset to read
     */
    public	int		readReports(Util util, String loginUrl, String reportMode) {
    	// store mode, util and login string each time
    	storeReports = reportMode;
    	storedUtil = util;
    	storedUrl = loginUrl;
		try {
        	// ReportMessageReader reader = ReportMessageReader.getInstance();
        	// ReportMessageReader reader = new ReportMessageReader();
			ReportMessageReader reader = this;
			// EventLog.log("before check for new lastRead "+lastRead);
			if (reader.newReports(util, loginUrl, reportMode)) {
				log.debug("starting lastRead "+lastRead);
				if (lastRead != 0) {
					EventLog.log("Reading More Reports from "+lastRead);
				} else {
					EventLog.log("Reading Reports");
				}
				// read some - act like a person first check latest page
				int newOffset = readReports(util, loginUrl, reportMode, 0);
				
				// check if only reading first page - filter mode and not deleting them
				// if (ReportMessageReader.filterMode(reportMode)) {
				// lastRead = 0;
				
				// use new if larger for next page - case when first page
				if( newOffset > lastRead ) {
					lastRead = newOffset;
				}
				// now check if more
				int attempts = 0;
				log.debug("newOffset "+newOffset+" lastRead "+lastRead);
				// use while loop with protected count to simulate reading some then getting bored
				while ((lastRead > 0) && reader.newReports(util, loginUrl, reportMode) && (attempts++ < PAGES_PER_TIME)) {
					// read some more from where got to last time - this is like having second tab open
					lastRead = readReports(util, loginUrl, reportMode, lastRead);
					// log.debug("newOffset "+newOffset+" lastRead "+lastRead);
				}
				// check if still more by counting attempts
				//if (newReports(util, storedUrl)) {
				if (attempts >= PAGES_PER_TIME) {
					// there are
					// return lastRead;
				} else {							
					// no new reports - reset to latest page
					lastRead = 0;
				}
			} else {
				// no new reports - reset to latest page
				lastRead = 0;
			}
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// reset in case caused problem
			lastRead = 0;
		}
		log.debug("returning lastRead "+lastRead);
		// return offset
		// System.exit(0);
    	return lastRead;
    }    

    

    // ------------------------------------------------------------------
    // MESSAGE
    // ------------------------------------------------------------------
    public enum MessageType {
        /** Incoming message type.*/
        INCOMING(MESSAGE_SUFFIX),
        /** Outgoing message type.*/
        OUTGOING(MESSAGE_SUFFIX + "?t=2");
        /** contains the url suffix of the messageType.*/
        private String urlSuffix;

        private MessageType(String suffix) {
            urlSuffix = suffix;
        }

        public String urlSuffix() {
            return urlSuffix;
        }
    }

    /**
     * This class represent an ingame (yellow) message, the content of it can be
     * reached by the {@link #read(Util, String)} method.
     * 
     * @author biminus
     * 
     */
    public static class Message {
        /** Message identifier.*/
        private int msgId;
        /** Message title.*/
        private String msgTitle;
        /** Message read flag true=>unread, false=>read.*/
        private boolean msgFlag;
        /** The user id of the sender.*/
        private int msgSenderUid;
        /** The name of the sender.*/
        private String msgSenderName;
        /** The time when the message was sent.*/
        private String msgTime;

        /**
         * Ctor.
         * 
         * @param id
         *            message identifier
         * @param title
         *            message title
         * @param newMsg
         *            new message indicator
         * @param senderUid
         *            user id of the sender
         * @param senderName
         *            name of the sender
         * @param time
         *            message sent time.
         */
        public Message(int id, String title, boolean newMsg,
                int senderUid, String senderName, String time) {
            msgId = id;
            msgTitle = title;
            msgFlag = newMsg;
            msgSenderUid = senderUid;
            msgSenderName = senderName;
            msgTime = time;
        }

        /**
         * Reads the message and sets the <code>msgFlag</code> to false.
         * 
         * @param util
         *            http request
         * @param url
         *            server base url
         * @return the whole html page
         * @throws ConversationException
         */
        public String read(Util util, String url) throws ConversationException {
        	log.debug("read message "+this.msgId);
            String page = util.httpGetPage(Util.getFullUrl(url,
                    MESSAGE_SUFFIX + "?id=" + msgId));
            msgFlag = false;
			// back to top message page
            util.httpGetPage(url);
            return page;
        }
        
        public boolean hasNewFlag() {
            return msgFlag;
        }
        
        public String toString() {
            return "(id=" + msgId + ", title=" + msgTitle + ", msgFlag="
                    + msgFlag + ", sender=" + msgSenderName + "("
                    + msgSenderUid + ")" + ", time=" + msgTime + ")";

        }

        /**
         * Factory method for creating message.
         * 
         * @param tableRow
         *            a table row eg from
         *            http://speed.travian.com/nachrichten.php
         * @param util 
         * @return the created message object or null.
         */
        private static Message createMessage(Util util, String tableRow) {
            /*
            Pattern p2 = Pattern.compile(Util.P_FLAGS
                    + "<a href=\\\""
                    + MESSAGE_SUFFIX
                    + "\\?id=(\\d+)\\\">(.*?)</a>(.*?)</td>.*?<a href=\\\"spieler\\.php\\?uid=(\\d+)\\\">(.*?)</a>.*?<td[^>]*?>(.*?)</td>");
            */
			Pattern p2 = util.getPattern("reportMessageReader.messages", MESSAGE_SUFFIX);
            Matcher m2 = p2.matcher(tableRow);

            if (m2.find() && m2.groupCount() == 6) {
                int msgId = Integer.parseInt(m2.group(1));
                String msgTitle = m2.group(2);
                boolean msgFlag = m2.group(3).trim().length() != 0;
                int senderUid = Integer.parseInt(m2.group(4));
                String senderName = m2.group(5);
                String time = m2.group(6);
                return new Message(msgId, msgTitle, msgFlag, senderUid,
                        senderName, time);
            }
            return null;
        }
    }


    /**
     * add commands from config file to the command and message list
     * 
     * @param report mode
     */
    public void setCommands(SubnodeConfiguration config) {
    	// get commands
        // List<String> commandName = new ArrayList<String>();
        // List<String> commandText = new ArrayList<String>();
		List<SubnodeConfiguration> itemNodes = config.configurationsAt("/item");
		for (SubnodeConfiguration itemNode : itemNodes) {
			// check if already exists
			String cmdText = itemNode.getString("/@title", "");
			String cmdName = itemNode.getString("/@name", "");
			String title = itemNode.getString("/@msgTitle", "");
			String text = itemNode.getString("/@msgText", "");
			
			// should this support no message declared in reply
			// in which case will it hit duplicate
			// also are duplicates ok if want to send same thing several times
			// was this just a remnant of the factory instance version?
			
			int cmdNo = 0;
			// check if already set
			while ((cmdNo = findEvent(cmdText, cmdNo, false)) != -1) {
				log.trace("checking "+cmdNo+" title "+msgTitle.get(cmdNo));
				if (msgText.get(cmdNo).equals(text) &&
					msgTitle.get(cmdNo).equals(title) &&
					commandName.get(cmdNo).equals(cmdName)) {
					log.trace(cmdNo+" matches");
					// is - quit while loop
					break;
				}
				cmdNo++;
			}
			if (cmdNo != -1) {
				// existing
				log.debug("Duplicate Command "+cmdNo+": "+cmdName+" Text:"+cmdText);
				// System.exit(0);
			} else {
			}
				// add new one
				commandText.add(cmdText);
				commandName.add(cmdName);
				msgTitle.add(title);
				msgText.add(text);
				msgSent.add(false);
				cmdNo = noCommands++;
				log.debug("Adding Command "+cmdNo+": "+cmdName+" Text:"+cmdText);
			// commandIds.add(itemNode.getString("/@id", null));
			// int maxLevel = itemNode.getInt("/@maxLevel", 999);
		}

    }
    
    /**
     * search command list for match
     * 
     * @param name
     * @return command index if found, -1 if not 
     */
    public int	findEvent(String name) {
    	return findEvent(name, 0, true);
    }
    /**
     * search command list for match
     * 
     * @param name
     * @param first index to check from 
     * @param flag to indicate if only return unsent events
     * @return command index if found, -1 if not 
     */
    public int	findEvent(String name, int first, boolean unsent) {
		// check for name using contains so can be in any order
		for (int cmdNo = first; cmdNo < commandName.size(); cmdNo++) {
    		if (name.contains(commandText.get(cmdNo))) {
    			String	cmd = commandName.get(cmdNo);
    			log.trace("Received Event "+cmdNo+" ("+cmd+") found in:"+name);
    			// check if already sent notification
    			if (unsent && msgSent.get(cmdNo)) {
    				// just trace
    				log.trace("Already sent message for event "+cmdNo);
    				// search for more of same command to use
    				continue;
    			}
				// System.exit(0);
    	    	return cmdNo;
    		}
		}
    	return -1;
    }
    
    public boolean	notifyEvent(int eventNo, String p1, String p2, String p3, String p4, String p5) {
    	boolean result = false;
		String mTitle = "";     		
    	if (msgTitle.get(eventNo) != null) {
    		mTitle = String.format(msgTitle.get(eventNo), p1, p2, p3, p4);     		
    	}
		String mText = ""; 
    	if (msgText.get(eventNo) != null) {
    		mText = String.format(msgText.get(eventNo), p1, p2, p3, p4); 
    	}
    	// check for any info
    	if ((p5 != null) && (p5.length() > 1)) {
			mText += " info " + p5;
		}
		try {
			// set so only send once - reset every day or every time server runs?
			msgSent.set(eventNo, true);
			sendMessage(storedUtil, storedUrl, storedUtil.getUserName(), mTitle, mText);
			result = true; 
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	    					
		// set so only send once - reset every day or every time server runs?
		msgSent.set(eventNo, true);
    	return result;
    }
  
    /**
     * sets mode for handling ingame reports starting with the next one.
     * 
     * @param report mode
     */
    public void setMessagesMode(String mode, SubnodeConfiguration config) {
        checkMessages = mode ;
        // checkMessages = messageConfig.getString("/output", "log");
        // force all mode
        // checkMessages = "all" ;
        messageCommands = config.getString("/@commands", null);
        // EventLog.log(storedUrl+" messageMode="+mode+" messageCommands="+messageCommands);
		log.debug("checkMessages Mode "+ mode +" CommandMode "+messageCommands);
        setCommands(config);
    }
    /**
     * Checks for any outstanding messages
     * 
     * @param util
     *            http request needs
     * @param serverUrl
     *            the base url http://speed.travian.com/
     * @return boolean true if more unread reports, false if all read
     * @throws ConversationException
     */
    public Boolean newMessages(Util util, String serverUrl) throws ConversationException {
    	// uses mN.gif rather than any direct indicator in html to show messages waiting
    	// javascript selects based on class     	// <div id="n5" class="i3">
    	// div.i1{background-image:url(un/l/m1.gif);}div.i2{background-image:url(un/l/m2.gif);}
    	// div.i3{background-image:url(un/l/m3.gif);}div.i4{background-image:url(un/l/m4.gif);}
    	// m3 report m2=i2 message m1 both m4=i4 neither
        // first get report type
    	// String page = village.gotoMainPage();
    	String	page =util.httpGetPage(serverUrl);
		Pattern p = util.getPattern("reportMessageReader.status");
		Matcher m = p.matcher(page);
		if (m.find()) {
			String mType = m.group(1);
			log.debug(mType+" checkMessages "+checkMessages);
			// check for class that indicates messages waiting indicator or test mode to read all
			if (mType.equals("i2") || mType.equals("i1") || ((checkMessages != null) && checkMessages.contains("all"))){
				return true;
			}
		} else {
			log.warn("Cannot find Message Status");			
		}
		// default to none - does not generate pattern error
    	return false;
    }

    /**
     * send an IGM
     * @param recipient
     * @param title
     * @param message
     * @return
     * @throws ConversationException
     */
    public static	String	sendMessage(Util util, String url, String recipient, String title, String message) throws ConversationException {    	
        // String	url = storedUrl;
        // Util	util = storedUtil;
        
        EventLog.log("Sending Message ("+title+") To:"+recipient);
        String sendMsg = MESSAGE_SUFFIX;
        String sendMsgPage = util.httpGetPage(Util.getFullUrl(url,
                sendMsg + "?t=1"));
        List<String> postNames = new ArrayList<String>();
        List<String> postValues = new ArrayList<String>();

        util.addHiddenPostFields(sendMsgPage,
                "reportMessageReader.hiddenPostFields", postNames, postValues);
        Util.addButtonCoordinates("s1", 50, 20, postNames, postValues);
        postNames.add("an"); // recipent
        postValues.add(recipient);
        postNames.add("be"); // title
        postValues.add(title);
        postNames.add("message"); // msg
        postValues.add(message);
       
        return util.httpPostPage(Util.getFullUrl(url,
        sendMsg), postNames, postValues, true);
    }

   

	/**
     * check text for a hidden command - if specific type found execute, if non standard return rest of string as instruction
     * @param inputMsg
     * @return any none standard command text after command string
     */
    // public static String checkCommands(String sender, Boolean flag, String inputMsg) {
    public String checkCommands(Message msg) {
    	String outputCmd = null;
    	// check if looking for commands - and this is a new message or forcing all
    	// handle message - needs to read it and clear the flag so not handled more than once 
    	// if ((messageCommands != null) && (messageCommands != "false") && flag) {
        if ((msg.msgTitle != null) && (msg.msgFlag || (messageCommands.contains("all")))) {
			// send a response
			try {
	        	String inputMsg = msg.msgTitle;
	        	
	    		// command action - check for standard using contains so can be in any order
	    		for (int cmdNo = 0; cmdNo < commandName.size(); cmdNo++) {
	        		if (inputMsg.contains(commandText.get(cmdNo))) {
	        			String	cmd = commandName.get(cmdNo);
	        			log.debug("Received Command Message("+cmd+") in:"+inputMsg);
	        			String user = storedUtil.getUserName();
	        			String sender =  storedUtil.stripTags(msg.msgSenderName);
	        			// warn or disable?  
	        			if (!sender.equalsIgnoreCase(user) && !messageCommands.contains("any")) {
	        				log.warn("IGM Command Received from other than user:"+sender);
	        				// disable for now
	        				// return null;
	        				// just skip this one
	        				continue;
	        			}
	        			// read the message so wont read it again, done before action in case there is a problem
	    	            String page = msg.read(storedUtil, storedUrl);
	        			// action translated command
	        			if (cmd.equalsIgnoreCase("suspend")) {
	            			EventLog.log("Suspending Server and All Strategies");
	            			storedUtil.getServer().suspend(true);
	        			} else if (cmd.equalsIgnoreCase("resume")) {
		            		EventLog.log("Resuming Server");
	            			storedUtil.getServer().suspend(false);
	        			} else if (cmd.equalsIgnoreCase("build")) {
		            		EventLog.log("Can we build something listed in the message body:"+page);
	        			} else {
	            			// command header but not a built in standard - return text after match point
	            			outputCmd = inputMsg.substring(inputMsg.indexOf(commandText.get(cmdNo)));      				
	        			}
	        			// send a response
        				// send a reply to sender - defensive check above as should normally be self
	        			if (msgTitle.get(cmdNo) != null) {
	    					sendMessage(storedUtil, storedUrl, sender, msgTitle.get(cmdNo),msgText.get(cmdNo));	        				
	        			}
    					// System.exit(0);
	        	    	return outputCmd;
	        		}
	    		}
	    		log.debug(msg.msgId+" not command ("+inputMsg+") ("+msg.msgFlag+") mode="+messageCommands);                		
			} catch (ConversationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	} else {
    		log.trace(msg.msgId+" not checking ("+msg.msgTitle+") ("+msg.msgFlag+") mode="+messageCommands);                		
    	}
    	return outputCmd;
    }
    
    /**
	 * @return the string that is used to select title scanning mode
	 */
	public String getScanMode() {
		return MESSAGE_SCAN;
	}

	/**
     * read and display Messages according to specified mode, start for specified point
     * 
     * @param util		required for http read
     * @param url		server/village url
     * @param msgMode	controls format
     * @param lastRead		counter for last report read latest is 0	
     * @return				update lastRead
     */
    private	int		readMessages(Util util, String url, String msgMode, int readOffset) {
        try {
        	// open file if needed for output
		    int	noReports = 0;
		    int	noRead = 0;
        	PrintWriter out = null;
			try {
				if (msgMode.contains("csv")) {
					out = new PrintWriter(new FileOutputStream(new File("logs" + File.separator + "Messages.csv"), true));
				}
				else if (msgMode.contains("txt")) {
					out = new PrintWriter(new FileOutputStream(new File("logs" + File.separator + "Messages.txt"), true));
				}
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				msgMode.concat(",log");	// log them					
			}				
	        // EventPage<Message> msgs = ReportMessageReader.getInstance().getYellowMessagePage(util, url, MessageType.INCOMING, readOffset);
	        EventPage<Message> msgs = getYellowMessagePage(util, url, MessageType.INCOMING, readOffset);
			
	        // check if only scaning titles - for commands
			// if (checkMessages.equals(MESSAGE_SCAN)) {
			if (msgMode.equalsIgnoreCase("false")) {
				log.debug(checkMessages+" Message mode:"+msgMode+" offset:"+readOffset);
			} else if (msgMode.equalsIgnoreCase("false") || msgMode.equalsIgnoreCase(getScanMode())) {
				log.debug(checkMessages+" Message Mode Scanning "+msgMode+" offset:"+readOffset);
			} else {
				log.trace(checkMessages+" Message Mode Reading:"+msgMode+" offset:"+readOffset);
			    for (Message msg : msgs.getEvents()) {
			    	// check if new - or in test mode to force reading all of them, use local param not checkMessages
			        if (msg.hasNewFlag() || msgMode.contains("all")) {
			        	// String	output = report.toString();
			        	// start with id of report and read page
			            String	page = msg.read(util, url);
			            // check for report
			            if (page != null) {
			            	// get report string
			            	String	output = msg.toString();
			    			String pattern = "reportMessageReader.msgcontent";
			    			Pattern p = util.getPattern(pattern);
			    			Matcher m = p.matcher(page);
			    			if (m.find()) {
			    				output = output.concat(m.group(1));
			    				output = util.stripTags(output);
			    			} else {
			    				log.warn(msg.msgId+" ("+msg.msgTitle+") cannot find "+pattern);
			    				util.saveTestPattern(pattern, p, page);
			    			}
			    			// change so can support multiple modes all,event,log
			    			if (out != null) {
			    				out.println(output);
			    			}
			    			if (msgMode.contains("event")) {
				    			EventLog.log(output);		    				
			    			}
			    			if (msgMode.equals("true")) {
				    			// dont record at all just read it - still on server anyway	   				
			    			}
			    			if (msgMode.contains("log")) {
			    				// default to log file - covers case where output file fails to open
				    			log.info(output);
			    			}
			            }
				    	// increment no read
				    	noRead += 1;
			        }
			    	// increment row counter - no no read
			    	noReports += 1;
			    }
			}
	        if (noRead > 0) {
	        	log.debug("read "+noRead+"/"+noReports+" Messages from offset"+readOffset);
	        }
	    	// increment count for next time if full page
		    if (noReports < REPORTS_PER_PAGE) {
		    	// reset to start - provides defensive check
		    	readOffset = 0;
		    } else {
		    	readOffset += noReports;		    	
		    }
		    if (out != null ) {
				out.close();
		    }
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(NullPointerException e){
			// add null pointer catch so does not stall server if any problems
			e.printStackTrace(); 
		}
		// return offset
    	return readOffset;
    }
    
    /**
     * read and display messages according to specified mode
     * 
     * @param util		required for http read
     * @param storedUrl		server/village url
     * @param reportMode	controls format
     * @return				next offset to read
     */
    public	int		readMessages(Util util, String loginUrl, String reportMode) {
    	// this is a single instance not per server so store mode, util and login string each time
        checkMessages = reportMode ;			
    	storedUtil = util;
    	storedUrl = loginUrl;
		try {
			// now check for messages
			if (newMessages(util, loginUrl)) {
				String	action = reportMode.equalsIgnoreCase(getScanMode()) ? "Checking" : "Reading" ;
				if (lastMessage != 0) {
					EventLog.log(action+" More Messages from "+lastMessage);
				} else {
					EventLog.log(action+" Messages");
				}
				// read some - act like a person first check latest page
				int newOffset = readMessages(util, loginUrl, reportMode, 0);
				// now check if more
				int attempts = 0;
				// use while loop with protected count to simulate reading some then getting bored
				while ((newOffset > 0) && newMessages(util, loginUrl) && (attempts++ < PAGES_PER_TIME)) {
					// use new if larger for next page - case when first page
					if( newOffset > lastMessage ) {
						lastMessage = newOffset;
					}
					// read some more from where got to last time - this is like having second tab open
					lastMessage = readMessages(util, loginUrl, reportMode, lastMessage);
				}
				// check if still more by counting attempts
				if (attempts >= PAGES_PER_TIME) {
					// there are
					// return lastRead;
				} else {							
					// no new reports - reset to latest page
					lastMessage = 0;
				}
			} else {
				// no new reports - reset to latest page
				lastMessage = 0;
			}
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// reset in case caused problem
			lastMessage = 0;
		}
		// return offset
    	return lastMessage;
    }    
    
}

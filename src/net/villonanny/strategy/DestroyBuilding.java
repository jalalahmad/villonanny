package net.villonanny.strategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.Building;
import net.villonanny.misc.TravianVersion;
import net.villonanny.type.BuildingType;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class DestroyBuilding extends Strategy {

    private final static Logger log = Logger.getLogger(DestroyBuilding.class);
    private final static String MAIN_BUILDING_DESTROY_POST_SUFFIX = "build.php";
    private final static int MIN_BUILDING_ID = 19;
    private final static int MAX_BUILDING_ID = 40;
    private final static String [] DESTROY_POST_NAMES = new String[] {"gid", "a", "abriss"};

    public TimeWhenRunnable execute() throws ConversationException {
        // <strategy class="DestroyBuilding" enabled="true" >
        // <building id="19" name="BUILDING NAME" minLevel="9"/>
        // </strategy>
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());

        try {
        	Building mainbuilding = village.getBuildingMap().getOne(BuildingType.MAIN_BUILDING);
        	// check if main building level 10 first
        	if (mainbuilding==null || mainbuilding.getCurrentLevel() < 10) {
        		EventLog.log("Need Main Building Level 10 to Destroy Building");
        		//Assume if a build strategy is waiting to build in this spot, its no point in starting it. fofo			
                //we might consider having the srategy waiting for the main building to finish with a minpauseminutes. fofo
        		//Assume if a build strategy is waiting to build in this spot, its no point in starting it. fofo			
        		//village.strategyDone.setFinished (this.getId(), true); //register it as done			
                return TimeWhenRunnable.NEVER;        		
        	}
        	// check valid number
//            int buildingId = config.getInt("/building/@id", 0);
            List<String> buildingIds = config.getList("/building/@id", new ArrayList<String>());
            List<String> allIds = super.itemIdsToList(buildingIds);
            TimeWhenRunnable result = TimeWhenRunnable.NEVER;
            for (String buildingIdString : allIds) {
            	try {
					int buildingId = Integer.parseInt(buildingIdString);
					result = destroy(buildingId, mainbuilding);
					if (result.before(TimeWhenRunnable.NEVER)) {
						break; // Break cycle because destroying in progress
					}
				} catch (NumberFormatException e) {
					log.error("Invalid building id (ignored): " + buildingIdString);
				}
            }
            return result;
        } finally {
            NDC.pop();
        }
    }
    
    private TimeWhenRunnable destroy(int buildingId, Building mainbuilding) throws ConversationException {
        log.debug("Building ID from configfile: " + buildingId);
        if (buildingId < MIN_BUILDING_ID || buildingId > MAX_BUILDING_ID) {
            throw new ConversationException("Building id (" + buildingId + ") must be between " + MIN_BUILDING_ID + " and " + MAX_BUILDING_ID);
        }
//        String mainbuilding = Util.getFullUrl(village.getVillageUrlString(), MAIN_BUILDING_SUFFIX);
        String page = util.httpGetPage(mainbuilding.getUrlString());
        //first, check if a demolition is ongoing
        // Pattern p = Pattern.compile("(?s)(?i)" + "<span id=timer1>(\\d+):(\\d+):(\\d+)</span>");
		Pattern p = util.getPattern("destroyBuilding.ongoing");
        Matcher m = p.matcher(page);
        Long hr=0L, min=0L, sec=0L;
        if (m.find()) {
            hr  = Long.parseLong(m.group(1));
            min = Long.parseLong(m.group(2));
            sec = Long.parseLong(m.group(3));
            EventLog.log("Building destruction ongoing "+m.group(1)+":"+m.group(2)+":"+m.group(3));
            return new TimeWhenRunnable (System.currentTimeMillis() +(hr*Util.MILLI_HOUR)+(min*Util.MILLI_MINUTE) +(sec*Util.MILLI_SECOND));
        }
        //then check if our building is already destroyed
		p = util.getPattern("destroyBuilding.done", Integer.toString(buildingId), Integer.toString(buildingId));
        m = p.matcher(page);
        if (m.find()) {
        	EventLog.log("Building destruction done. Slot "+Integer.toString(buildingId)+" free");
			village.strategyDone.setFinished (this.getId(), true); //register it as done			
            return TimeWhenRunnable.NEVER;
        } 
        String buildingNameCFG=config.getString("/building/@name", "");
//        if (buildingNameCFG==""){
//        	EventLog.log("No building name given. Exiting.");
//        	return TimeWhenRunnable.NEVER;
//        }
        log.debug("Village Info "+ village.getItem(Integer.toString(buildingId).toString() ));
    	// check level of target
        Integer minLevel = config.getInt("/building/@minLevel", 0);
        if (village.getItem(Integer.toString(buildingId)).getCurrentLevel() <= minLevel) {
        	EventLog.log("Building " + buildingId + " <= Minimum Level " + minLevel);
			village.strategyDone.setFinished (this.getId(), true); //register it as done			            	
            return TimeWhenRunnable.NEVER;
        } else {
        	log.debug("Building " + buildingId + " > Minimum Level " + minLevel);
        }
        /* p = Pattern.compile("(?s)(?i)"
                        + "<input type=\"hidden\" name=\"gid\" value=\"(\\S*)\">"
                        + ".*?"
                        + "<input type=\"hidden\" name=\"a\" value=\"(\\S*)\">"
                        + ".*?"
                        + "<option value=\""+Integer.toString(buildingId)+"\">"+Integer.toString(buildingId)+". (\\S*\\s*\\S*\\s*\\S*) (\\d*)</option>"
                        + ".*?"
                        + "<input class=\"f8\" type=\"Submit\" name=\"ok\" value=\"(\\S*\\s*\\S*\\s*\\S*\\s*\\S*)\"></p>"  
                        ); */
		p = util.getPattern("destroyBuilding.demolish", buildingId, buildingId);
		// log.debug("GAC destroyBuilding.demolish (" + p + ")");
        m = p.matcher(page);
        if (m.find()) {
            // log.debug("GAC 1(" + m.group(1) + ") 2("  + m.group(2) + ") 3("  + m.group(4) + ") 4("  + m.group(4) + ") 5("  + m.group(5) + ")");
            /* log.debug("GAC destroyBuilding.demolish count "  + m.groupCount() );
            for (Integer i = 0 ; i++ < m.groupCount() ; ) {
                log.debug(i + "(" + m.group(i) +  ")");
            } */
            String gidValue = m.group(1); 
            String aValue = m.group(2);
            String buildingName = m.group(3);
            String currentLevel = m.group(4);
            String abrissValue = Integer.toString(buildingId);
            //Check if it is the correct building, if building name is given
			String keyCFG = null;
			if (buildingNameCFG.length()>0) {
				keyCFG = util.getTranslator().getKeyword(buildingNameCFG);
			}
			String key = util.getTranslator().getKeyword(buildingName); 
	          // EventLog.log("fullkeyCFG: "+key+" typeKeyCFG: ");
            if (keyCFG==null || key.equals(keyCFG)){
            	String url = Util.getFullUrl(village.getVillageUrlString(),MAIN_BUILDING_DESTROY_POST_SUFFIX);
            	List<String> postNames = new ArrayList<String>(Arrays.asList(DESTROY_POST_NAMES)); //DESTROY_POST_NAMES = new String[] {"gid", "a", "abriss"}
            	List<String> postValues = new ArrayList<String>(Arrays.asList(gidValue, aValue, abrissValue));
            	// Before v3.5b there was a single "ok" value, then it became ok.x and ok.y
            	if (util.isTravianVersionBelow(TravianVersion.V35B)) {
            		String okValue = m.group(5); // This won't be found from v35b
            		postNames.add("ok");
            		postValues.add(okValue);
            	} else {
            		// post button co-ordinates. Image is 97x20
                	Util.addButtonCoordinates("ok", 97, 20, postNames, postValues);
            	}
            	EventLog.log ("Demolishing "+buildingName+" "+currentLevel);
            	String result="";
            	result = util.httpPostPage(url, postNames, postValues, true);
            	return getDestroyDoneTime(result);
            }
            else {
            	EventLog.log("Incorrect building type for demolish");
            	village.strategyDone.setFinished (this.getId(), true); //register it as done, if the building we tried to demolish have been replaced already, its obviously demolished enough :) fofo
            	return TimeWhenRunnable.NEVER;
            }
        } else {
        	// checking twice - assume just destroyed no error but throw pattern file in case
        	EventLog.log("Cannot Find " + buildingNameCFG + " id=" + Integer.toString(buildingId) + " Assume Already Destroyed");
        	util.saveTestPattern("destroyBuilding.demolish", p, page);
            // throw new ConversationException("Can't find form attributes " + Arrays.toString(DESTROY_POST_NAMES));
        	village.strategyDone.setFinished (this.getId(), true); //register it as done. fofo
        	return TimeWhenRunnable.NEVER;
        }
    }
    
    private TimeWhenRunnable getDestroyDoneTime(String resultPage) throws ConversationException {
        // <span id=timer1>0:22:35</span>
        // Pattern p = Pattern.compile(Util.P_FLAGS
        //                + "<img src=\".*?img/un/a/del.gif\".*?<td>.*?<span id=timer.*?>(\\d?\\d:\\d?\\d:\\d?\\d)</span> ");
		Pattern p = util.getPattern("destroyBuilding.destroyTime");
        Matcher m = p.matcher(resultPage);
        if (m.find()) {
            // Date time2Sleep =  util.getCompletionTime(m.group(1).trim());
    		int seconds = util.timeToSeconds(m.group(1).trim());
			int minPauseSeconds = super.config.getInt("/building/@minPauseMinutes", 1) * 60;
			if (seconds > minPauseSeconds) {
				minPauseSeconds = seconds;
			}
			return new TimeWhenRunnable(System.currentTimeMillis() + minPauseSeconds * Util.MILLI_SECOND);

            // return new TimeWhenRunnable(time2Sleep);
        } else {
        	util.saveTestPattern("destroyBuilding.destroyTime", p, resultPage);
            throw new ConversationException("Can't find completion time ");
        }
    }
}

package net.villonanny.strategy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.Building;
import net.villonanny.type.BuildingType;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class ThrowParty extends Strategy {

    private final static Logger log = Logger.getLogger(DestroyBuilding.class);
	private String localPage;
	private long pageAge=0;

    public TimeWhenRunnable execute() throws ConversationException {
        // <strategy class="DestroyBuilding" enabled="true" >
        // <building id="19" name="BUILDING NAME" minLevel="9"/>
        // </strategy>
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
			boolean bigParty = config.getBoolean("/@bigParty", false);
			Building partyHouse = village.getBuildingMap().getOne(
					BuildingType.CITY_HALL);
			String urlString = partyHouse.getUrlString();
			// check if main building level 10 first
			if (partyHouse == null) {
				EventLog.log("Need City hall to throw partys");
				village.strategyDone.setFinished(this.getId(), true); //register it as done			
				return TimeWhenRunnable.NEVER;
			} else {
				if ((partyHouse.getCurrentLevel() < 10) && (bigParty == true)) {
					EventLog.log("Need City hall level 10 to throw BIG partys");
				} else {
					TimeWhenRunnable party = getPartyGoingTime(util, urlString);
					if (party != null) {
						return party;
					}
					party = rockIt(util, bigParty, urlString);
					if (party != null) {
						return party;
					}
				}
			}
			//no party started, or going. return later to try agaim
			int pauseMinutes = config.getInt("/@minPauseMinutes", 75);
			return new TimeWhenRunnable((long) (pauseMinutes * 60 * 1000));
		} finally {
			NDC.pop();
		}
   }

	private void setPage(String newPage) {
		this.localPage = newPage;
		this.pageAge=System.currentTimeMillis();
	}
	
	private String getPage(Util util, String urlString) throws ConversationException {
		long now = System.currentTimeMillis();
		long ageSeconds=(now-this.pageAge)/1000;
		if (ageSeconds < 15) { //if page is not older than 15 seconds, use it
			log.debug("Party Strategy reuse old page, page age "+(int)(ageSeconds));
		} else {
			log.debug("Party Strategy get fresh page");
			this.pageAge=now;
			this.localPage=util.httpGetPage(urlString);
		}
		return this.localPage;
	}
	
    private TimeWhenRunnable rockIt(Util util, boolean bigParty, String urlString) throws ConversationException {
    	//first, check if a party is ongoing
        String page = this.getPage(util, urlString);
        TimeWhenRunnable partyGoingOn = getPartyGoingTime (util, urlString);
        if (partyGoingOn != null) {
        	return partyGoingOn;
        }
        //no party going, lets start one
        Pattern p;
        Matcher m;
        if (bigParty == true) {
        	p = util.getPattern("throwParty.bigParty");
        } else {
        	p = util.getPattern("throwParty.smallParty");
        }
        m = p.matcher(page);
        if (m.find()) {
        	//code to figure out the submit string
        	String postUrlString = Util.getFullUrl(urlString, m.group(1));
        	log.debug("throw party urlString "+m.group(1));
        	page = util.httpGetPage(postUrlString);
        	this.setPage(page);
        	partyGoingOn = getPartyGoingTime (util, urlString);
        	//util.saveTestPattern("TrainerSite: village.AvailableResources", p, page);
        	if (partyGoingOn != null) {
        		return partyGoingOn;
        	}
        } 
    	//could not start party for some reason, probably resources
    	EventLog.log("Could not start party, trying later");
        return null;
    }
    
    private TimeWhenRunnable getPartyGoingTime(Util util, String urlString) throws ConversationException {
        // <span id=timer1>0:22:35</span>
        // Pattern p = Pattern.compile(Util.P_FLAGS
        //                + "<img src=\".*?img/un/a/del.gif\".*?<td>.*?<span id=timer.*?>(\\d?\\d:\\d?\\d:\\d?\\d)</span> ");
    	String page=this.getPage(util, urlString);
    	Pattern p = util.getPattern("throwParty.partyLasting");
        Matcher m = p.matcher(page);
        Long hr=0L, min=0L, sec=0L;
        if (m.find()) {
            hr  = Long.parseLong(m.group(1));
            min = Long.parseLong(m.group(2));
            sec = Long.parseLong(m.group(3));
            EventLog.log("Party is going, ends in "+m.group(1)+":"+m.group(2)+":"+m.group(3));
            return new TimeWhenRunnable (System.currentTimeMillis() +(hr*Util.MILLI_HOUR)+(min*Util.MILLI_MINUTE) +(sec*Util.MILLI_SECOND));
        }
        return null;
    }
}

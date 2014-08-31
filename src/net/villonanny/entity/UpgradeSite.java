package net.villonanny.entity;

import java.util.EnumMap;
import java.util.HashMap;
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
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.log4j.Logger;

public class UpgradeSite extends Building {
	private final static Logger log = Logger.getLogger(UpgradeSite.class);
	
	private double hoursQueue; //the number of hours the training site will be busy if no new troops are put in queue
	private String queueString="";
	boolean debugg=false;
	
	private Map<TroopType, Integer> troopLevel; // Troops that can be produced
	private EnumMap<TroopType, ResourceTypeMap> troopCost;
	private Map<TroopType, String> troopNameList;
	private Map<TimeWhenRunnable, TroopTypeMap> producingTroop; // Troops currently in production
	
	public UpgradeSite(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
	}

	public void fetch(Util util) throws ConversationException {
		super.fetch(util);
		troopLevel = new HashMap<TroopType, Integer>();
		troopCost = new EnumMap<TroopType, ResourceTypeMap>(TroopType.class);
		troopNameList = new HashMap<TroopType, String>();
		producingTroop = new HashMap<TimeWhenRunnable, TroopTypeMap>();
		Pattern p;
		Matcher m;
		String page = util.httpGetPage(getUrlString());
		if (debugg) EventLog.log("URLstring= "+getUrlString());
		// 1 - Troop name 
		// 2 - Wood Cost
		// 3 - Clay Cost
		// 4 - Iron Cost
		// 5 - Crop Cost
		// 6 - Food Cost
		// 7 - Max Production available
//		p = Pattern.compile("(?s)(?i)<td class=\"s7\"><div><a href=.*? onClick=\"return Popup.*?;\">(\\S*\\s*\\S*)</a> <span class=\"f8\">\\S*\\s*(\\d*)[)]</span>" +
//				".*?" +
//				"src=\"img/un/r/1.gif\">(\\d*)[|]" +
//				".*?" +
//				"src=\"img/un/r/2.gif\">(\\d*)[|]" + 
//				".*?" +
//				"src=\"img/un/r/3.gif\">(\\d*)[|]" + 
//				".*?" +
//				"src=\"img/un/r/4.gif\">(\\d*)[|]"
//				);
		p = util.getPattern("upgradeSite.troopCost");
		// The use above of .*? instead of .* makes the difference 
		m = p.matcher(page);
		if (debugg) if (m==null) EventLog.log("m = nullpointer");
		int lastMatchPos=0;
		if (debugg) EventLog.log("First search: ");
		while (m.find()) { 
			lastMatchPos = m.end();
			String troopName = m.group(1).trim();
			String level = m.group(2); 
			String woodCost = m.group(3); 
			String clayCost = m.group(4); 
			String ironCost = m.group(5); 
			String cropCost = m.group(6); 
			ResourceTypeMap resources = new ResourceTypeMap(); 
			try {
				String fullkey = util.getTranslator().getKeyword(troopName); 
				String typeKey = fullkey.substring(fullkey.indexOf(".") + 1); 
				TroopType troopType = TroopType.fromString(typeKey);  
				resources.put(ResourceType.WOOD, Integer.valueOf(woodCost)); 
				resources.put(ResourceType.CLAY, Integer.valueOf(clayCost));
				resources.put(ResourceType.IRON, Integer.valueOf(ironCost));
				resources.put(ResourceType.CROP, Integer.valueOf(cropCost));
				troopCost.put(troopType, resources); 
				troopLevel.put(troopType, Integer.valueOf(level));
				troopNameList.put (troopType, troopName);
				if (debugg) EventLog.log("Upgradable troop: " + troopName+" type: "+typeKey+" Cost= ("+resources.toStringNoFood()+")");
			} catch (NumberFormatException nfe) {
				log.error("Problem parsing upgrade costs", nfe);
				throw new ConversationException("Problem parsing troop costs in " + this.getName());
			}
		}
		// check if found some
		if (debugg && (lastMatchPos == 0)) {
			log.debug("no Upgradable Troops found");
			// util.saveTestPattern("upgradeSite.troopCost", p, page);	
			// System.exit(0);
		}


		// p = Pattern.compile("(?s)(?i)><span id=timer1>(\\d*):(\\d*):(\\d*)</span>");
		p = util.getPattern("upgradeSite.time");
		m = p.matcher(page);
		m.region(lastMatchPos, page.length());
		int hrs=0, min=0, sec=0;
		if (m.find()) {
			hrs=Integer.parseInt(m.group(1));
			min=Integer.parseInt(m.group(2));
			sec=Integer.parseInt(m.group(3));
			//EventLog.log ("Upgrade site busy. Ready in  "+m.group(1)+":"+m.group(2)+":"+m.group(3));
			if (debugg) {
				log.debug("Upgrade site busy. Ready in  "+m.group(1)+":"+m.group(2)+":"+m.group(3));
			}
		}
		hoursQueue=(double)hrs+(double)min/(double)60+(double)sec/(double)(60*60);
	}
	
	public long upgradeTroop(Util util, TroopType type) throws ConversationException {
		Pattern p;
		Matcher m;
		String url=getUrlString();
		String page = util.httpGetPage(url);
		String troopName=troopNameList.get(type);
		if (debugg) {
			EventLog.log("upgradeTroop "+troopName);
		}
//		p = Pattern.compile("(?s)(?i)"+troopName+
//				                      ".*?"+
//								      "href=\"build.php[?]id=(\\d*)(\\S*)\">"
//				                      );
		p = util.getPattern("upgradeSite.troopName", troopName);
		m = p.matcher(page);
		String a_nr="";
		if (m.find()) {
			// EventLog.log("url "+url+" g1("+m.group(1)+") g2("+m.group(2)+") g3("+m.group(3)+")");
			a_nr=m.group(2);
			// EventLog.log("a_nr= "+m.group(2));
			url=url+a_nr;
			// GAC - add check that this troop has valid url and not error message
			if (a_nr.equalsIgnoreCase("none")) {
				// cannot upgrade 
				String s = util.stripTags(m.group(3));
				
				log.warn("Cannot Upgrade "+troopName+":"+s);
				return -1;
			}
			// ok so reconstruct url
			url = Util.getFullUrl(url,a_nr);
			// EventLog.log("url "+url);
			page = util.httpGetPage(url);
			// Dont upgrade http://s5.travian.co.uk/build.php?id=32&a=3
			// p = Pattern.compile("(?s)(?i)<span id=timer1>(\\d*):(\\d*):(\\d*)");
			p = util.getPattern("upgradeSite.timer1");
			m = p.matcher(page);
			if (m.find()) {
				int hrs=Integer.valueOf(m.group(1));
				int min=Integer.valueOf(m.group(2));
				int sec=Integer.valueOf(m.group(3));
				EventLog.log("Upgrade for "+troopName+" started. Done in "+m.group(1)+":"+m.group(2)+":"+m.group(3)+" h.");
				return (long)((hrs*60+min)*Util.MILLI_MINUTE+sec*Util.MILLI_SECOND);
			} else {
				EventLog.log("Cannot Find "+troopName+" upgradeSite.timer1");
			}
			
		} else {
			EventLog.log("Cannot Find "+troopName+" upgradeSite.troopName");
		}
		return -1;
	}
	
	public Double getTimeToReady() {
		return hoursQueue;
	}

	public Integer getCurrentLevel(TroopType type) {
		return troopLevel.get(type);
	}

	public ResourceTypeMap getTroopCost(TroopType type) {
		return troopCost.get(type);
	}
	
}

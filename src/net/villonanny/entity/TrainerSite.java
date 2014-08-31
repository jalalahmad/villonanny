package net.villonanny.entity;

import java.util.ArrayList;
import java.util.EnumMap;
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
import net.villonanny.misc.TravianVersion;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.TribeType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.log4j.Logger;

public class TrainerSite extends Building {
	private final static Logger log = Logger.getLogger(TrainerSite.class);
	
	private Map<TroopType, Integer> troopTrainMax; // Troops that can be produced
	private double hoursQueue; //the number of hours the training site will be busy if no new troops are put in queue
	private String queueString="";
	private String localPage;
	private long pageAge=0;
	private EnumMap<TroopType, ResourceTypeMap> troopCost;
	private TroopTypeMap troopsInTraining;
	private Map<TimeWhenRunnable, TroopTypeMap> producingTroop; // Troops currently in production
	
	public TrainerSite(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
	}
	
	public TroopTypeMap getTroopsInTraining () {
		return troopsInTraining;
	}

	public int getTypeInTraining (TroopType troopType) {
		return troopsInTraining.get(troopType);
	}
	
	public double getTrainerQueueHours () {
		return hoursQueue;
	}
	
	private void setPage(String newPage) {
		this.localPage = newPage;
		this.pageAge=System.currentTimeMillis();
	}
	
	private String getPage(Util util, String urlString) throws ConversationException {
		long now = System.currentTimeMillis();
		long ageSeconds=(now-this.pageAge)/1000;
		//log.debug("Trainersite getPage, page age "+(int)(ageSeconds));
		if (ageSeconds < 15) { //if page is not older than 15 seconds, use it
			log.debug("Trainersite reuse old page, page age "+(int)(ageSeconds));
		} else {
			log.debug("Trainersite get fresh page");
			this.localPage=util.httpGetPage(urlString);
			this.pageAge=System.currentTimeMillis(); //pageAge needs to be set after httpGetPage because of pause in httpGetPage
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
				util.saveTestPattern("TrainerSite: village.AvailableResources", p, page);
				throw new ConversationException("Can't find resources");
			}
			if (resourceType == ResourceType.FOOD) {
				continue;
			}
		}
		return availableResources;
    }

    public void trainerFetch(Util util) throws ConversationException {
		//super.fetch(util); no need after name change to trainerFetch
		troopTrainMax = new HashMap<TroopType, Integer>();
		troopCost = new EnumMap<TroopType, ResourceTypeMap>(TroopType.class);
		producingTroop = new HashMap<TimeWhenRunnable, TroopTypeMap>();
		troopsInTraining = new TroopTypeMap();
		Pattern p;
		Matcher m;
		String page = this.getPage(util, getUrlString());

		// Find out how long production queue there is
		int hr=0, min=0, sec=0;
		int lhr=0, lmin=0, lsec=0;
		// p = Pattern.compile("(?s)(?i)span id=timer.*?>(\\d+):(\\d+):(\\d+)</span></td>");
		p = util.getPattern("trainerSite.troopsInTraining");
		m = p.matcher(page);
		//EventLog.log("Try to find troops in training");
		//util.saveTestPattern("trainerSite.troopsInTraining", p, page);
		while (m.find()) {
			String troopIdNo = m.group(1);
			String noOfTraineeString = m.group (2);
			int type= Integer.parseInt(troopIdNo);
			if (util.isTravianVersionAbove(TravianVersion.V35)) {
				if (util.getTribeType() == TribeType.GAULS) {
					type = type - 20; //gaul type numbers are unit u21 for phalanx and so forth in v 3.6
					                  //Romans is the same old, I haven't tested teutons. fofo
				}
				if (util.getTribeType() == TribeType.TEUTONS) {
					type = type - 10; //gaul type numbers are unit u21 for phalanx and so forth in v 3.6
					                  //Romans is the same old, I haven't tested teutons, taking a chance on those fofo
				}
			}
			int  noOfTrainees=Integer.parseInt(noOfTraineeString);
			TroopType troopType=TroopType.fromInt(type);
			int oldTraineeNumber = troopsInTraining.get(troopType);
			troopsInTraining.put(troopType,noOfTrainees+oldTraineeNumber);
			//EventLog.log("Troop in training "+troopType.toString()+" count of "+noOfTrainees+" total "+(noOfTrainees+oldTraineeNumber));
		}
		
		p = util.getPattern("trainerSite.queueTimer");
		m = p.matcher(page);
		while (m.find()) {
			lhr =Integer.parseInt(m.group(1));
			lmin=Integer.parseInt(m.group(2));
			lsec=Integer.parseInt(m.group(3));
			hr=lhr;         //when placed before the parseint's these 3 
			min=lmin;       //selects the second to last timer. that don't seem to be necessary any more. fofo
			sec=lsec;
		}
		//util.saveTestPattern("trainerSite.queueTimer", p, page);
		// GAC Warning - there is a subtle difference between .com and .uk with extra char after time
		// com <td colspan=5>Next soldier will be ready in <span id=timer1>0:00:39</span>.</td></tr></table>
		// uk <td colspan=5>The next unit will be finished in <span id=timer1>0:01:28</span></td></tr>
		// so amending original pattern span id=timer.*?>(\\d+):(\\d+):(\\d+)</span></td> to remove </td>
		// this will leave the next unit time in lhr if anyone wants to use it
		hoursQueue=(double)hr+(double)min/(double)60+(double)sec/(double)(60*60);
		queueString=Integer.toString(hr)+":"+Integer.toString(min)+":"+Integer.toString(sec);
		log.debug("Trainer queue length is: "+queueString);
		

		// 1 - Troop name 
		// 2 - Wood Cost
		// 3 - Clay Cost
		// 4 - Iron Cost
		// 5 - Crop Cost
		// 6 - Food Cost
		// 7 - Max Production available
//		p = Pattern.compile("(?s)(?i)<table width=\"100%\" class=\"f10\".*?onClick[^>]+>([^<]*)</a>.*?src=\"img/un/r/1.gif\">(\\d*)[^<]*<img " + 
//				"class=\"res\" src=\"img/un/r/2.gif\">(\\d*)[^<]*<img class=\"res\" src=\"img/un/r/3.gif\">(\\d*)[^<]*<img class=\"res\" src=\"img/un/r/4.gif\">(\\d*)[^<]*<img " +
//				"class=\"res\" src=\"img/un/r/5.gif\">(\\d*)[^<]*<img.*?onClick[^>]+>\\((\\d*)\\)</a></div>");
		// The use above of .*? instead of .* makes the difference 
		p = util.getPattern("trainerSite.cost");
		m = p.matcher(page);
		int lastMatchPos=0;
		while (m.find()) {
			// String dInfo = "";
            // for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
            // log.debug("trainerSite.cost"+": "+dInfo);

			lastMatchPos = m.end();
			String troopName = m.group(1).trim();
			String stringNumber1 = m.group(2);
			String stringNumber2 = m.group(3);
			String stringNumber3 = m.group(4);
			String stringNumber4 = m.group(5);
			String stringNumber5 = m.group(6);
			String maxProd = m.group(7);
			ResourceTypeMap resources = new ResourceTypeMap();
			try {
				String fullkey = util.getTranslator().getKeyword(troopName); // romans.troop1
				String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
				TroopType troopType = TroopType.fromString(typeKey);
				resources.put(ResourceType.WOOD, Integer.valueOf(stringNumber1));
				resources.put(ResourceType.CLAY, Integer.valueOf(stringNumber2));
				resources.put(ResourceType.IRON, Integer.valueOf(stringNumber3));
				resources.put(ResourceType.CROP, Integer.valueOf(stringNumber4));
				resources.put(ResourceType.FOOD, Integer.valueOf(stringNumber5));
				troopCost.put(troopType, resources);
				troopTrainMax.put(troopType, Integer.valueOf(maxProd));
				log.debug("Trainable troop: " + troopType.toString() + " " + troopName);
			} catch (NumberFormatException nfe) {
				log.error("Problem parsing troop costs", nfe);
				throw new ConversationException("Problem parsing troop costs in " + this.getName());
			}
		}
		// check if found some
		if (lastMatchPos == 0) {
			log.debug("no Trainable Units found");
			// util.saveTestPattern("trainerSite.cost", p, page);			
		}
		
		// 1 - Troop quantity
		// 2 - Troop name
		// 3 - Time to finish
		// <img class="unit" src="img/un/u/2.gif" border="0"></td>
		// <td width="6%" align="right">89&nbsp;</td>
		// <td width="39%" class="s7">Praetorian</td>
		// <td width="25%"><span id=timer2>2:42:00</span></td>
		
		// p = Pattern.compile("(?s)(?i)<img *class=\"unit\" *src=\"img/un/u/\\d+.gif\"[^>]*></td>.*?<td[^>]*>(\\d+)[^<]*</td>.*?<td[^>]*>([^<]+)</td>.*?<td[^>]*><span id=timer2>([^<]+)</span></td>");
		p = util.getPattern("trainerSite.details");
		m = p.matcher(page);
		m.region(lastMatchPos, page.length());
		while (m.find()) {
			String stringNumer = m.group(1);
			String troopName = m.group(2);
			String timeString = m.group(3);
			try {
				String fullkey = util.getTranslator().getKeyword(troopName); // romans.troop1
				String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
				TroopType troopType = TroopType.fromString(typeKey);
				TimeWhenRunnable trainFinishTime = new TimeWhenRunnable(System.currentTimeMillis() + Util.timeToSeconds(timeString));
				TroopTypeMap trainingGroup = new TroopTypeMap();
				trainingGroup.put(troopType, Integer.getInteger(stringNumer));
				producingTroop.put(trainFinishTime, trainingGroup);
				log.debug("Currently training troops: " + troopType.toString());
			} catch (NumberFormatException nfe) {
				log.error("Problem parsing traning troops in", nfe);
				throw new ConversationException("Problem parsing traning troops in " + this.getName());
			}
		}
	}
	
	public void trainTroop(Util util, TroopType type, int value, boolean quick, double maxQueue) throws ConversationException {
		TroopTypeMap troop = new TroopTypeMap();
		troop.put(type, value);
		
		trainTroops(util, troop, quick, maxQueue);
	}
	
	public void trainTroops(Util util, TroopTypeMap troops, boolean quick, double maxQueue) throws ConversationException {
		String page;
		StringBuffer trainedTroops = null;
		TroopTypeMap trainTroops = new TroopTypeMap();
		
		// Go to troop transfer page
		page = this.getPage(util, getUrlString());
		List<String> postNames = new ArrayList<String>();
		List<String> postValues = new ArrayList<String>();

		// Find hidden fields
		// util.addHiddenPostFields(page, "<form method=\"POST\" name=\"snd\" action=\"build.php\">", postNames, postValues);
		util.addHiddenPostFields(page, "trainerSite.hiddenPostFields", postNames, postValues);
		Util.addButtonCoordinates("s1", 80, 20, postNames, postValues);
		
		// Add troop amounts
		for (TroopType type : troops.keySet()) {
			if (troops.get(type).intValue() <= 0) {
				continue;
			}
			if (maxQueue>=0) //if maxQueue is less than zero, its ignored
				if (hoursQueue>maxQueue) {
					EventLog.log("Current Troop queue too long:"+queueString);
					continue;
				}
			int troopsValue = troops.get(type);
			// Check if the troop building site is this one
			// or the alternative for Great Barracks/Stable Residence/Palace
			if ((type.getBuilding(util.getTribeType()) == getType()) || (type.getAlternativeBuilding(util.getTribeType()) == getType())) {
				// Check if the amount of troops to be trained is bigger than what we can train
				trainTroops.put(type, Math.max(Math.min(getTroopTrainMax(type).intValue(), troopsValue), 0));
				if (trainTroops.get(type) > 0) {
					if (trainedTroops == null) {
						trainedTroops = new StringBuffer(type.toString());
					} else {
						trainedTroops.append("; ");
						trainedTroops.append(type.toString());
					}
				}
			}
		}
		if (trainedTroops == null) {
			EventLog.log("No troops to be done");
			return;
		}

		// Convert to string post values
		int toSend=0;
		for (TroopType troopTypes : TroopType.values()) {
			toSend = trainTroops.get(troopTypes);
			postNames.add("t" + (troopTypes.toInt()+1));
			if (toSend>0) {
				postValues.add(Integer.toString(toSend));
			} else {
				postValues.add("");
			}
		}
		
		// post
		//EventLog.log("quick is "+quick);
		util.shortestPause(false); //the below is too fast
		page = util.httpPostPage(getUrlString(), postNames, postValues, quick);
		this.setPage (page); //this post page is the same as the fetch page, so we use it. fofo
		log.debug("Making troops " + toSend+" of "+trainedTroops.toString());
	}

	public Integer getTroopTrainMax(TroopType type) {
		return troopTrainMax.get(type);
	}

	public ResourceTypeMap getTroopCost(TroopType type) {
		return troopCost.get(type);
	}
	
}

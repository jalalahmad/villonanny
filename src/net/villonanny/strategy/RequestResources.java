package net.villonanny.strategy;

import java.awt.Point;


import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class RequestResources extends Strategy {
	private final static Logger log = Logger.getLogger(RequestResources.class);

	public TimeWhenRunnable execute() throws ConversationException {
		// <strategy class="RequestResources" desc="no 5 village Request resources" enabled="true" opportunist="true" >
		//    <options delayMinutes="60" neverLessThan="100" reqestId="string for req ID when uid not used"/>
		//    <request    x="" y="" wood="4000" clay="4000" iron="4000" crop="4000" percent="false"/>  the amount of resources to request
		//            x and y is the optional manual coordinates of the village requesting the items.
		//    <request    x="" y="" wood="80" clay="80" iron="80" crop="80" percent="true"/>  the amount of resources to request
		//    neverLessThan is the smallest amount ever requested, to avoid silly transmits of tiny amounts. no sane persons sends 3+6+1+2 resources between villages
		//    if percent is set to true, the request is made in percent of max storage space
		//    the percent="true" example above will request enough resources to fill up 80% of the storage space
		//    <maxRequest wood="4000" clay="4000" iron="4000" crop="4000" />  
		//           the maximum amount to be requested. used with percent="true" if you want
		// </strategy> 
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());
		try {
			log.debug("Starting RequestResources");
			int delayMinutes = config.getInt("/options/@delayMinutes", 60);
			int neverLessThan = config.getInt("/options/@neverLessThan", 100);
			String reqestId = config.getString("/options/@reqId", this.getId());
			ResourceTypeMap available = village.getAvailableResources();
			ResourceTypeMap requested = new ResourceTypeMap();
			ResourceTypeMap maxRequest = new ResourceTypeMap();
			String[] coords = new String [2];
			requested.put(ResourceType.WOOD, config.getInt("/request/@wood", 0));
			requested.put(ResourceType.CLAY, config.getInt("/request/@clay", 0));
			requested.put(ResourceType.IRON, config.getInt("/request/@iron", 0));
			requested.put(ResourceType.CROP, config.getInt("/request/@crop", 0));
			coords[0]= config.getString("/request/@x", "");
			coords[1]= config.getString("/request/@y", "");
			log.debug("(X|Y) coords from config  is ("+coords[0]+"|"+coords[1]+")");
			log.debug("requested ress before reduction is "+requested.toStringNoFood());
			if ((coords[0]=="") || (coords[1]=="")) {
				Point position = village.getPosition();
				coords[0]=Integer.toString(position.x);
				coords[1]=Integer.toString(position.y);
				log.debug("(X|Y) coords from getPosition is ("+coords[0]+"|"+coords[1]+")");
			}
			maxRequest.put(ResourceType.WOOD, config.getInt("/maxRequest/@wood", -1));
			maxRequest.put(ResourceType.CLAY, config.getInt("/maxRequest/@clay", -1));
			maxRequest.put(ResourceType.IRON, config.getInt("/maxRequest/@iron", -1));
			maxRequest.put(ResourceType.CROP, config.getInt("/maxRequest/@crop", -1));
			
			boolean percent = config.getBoolean("/request/@percent", false);
			ResourceTypeMap wantedResources = new ResourceTypeMap();
			int sum=0;
			ResourceTypeMap resourceCapacity = village.getMaxResources();
			log.debug("Max capacity is    :"+requested.toStringNoFood());
			log.debug("Available resources:"+available.toStringNoFood());
			for (ResourceType resourceType : ResourceType.values()) {
				if (resourceType == ResourceType.FOOD) {
					continue;
				}
				int wantedLevel=0;
				if (percent==true) {
					if (requested.get(resourceType) > 100) {//set something reasonable
						requested.put(resourceType, 75);    //in case of a mistake
					}
					wantedLevel = (resourceCapacity.get(resourceType)*requested.get(resourceType))/100; //wantedlLevelnow=how much we want the storehouse to have
				} else {
					wantedLevel = requested.get(resourceType);
				}
				if ((wantedLevel > maxRequest.get (resourceType)) && (maxRequest.get (resourceType)>-1)) {
					wantedLevel = maxRequest.get (resourceType);
				}
				int sendToMe = wantedLevel-available.get(resourceType);
				int max=resourceCapacity.get(resourceType)-available.get(resourceType);
				if (sendToMe>max) { //double check. should not be necessary. Do it anyway
					sendToMe=max;
				}
				if ((sendToMe<neverLessThan) || (sendToMe<0)) { //in case neverLessThan < 0
					sendToMe=0;
				}
				wantedResources.put(resourceType, sendToMe);
				sum=sum+sendToMe;
			}
			log.debug("requested ress after percent calc is "+wantedResources.toStringNoFood());
			if((sum>0) || (percent==false)) {
				village.strategyDone.setRequestedResources(reqestId, wantedResources, coords);
				EventLog.log("Requesting resources to ("+coords[0]+"|"+coords[1]+")"+
						" wood:"+Integer.toString(wantedResources.get(ResourceType.WOOD))+
                        " clay:"+Integer.toString(wantedResources.get(ResourceType.CLAY))+
                        " iron:"+Integer.toString(wantedResources.get(ResourceType.IRON))+
                        " crop:"+Integer.toString(wantedResources.get(ResourceType.CROP)));
			}
			else { //set requested res to zero
				village.strategyDone.setRequestedResources(reqestId, new ResourceTypeMap(), coords);
				EventLog.log("No need to request resources");
			}
			return new TimeWhenRunnable(System.currentTimeMillis() + delayMinutes*Util.MILLI_MINUTE);
		} 
		finally {
			NDC.pop();
		}
	}

	public boolean modifiesResources() {
		return false;
	}

	/**
	 * Return the minimum resources needed to run, or null if not applicable
	 * @return
	 */
	public ResourceTypeMap getTriggeringResources() {
		return null;
	}

}
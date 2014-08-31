package net.villonanny.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.MarketSite;
import net.villonanny.entity.Server;
import net.villonanny.entity.Village;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class LevelResources extends ServerStrategy {
	// <serverStrategy class="LevelResources" enabled="true" uid="lvl01" maxPauseMinutes="3" minPauseMinutes="0" tolerance="9000">
	//     <village enabled="true" villageUid="b02"/>
	//     <village enabled="true" merchants="15" villageUid="b03"/>
	//     <village enabled="true" villageUid="b04"  receiveOnly="true"/>
	//     <!-- TODO no village for all villages -->
	// </serverStrategy>
    private final static Logger log = Logger.getLogger(LevelResources.class);
	
	public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
        	long longestTimeToWait = Long.MAX_VALUE;
        	List<Village> villages = new ArrayList<Village>();
        	int tolerance = config.getInteger("/@tolerance", 1);
        	// Find and update all villages involved and total resources
    		Server server = util.getServer();
    		ResourceTypeMap totResources = new ResourceTypeMap(); // Sum of resources in all villages, by type
    		List<SubnodeConfiguration> villageNodes = config.configurationsAt("/village[@enabled='true']");
    		for (SubnodeConfiguration villageNode : villageNodes) {
    			String uid = villageNode.getString("/@villageUid", null);
    			if (uid==null) {
    				throw new InvalidConfigurationException("Missing 'uid' attribute");
    			}
    			Village village = server.getVillage(uid);
    			if (village==null) {
    				throw new InvalidConfigurationException("Missing village with uid=\"" + uid + "\"");
    			}
    			villages.add(village);
    			village.update();
    			ResourceTypeMap available = village.getAvailableResources();
    			totResources.addResources(available);
    		}
    		// Find average
    		int totVillages = villages.size();
    		ResourceTypeMap averageResources = totResources.getAverage(totVillages);
    		log.debug("Average resources = " + averageResources.toStringNoFood());
    		// Find difference from average
    		Map<Village, ResourceTypeMap> neededForVillageMap = new HashMap<Village, ResourceTypeMap>();
    		for (Village village : villages) {
    			ResourceTypeMap differenceFromAverage = village.getAvailableResources().getDifference(averageResources);
    			ResourceTypeMap villageCapacity = village.getMaxResources();
    			// Remove tolerance and limit on free space
       			for (ResourceType resource : differenceFromAverage.keySet()) {
       				if (resource.equals(ResourceType.FOOD)) {
       					continue;
       				}
       				int value = differenceFromAverage.get(resource);	
       				if (value>tolerance) {
       					value-=tolerance;
       				} else if (value<-tolerance) {
       					value+=tolerance;
       				} else {
       					value=0;
       				}
					int freeSpace = villageCapacity.get(resource)-village.getAvailableResources().get(resource);
					if (value>freeSpace) {
						value = freeSpace;
					}
					if (value<-freeSpace) {
						value = -freeSpace;
					}
       				differenceFromAverage.put(resource, value);
       			}
       			EventLog.log("Village " + village.getVillageName() + ", resource delta = " + differenceFromAverage.toStringNoFood());
       			neededForVillageMap.put(village, differenceFromAverage);
			}
    		// Send excess resources
       		for (Village sendingVillage : villages) {
       			String receiveOnly = config.getString("/village[@villageUid='"+sendingVillage.getId()+"']/@receiveOnly", "false");
       			if ("true".equalsIgnoreCase(receiveOnly)) {
       				log.debug("Village " + sendingVillage + " receives only");
       				continue;
       			}
       			ResourceTypeMap resourceBalance = neededForVillageMap.get(sendingVillage);
       			MarketSite market = sendingVillage.getMarket();
                if (market == null) {
                    log.debug("No market found; skipping " + sendingVillage.getVillageName());
                    continue;
                }
                sendingVillage.gotoMainPage();
                market.fetch(util);
                int merchantCapacity = market.getMerchantCapacity();
                int availableMerchants = market.getMerchantsFree();
       			int merchants = config.getInteger("/village[@villageUid='"+sendingVillage.getId()+"']/@merchants", 999);
       			if (merchants>availableMerchants) {
       				merchants = availableMerchants;
       			}
       			int merchantsToSend = merchants;
       			log.debug("Village " + sendingVillage.getVillageName() + " using " + merchants + " merchants");
       			// Find the sum of excess resources
       			int totInExcess = 0;
       			for (ResourceType resource : resourceBalance.keySet()) {
       				if (resource.equals(ResourceType.FOOD)) {
       					continue;
       				}
      				int value = resourceBalance.get(resource);
       				if (value>0) {
       					totInExcess+=value;
       				}
       			}
       			// For each excess resource, send to villages in need
       			for (ResourceType resource : resourceBalance.keySet()) {
       				// For each resource in excess, assign part of the merchants proportionally to the excess
       				if (resource.equals(ResourceType.FOOD)) {
       					continue;
       				}
       				int sendableResourceAmount = resourceBalance.get(resource);
       				if (sendableResourceAmount<=0) {
       					continue; // No resources to spare
       				}
       				int merchantsForResource = Math.round(((float)sendableResourceAmount/(float)totInExcess) * (float)merchants);
       				if (merchantsForResource<=0) {
       					totInExcess-=sendableResourceAmount; // Forget this value to cope with roundings
       					continue; // No merchants for this resource
       				}
       				log.debug("Village " + sendingVillage + " sends " + resource + " with " + merchantsForResource + " merchants");
       				// Find the sum of needed resources of this type
       				int totNeeded = 0;
       				for (Village destVillage : villages) {
       					if (!destVillage.equals(sendingVillage)) {
       						ResourceTypeMap destBalance = neededForVillageMap.get(destVillage);
       						int destValue = -destBalance.get(resource);
       						if (destValue>0) {
//       							ResourceTypeMap villageCapacity = destVillage.getMaxResources();
//       							int maxRes = villageCapacity.get(resource);
//       							if (destValue>maxRes) {
//       								destValue = maxRes;
//       							}
      							totNeeded += destValue;
       						}
       					}
					}
       				// Send the merchants
       				for (Village destVillage : villages) {
       					if (!destVillage.equals(sendingVillage) && merchantsForResource>0 && sendableResourceAmount>0) {
       						ResourceTypeMap destBalance = neededForVillageMap.get(destVillage);
       						int destValue = -destBalance.get(resource);
       						if (destValue>0) {
       							int merchantsForVillage = Math.round(((float)destValue/(float)totNeeded) * (float)merchantsForResource);
       							if (merchantsForVillage>merchantsToSend) {
       								// This is a workaround for a bug that maybe has been fixed
       								merchantsForVillage = merchantsToSend;
       								log.debug("Workaround: limiting merchants to " + merchantsToSend);
       							}
       							if (merchantsForVillage<=0) {
       								totNeeded-=destValue; // Forget this value to cope with roundings
       		       					continue; // No merchants for this village
       							}
       							log.debug("Village " + sendingVillage + " sends " + resource + " to " + destVillage + " with " + merchantsForVillage + " merchants");
       							int toSend = merchantCapacity * merchantsForVillage;
       							if (toSend>destValue) {
       								toSend = destValue;
       							}
       							if (toSend>sendableResourceAmount) {
       								toSend = sendableResourceAmount;
       							}
       							ResourceTypeMap resources = new ResourceTypeMap();
       							resources.put(resource, toSend);
       							EventLog.log("Village " + sendingVillage + " sending to " + destVillage + ": " + resources.toStringNoFood());
       							long timeToWaitSeconds = 60*60; // 1h
       							try {
       								timeToWaitSeconds = market.sendResource(util, resources, String.valueOf(destVillage.getPosition().x), String.valueOf(destVillage.getPosition().y), null, false, sendingVillage);
       								sendableResourceAmount-=toSend;
       								merchantsForResource-=Math.ceil(((double)toSend)/merchantCapacity);
       								merchantsToSend-=Math.ceil(((double)toSend)/merchantCapacity);
       								destBalance.addResources(resources);
       							} catch (net.villonanny.ConversationException e) {
       								log.error("Exception caught when sending resources. Skipping and continuing...", e);
       								// Keep going
       							}
       			                long timeToWaitMillis = timeToWaitSeconds * Util.MILLI_SECOND;
       			                // Keep the longest time
       			                if (timeToWaitMillis>0 && timeToWaitMillis > longestTimeToWait) {
       			                	longestTimeToWait = timeToWaitMillis;
       			                }
       						}
       					}
       				}
       			}
			}
       		long minPauseMillis = config.getInt("/@minPauseMinutes", 0) * Util.MILLI_SECOND;
       		long maxPauseMillis = config.getInt("/@maxPauseMinutes", 60*24) * Util.MILLI_SECOND;
       		if (longestTimeToWait == Long.MAX_VALUE) {
       			// Nothing sent
       			longestTimeToWait = minPauseMillis;
       		}
       		if (longestTimeToWait<minPauseMillis) {
       			longestTimeToWait = minPauseMillis;
       		}
       		if (longestTimeToWait>maxPauseMillis) {
       			longestTimeToWait = maxPauseMillis;
       		}
        	return new TimeWhenRunnable(System.currentTimeMillis() + longestTimeToWait);
        } finally {
            NDC.pop();
        }
	}
	

}

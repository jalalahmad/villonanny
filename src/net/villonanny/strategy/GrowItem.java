package net.villonanny.strategy;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.NoSuchElementException;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.Building;
import net.villonanny.entity.EmptySite;
import net.villonanny.entity.Field;
import net.villonanny.entity.UpgradeableSite;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.ResourceTypeToFieldMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class GrowItem extends Strategy {
	private final static Logger log = Logger.getLogger(GrowItem.class);
	private UpgradeableSite cheapest = null;

	private class GrowData {
		boolean allMaxed=true;
		boolean allBlocked=true;		// gac - add flag if all items waiting for something to build
		boolean queueFull=true;
		boolean updateNeeded = false; //changed to false. It dont seem any more updates are needed than the one before strategy is started
		Date earliestQueue = null;
		Date earliestItem = null;
	}
	
	public GrowItem() {
	}

	public TimeWhenRunnable execute() throws ConversationException {
		//	<strategy class="GrowItem" enabled="true" disableWhenFinished="true">
		//		<minProductionRate wood="234" clay="424" iron="100" crop="202" checkTimeMinutes="20"/>
		//		<item id="26" desc="Centro piccolo" building="Centro del villaggio" maxLevel="3"/>
		//		<item id="1, 2, 3-6, 8" desc="campi" maxLevel="10"/>
		//	</strategy>
		// - desc and building are optional
		// - when building specified, make one if site empty or grow it if site matches
		// - when building not specified, grow if not empty or make one if wall/rallypoint id (no other choices there)
//		int count;
//		int noOfMaxed;
//		boolean opportunist=true;

		NDC.push(getDesc()); // Strategy
		cheapest = null;
		GrowData growData = new GrowData();
//		count=0;
		try {
			if (!(village.buildingQueueAvailable() || village.fieldQueueAvailable())) {
				// When both queues are full, wait until the first one is available
				String s = "Construction queues full for " + village.getDesc();
				log.debug(s);
				EventLog.log(s);
				TimeWhenRunnable TimeToRun= new TimeWhenRunnable(village.getFirstAvailableQueueTime());
				// [xtian] why should we force it to be opportunist?
				// TimeToRun.setOpportunist(true); //run if villonanny should wake up before getFieldQueueAvailableTime in case of trade or loot
				return TimeToRun;
			}
			int checkTimeMinutes = config.getInt("/@minPauseMinutes", 60);
			// minProductionRate check moved to parent strategy class, inside timeWhenRunnable
            // gac add keep resources
			// TODO currently simple check but should be in growSingleItem so only upgrades if stays above keep
            if (!village.checkSpareResources(config)){
                EventLog.log("Not enough resource available");            	
				return new TimeWhenRunnable(System.currentTimeMillis() + checkTimeMinutes*Util.MILLI_MINUTE); // try again later
            }


			List<SubnodeConfiguration> itemNodes = super.config.configurationsAt("/item");
			for (SubnodeConfiguration itemNode : itemNodes) {
				List<String> itemIds = itemNode.getList("/@id", new ArrayList<String>());
				super.checkConsoleFlags();
				if (super.isDeleted()) {
					village.strategyDone.setFinished (this.getId(), true); //register it as done in case user screwed up and deleted something we wait for
					// The configuration was edited while running in this loop, and the strategy was removed/disabled
					return TimeWhenRunnable.NEVER; // Quit strategy
				}
				String buildingName = itemNode.getString("/@building", null);
				String buildingKey = translator.getKeyword(buildingName); // TranslationException if buildingName not found
				BuildingType desiredBuildingType = BuildingType.fromKey(buildingKey); // null if no /@building or name not a building
				if (buildingKey!=null && desiredBuildingType==null) {
					EventLog.log("evt.notBuilding", this.getClass(), buildingName);
				}
				
				int maxLevel = itemNode.getInt("/@maxLevel", 999);
				
				// Handle multiple id's
				List<String> allIds = super.itemIdsToList(itemIds);
				for (String singleItemId : allIds) {
					String itemLog = itemNode.getString("/@desc", buildingName==null?"":buildingName) + " (id=" + singleItemId + ")";
					// if add test of trigger conditions here before trying to grow item then may be no cheapest
					// need to think carefully about this dont want cheapest to be one blocked but dont want to finish if all waiting
					// use waitfor true to set allMaxed false?
					// do I then need to enhance debug below when both earliest and cheapest are null - extra flag on growdata
					
					//what is cheapest used for? isn't earliest all we need? fofo
					String	waitFor = itemNode.getString("/@waitFor", null);
					if (waitFor != null) {
						int minLevel = itemNode.getInt("/@minLevel", 1);
						int currentLevel = village.getMinLevel(waitFor);
						if (currentLevel < minLevel) {
							// blocked
							// assume not all maxed when cannot build it yet
							log.debug(buildingName+" Waiting For "+waitFor+" Level "+minLevel+" Current "+currentLevel);				        
							growData.allMaxed = false;	
							// dont change queue status, need to make sure test items in order 
							// break;
							continue;
						} else { 
							// set true at start set false if any has waitFor but is not blocked
							// have to do in growSingleItem after max check
							// growData.allBlocked = false;
						}
						// System.exit(0);							
					} else {
						// no wait for so not blocked
						// have to do in growSingleItem after max check
						// growData.allBlocked = false;
					}
					NDC.push(itemLog);
					try {
						growSingleItem(singleItemId, desiredBuildingType, maxLevel, growData);
//						count=count+1;
//						log.debug("count " + Integer.toString( count) + " NoOfMaxed " + Integer.toString(noOfMaxed) + itemNode.getString("/@desc", buildingName==null?"":buildingName) + " (id=" + singleItemId + ")");
					} finally {
						NDC.pop(); // itemLog
					}
				}
			}
//			log.debug(" Final count " + Integer.toString( count) + " NoOfMaxed " + Integer.toString(noOfMaxed));
			if (growData.allMaxed) { //all buildings are maxed or not buildable 
				village.strategyDone.setFinished (this.getId(), true);
				if (config.getBoolean("/@disableWhenFinished", true)) {
					EventLog.log("No more upgradable buildings, strategy \""+this.getDesc()+"\" finished and disabled");
					village.strategyDone.setFinished (this.getId(), true); //register it as done. fofo
					return TimeWhenRunnable.NEVER; //stop this strategy
				} else {
					EventLog.log("No more upgradable buildings, strategy \""+this.getDesc()+"\" finished but not disabled");
					return new TimeWhenRunnable(System.currentTimeMillis() + (checkTimeMinutes * Util.MILLI_MINUTE));	
				}
			}
			// check max before blocked
			if (growData.allBlocked) {
				EventLog.log("All building blocked waiting for minimum levels");					
				return new TimeWhenRunnable(System.currentTimeMillis() + (checkTimeMinutes * Util.MILLI_MINUTE));	
			}
			if (growData.queueFull) {
				EventLog.log("Construction queue full");
				return new TimeWhenRunnable(growData.earliestQueue);
			}
			if (growData.earliestItem==null && cheapest!=null && !cheapest.getFoodShortage()) {
				// Return latest time between construction queue available and resources available (you need both to be available)
				Date queueAvailableAt = village.getQueueAvailableTime(cheapest);
				Date resAvailableAt = util.calcWhenAvailable(village.getProduction(), village.getAvailableResources(), cheapest.getNeededResources());
				Date chosenDate = resAvailableAt;
				String itemName = cheapest.getName();
				if (queueAvailableAt.after(resAvailableAt)) {
					chosenDate = queueAvailableAt;
					cheapest = null; // Don't trigger by resources
				}
				EventLog.log("Can't upgrade now, must wait until " + chosenDate + " for \"" + itemName + "\" resources");
				// check if time is now - problem with submit string?
				if (!chosenDate.after(new Date())) {
					log.warn("Candidate for Growth Ready now - check submitUrl");
				}
				return new TimeWhenRunnable( chosenDate );
			}
			if (growData.earliestItem==null && cheapest==null) {
				log.debug("earliestItem==null and cheapest==null");					
//				EventLog.log("Not enough food (perhaps)");
			}
			if (cheapest != null && cheapest.getFoodShortage() ) {
				EventLog.log("Not enough food for " + cheapest.toString());
			}
			cheapest = null; // Don't trigger by resources
			log.info(String.format("Strategy %s done for now", getDesc())+ " earliest item is "+growData.earliestItem);
			if (growData.earliestItem==null) {
				// protect from null - need to find what causes it
				log.debug("GrowItem earliestItem is null cheapest is " + cheapest + " - buildings not available yet");
				return new TimeWhenRunnable(System.currentTimeMillis() + (checkTimeMinutes * Util.MILLI_MINUTE));			
			} else {
				return new TimeWhenRunnable(growData.earliestItem);	
			}
		} finally {
			NDC.pop(); // Strategy
		}
	}

	
	/**
	 * Build an item
	 * @param itemId
	 * @param desiredBuildingType
	 * @param maxLevel
	 * @param growData
	 * @throws ConversationException
	 */
	private void growSingleItem(final String itemId, final BuildingType desiredBuildingType, int maxLevel, GrowData growData) throws ConversationException {
		UpgradeableSite currentItem = village.getItem(itemId);
		if (currentItem == null) {
			log.warn("Item " + itemId + " not found in village");
			return; // Next item
		}
		
		if (currentItem instanceof EmptySite) {
			if (desiredBuildingType==null && !((EmptySite)currentItem).isOneChoiceOnly()) {
				log.warn("EmptySite has no building attribute: skipping item");
				return; // Next item
			}
			((EmptySite)currentItem).setDesiredBuildingType(desiredBuildingType);
		} else {
			if (currentItem.getCurrentLevel()>=maxLevel) {
				log.debug(String.format("Level %s reached", maxLevel));
				return; // Next item
			}
			if (desiredBuildingType!=null && !currentItem.getType().equals(desiredBuildingType)) {
				log.warn(String.format("Site building \"%s\" different from desired building \"%s\": skipping item", currentItem.getType(), desiredBuildingType));
				return; // Next item
			}
		}

		// only use name before fetch
		log.trace("growSingleItem "+currentItem.getName());
		
		if (growData.updateNeeded) {
			village.update();
			growData.updateNeeded = false;
		}
		
		// have a valid building to try so not blocked, clear here before check q so report q full not waiting
		growData.allBlocked = false;
		// Skip if no queues available
		boolean noQueue = false;
		if (currentItem.needsFieldsQueue() && !village.fieldQueueAvailable()) {
			noQueue = true;
		} else if (!currentItem.needsFieldsQueue() && !village.buildingQueueAvailable()) {
			noQueue = true;
		}
		
		if (noQueue) {
			log.debug("Queue not available for item \"" + currentItem.getName() + "\"");
			Date queueAvailableTime = village.getQueueAvailableTime(currentItem);
			if (growData.earliestQueue == null || queueAvailableTime.before(growData.earliestQueue)) {
				growData.earliestQueue = queueAvailableTime;
			}
			growData.allMaxed = false;
			return; // Next item // Queue not available for this item
		}
		
		growData.queueFull=false;
		growData.allMaxed=false;
		//  was if (log.isDebugEnabled()) {
		// if (log.isTraceEnabled()) {
		if (log.isDebugEnabled()) {
			log.debug("Construction queue for item " + currentItem.getName() + " available");
			log.debug("Available time=" + village.getQueueAvailableTime(currentItem));
			log.debug("fieldQueueAvailableTime=" + village.getFieldQueueAvailableTime());
			log.debug("buildingQueueAvailableTime=" + village.getBuildingQueueAvailableTime());
		}
		currentItem.fetch(util);
		// move debug to below fetch
		log.debug("Current item is: "+currentItem.toString());
		if (currentItem.isSubmittable()) {
			// Upgrade
			EventLog.log("Upgrading \"" + currentItem.getName() + "\"");
			Date localCompletionTime = currentItem.upgrade(util);
//				village.setQueueAvailableTime(item, localCompletionTime);
			growData.updateNeeded=true;
			if (growData.earliestItem==null || localCompletionTime.before(growData.earliestItem)) {
				growData.earliestItem = localCompletionTime;
			}
		} else {
			// Can't be upgraded, check if cheapest
			log.trace("Can't upgrade item \"" + currentItem + "\"");
			int totNeeded = currentItem.getTotResourcesNeeded();
			if (totNeeded>0) { // If totNeeded == 0 the item can't be built yet because of a prerequisite missing  
				if (cheapest==null || cheapest.getTotResourcesNeeded()>totNeeded) {
					log.debug("Cheapest item so far: \"" + currentItem.getName() + "\"");
					// "Food needed" is a problem only when there are enough resources
					if (growData.updateNeeded) {
						log.trace("Updating village");
						village.update(); // Update available resources
						growData.updateNeeded=false;
					}
					cheapest = currentItem;
					ResourceTypeMap available = village.getAvailableResources();
					ResourceTypeMap needed = currentItem.getNeededResources();
					if (log.isDebugEnabled()) {
						for (ResourceType resourceType : ResourceType.values()) {
							if (needed.get(resourceType)>available.get(resourceType)) {
									log.debug("Resource \"" + resourceType + "\": available=" + available.get(resourceType) + ", needed=" + needed.get(resourceType));
							}
						}
					}
				} else {
					log.trace("Not cheapest item: \"" + currentItem.getName() + "\"");
				}
			}
		}
	}

	
	/**
	 * Return the minimum resources needed to run, or null if not applicable
	 * @return
	 */
	public ResourceTypeMap getTriggeringResources() {
		return cheapest!=null?cheapest.getNeededResources():null;
	}
	
	public boolean modifiesResources() {
		return super.waiting==false; // Modifies only if not waiting
	}

//	 public String createId(SubnodeConfiguration strategyConfig) {
//		 // GrowItem doesn't have any special attributes that could make a better id.
//		 // We implement this method just as a reminder for developers.
//		 // Also remember that two GrowItem strategies with the same description (or no description)
//		 // make one single instance
//		 return super.createId(strategyConfig);
//	 }

//	@Override
//	public void updateConfig(SubnodeConfiguration strategyConfig, Util util) {
//		super.updateConfig(strategyConfig, util);
//		// After a config reload, we don't know the time to be run anymore, so run as soon as possible
//		setTimeWhenRunnable(TimeWhenRunnable.NOW); 
//	}

}

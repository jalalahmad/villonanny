package net.villonanny.strategy;

import java.util.EnumMap;
import java.util.NoSuchElementException;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.Field;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.ResourceTypeToFieldMap;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class RatedFieldGrowth extends Strategy {
	private final static Logger log = Logger.getLogger(RatedFieldGrowth.class);
	private Field candidate;

	public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
		// <strategy desc="RFG01" class="RatedFieldGrowth" enabled="true" maxLevel="9">
		//    <productionRate wood="10" clay="10" iron="10" crop="6" checkTimeMinutes="60"/>
		//    <whileProductionBelow wood="1000" clay="1000" crop="600"/>
		//	  <maxLevel iron="6" crop="5" />
		// </strategy>
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());

		try {
			if (!village.fieldQueueAvailable()) {
				String s = "Construction queue full for " + village.getDesc();
				log.debug(s);
				EventLog.log(s);
				return new TimeWhenRunnable(village
						.getFieldQueueAvailableTime());
			}
			EnumMap<ResourceType, Double> resourceDesiredRate = new EnumMap<ResourceType, Double>(ResourceType.class);
			int checkTimeMinutes = 0;

			// Get the resource rate from the configuratoin file
			try {
				resourceDesiredRate.put(ResourceType.WOOD, config.getDouble(
						"/productionRate/@wood", 10));
				resourceDesiredRate.put(ResourceType.CLAY, config.getDouble(
						"/productionRate/@clay", 12));
				resourceDesiredRate.put(ResourceType.IRON, config.getDouble(
						"/productionRate/@iron", 8));
				resourceDesiredRate.put(ResourceType.CROP, config.getDouble(
						"/productionRate/@crop", 6));
				checkTimeMinutes = config.getInt("/productionRate/@checkTimeMinutes", 60);
			} catch (NoSuchElementException nseE) {
				log.error("Strategy had a loading error; disabling", nseE);
				EventLog.log("Strategy had a loading error; disabling");
				return TimeWhenRunnable.NEVER;
			}
			
			//*********** fofo start
			ResourceTypeMap resourceRoof = new ResourceTypeMap();
			resourceRoof.put(ResourceType.WOOD, config.getInt("/keepProductionBelow/@wood", -1));
			resourceRoof.put(ResourceType.CLAY, config.getInt("/keepProductionBelow/@clay", -1));
			resourceRoof.put(ResourceType.IRON, config.getInt("/keepProductionBelow/@iron", -1));
			resourceRoof.put(ResourceType.CROP, config.getInt("/keepProductionBelow/@crop", -1));
			
			
			// log.debug("GAC Resource Available is "+village.getAvailableResources().toStringNoFood());
			// log.debug("GAC Resource Limit is "+resourceLimit.toStringNoFood());
			
			ResourceTypeMap resourceProduction = village.getProduction();
			
			/*
			 * gac original location of check
			*/
			// Check resourceLimit
			int roofCount=0;
			for (ResourceType resourceType : ResourceType.values()) {
				if (resourceType == ResourceType.FOOD) {
					continue;
				}
				if (resourceRoof.get(resourceType) >=0) {  //a negative number disables the check
					// gac test was > but called while below not below or equal so change test to be >= 
					if (resourceProduction.get(resourceType) >= resourceRoof.get(resourceType)) {
						// log.debug("Overproduction "+resourceType+". Exiting strategy");
						// return new TimeWhenRunnable(System.currentTimeMillis() + checkTimeMinutes  * Util.MILLI_MINUTE);
						// gac - Implement as simple or - Don't call it again
						resourceDesiredRate.put (resourceType, 0.0); //set the offending resource to zero, so we don't increase it
						roofCount++;
						if (roofCount >=4) { //every rate is above the limit, so end strategy if all 4 resourceDesiredRate is zero,
											 // strategy will disable itself further down anyway. fofo
							EventLog.log(resourceType+" Production Achieved - Strategy Finished");
							village.strategyDone.setFinished (this.getId(), true); //register it as done
							return TimeWhenRunnable.NEVER;
						} else {
							log.debug("Limiting Production of "+resourceType);
						}
					}
				}
			}
			//*********** fofo end
			ResourceTypeMap resourceLimit = new ResourceTypeMap();
			resourceLimit.put(ResourceType.WOOD, config.getInt("/whileProductionBelow/@wood", -1));
			resourceLimit.put(ResourceType.CLAY, config.getInt("/whileProductionBelow/@clay", -1));
			resourceLimit.put(ResourceType.IRON, config.getInt("/whileProductionBelow/@iron", -1));
			resourceLimit.put(ResourceType.CROP, config.getInt("/whileProductionBelow/@crop", -1));
			
			ResourceTypeToFieldMap candidatesPerType = village.getLowestFieldPerType();

			// log.debug("GAC Resource Available is "+village.getAvailableResources().toStringNoFood());
			// log.debug("GAC Resource Limit is "+resourceLimit.toStringNoFood());
			
			Field toSubmit = null;

			
			/*
			 * gac original location of check
			*/
			// Check resourceLimit
			for (ResourceType resourceType : ResourceType.values()) {
				if (resourceType == ResourceType.FOOD) {
					continue;
				}
				if (resourceLimit.get(resourceType) >=0) {  //a negative number disables the check
					// gac test was > but called while below not below or equal so change test to be >= 
					if (resourceProduction.get(resourceType) >= resourceLimit.get(resourceType)) {
						// log.debug("Overproduction "+resourceType+". Exiting strategy");
						// return new TimeWhenRunnable(System.currentTimeMillis() + checkTimeMinutes  * Util.MILLI_MINUTE);
						// gac - Implement as simple or - Don't call it again
						EventLog.log(resourceType+" Production Achieved - Strategy Finished");
						village.strategyDone.setFinished (this.getId(), true); //register it as done
						return TimeWhenRunnable.NEVER;
						// to make a simple "and" do not take action but set a flag instead 
						// overProduction = true;
					} else {
						// to make a simple "and" clear the flag if >=0 and this limit is still less
						// overProduction = false;
						// then separate test at end to finish
						// this will maintain production past while limit if more than 1 specified
					}
				}
			}
			
			double resourceProductionRate = 0;
			double resourceDesiredRateCalculated = 0;
			ResourceType higherEval = ResourceType.fromInt(0);
			// GAC was double higherDistance=Double.MIN_VALUE;
			double higherDistance=Double.NEGATIVE_INFINITY;


			// Calculate the desired and actual rate production
			double totResourceProduction = Math.max(1, resourceProduction.getSumOfValues());
			double totResourceDesiredRate = 
				resourceDesiredRate.get(ResourceType.WOOD) +
				resourceDesiredRate.get(ResourceType.CLAY) +
				resourceDesiredRate.get(ResourceType.IRON) +
				resourceDesiredRate.get(ResourceType.CROP);
			if (totResourceDesiredRate==0) {
				log.warn("Desired production rate is zero. Nothing to do. Disabling strategy");
				return TimeWhenRunnable.NEVER;
			}
			log.debug("Current resources: " + resourceProduction);
			EventLog.log("Resource prod rate is "+resourceProduction.toStringNoFood());
		
			
			// GAC modify to include a maxlevel check
			// Get the maximum level resource rate from the configuration file
			// first get any overall limit
			int maxLevel = config.getInt("/@maxLevel", 999);
			// next check for limit on individual resources - default is the overall max or very large impossible number if none
			EnumMap<ResourceType, Integer> resourceMaxLevel = new EnumMap<ResourceType, Integer>(ResourceType.class);
			try {
				resourceMaxLevel.put(ResourceType.WOOD, config.getInt("/maxLevel/@wood", maxLevel));
				resourceMaxLevel.put(ResourceType.CLAY, config.getInt("/maxLevel/@clay", maxLevel));
				resourceMaxLevel.put(ResourceType.IRON, config.getInt("/maxLevel/@iron", maxLevel));
				resourceMaxLevel.put(ResourceType.CROP, config.getInt("/maxLevel/@crop", maxLevel));
			} catch (NoSuchElementException nseE) {
				log.error("Strategy had a loading error; disabling", nseE);
				EventLog.log("Strategy had a loading error; disabling");
				return TimeWhenRunnable.NEVER;
			}
			// cannot set to null to check no match was found at all
			// but risk if all ideal then will not grow at all
			// so set to known type run loop - if type still clay at end check not max
			// TODO should this be from a growPriority field for case when two resource rates are equal
			higherEval = ResourceType.CLAY;
			
			for (ResourceType resourceType : ResourceType.values()) {
				if (resourceType == ResourceType.FOOD) {
					continue;
				}
				resourceProductionRate = ((double) resourceProduction.get(resourceType))
						/ totResourceProduction;
				resourceDesiredRateCalculated = resourceDesiredRate.get(resourceType)
						/ totResourceDesiredRate;
				// Calculate the diff to find the most distant from the rate desired
				double distance = resourceDesiredRateCalculated - resourceProductionRate;
				// GAC modify test to check if this candidate is at configurable maximum
				// also move limit test so it works in similar way as an and if complex and required
				// log.debug("check " + resourceType.name() + " distance " + distance + " >? " + higherDistance +				
				// " current " + candidatesPerType.get(resourceType).getCurrentLevel() + " <? " + resourceMaxLevel.get(resourceType));
				// if (distance > higherDistance) {
				if ((distance > higherDistance) &&
					((resourceLimit.get(resourceType) < 0) || (resourceProduction.get(resourceType) < resourceLimit.get(resourceType))) &&
					(candidatesPerType.get(resourceType).getCurrentLevel() < resourceMaxLevel.get(resourceType)) ) {
					higherDistance = distance;
					higherEval = resourceType;
				}
			}
			// store the one found most distant
			candidate = candidatesPerType.get(higherEval);
			// check if have not changed resource type
			if (higherEval == ResourceType.CLAY) {
				// may be at max which is not valid so check explicitly
				// valid if because it is the best candidate or all are equal so any is valid
				if (candidatesPerType.get(ResourceType.CLAY).getCurrentLevel() >= resourceMaxLevel.get(ResourceType.CLAY)) {
					// is at max so not valid
					candidate = null;
				}
			}
			
			// candidate may be null if all fields at max limit
			if (candidate!=null) {
				candidate.fetch(util); // Load resource information
				// log.debug("GAC Resource Needed "+candidate.getNeededResources().toStringNoFood());
				// log.debug("GAC candidate status "+candidate.toString() + " submittable " + candidate.isSubmittable());
			}

			// Check for food needs
			if ((candidate!=null) && candidate.getFoodShortage()) {
				log.debug("Food is low, must increase crop");
				candidate = candidatesPerType.get(ResourceType.CROP);
				candidate.fetch(util);
			}
			// check if have resources available
			if ((candidate!=null) && candidate.isUpgradeable() && candidate.isSubmittable()) {
				toSubmit = candidate;
			}

			if (toSubmit == null) {
				String s = "No fields can be upgraded now";
				log.info(s);
				EventLog.log(s);
				log.debug("best candidate: " + candidate);

				// Find the time at which we will have enough resources
				if (candidate == null || candidate.isMaxLevelReached()) {
					// Don't call it again
					EventLog.log("Fields At maxLevel - Finished");
					village.strategyDone.setFinished (this.getId(), true); //register it as done
					return TimeWhenRunnable.NEVER;
				}
				TimeWhenRunnable available = new TimeWhenRunnable(util
						.calcWhenAvailable(village.getProduction(), village
								.getAvailableResources(), candidate
								.getNeededResources()));
				EventLog.log("best candidate "+candidate.getName()+" at " + available.getDate());
				// check if time is now - problem with submit string?
				if (!available.after(TimeWhenRunnable.NOW)) {
					log.warn("Candidate for Growth Ready now - check submitUrl");
				}
				
				TimeWhenRunnable checkTime = new TimeWhenRunnable(System.currentTimeMillis() + checkTimeMinutes  * Util.MILLI_MINUTE);
				// Check if we have resource available is before next check
				if (available.before(checkTime)) {
					return available;
				} else {
					return checkTime;
				}
			}
			candidate = null; // Don't be triggered by resources
			village.gotoMainPage();
			String s = "Upgrading " + toSubmit.getName();
			EventLog.log(s);
			log.info(s);

			TimeWhenRunnable localCompletionTime = new TimeWhenRunnable(
					toSubmit.upgrade(util));
			log.info(String.format("Strategy %s done for now", getDesc()));
			return localCompletionTime;
		} finally {
			NDC.pop();
		}
	}

	public boolean modifiesResources() {
		return true;
	}
	
	/**
	 * Return the minimum resources needed to run, or null if not applicable
	 * @return
	 */
	public ResourceTypeMap getTriggeringResources() {
		return candidate!=null?candidate.getNeededResources():null;
	}
	
}

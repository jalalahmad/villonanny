package net.villonanny.strategy;

import java.util.Date;
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

public class FieldGrowth extends Strategy {
	private final static Logger log = Logger.getLogger(FieldGrowth.class);
	private Field candidate = null;

	public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());

		try {
			if (!village.fieldQueueAvailable()) {
				String s = "Construction queue full for " + village.getDesc();
				log.debug(s); // TODO col travian plus ci potrebbero essere + slot
				EventLog.log(s);
				return new TimeWhenRunnable(village.getFieldQueueAvailableTime());
			}
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
	

			String growCheapest = config.getString("/@growPriority", "cheapest");

			Field toSubmit = null; // The cheapest submittable field

			ResourceTypeToFieldMap candidatePerType = village.getLowestFieldPerType();
			
			ResourceType candidateType = null;
			// even if woken for resources recheck
			candidate = null;
			// GAC - check for priority field first
			if (growCheapest.toLowerCase().equals("clay")) {
				candidateType = ResourceType.CLAY;
			} else if (growCheapest.toLowerCase().equals("wood")) {
				candidateType = ResourceType.WOOD;
			} else if (growCheapest.toLowerCase().equals("iron")) {
				candidateType = ResourceType.IRON;
			} else if (growCheapest.toLowerCase().equals("crop")) {
				candidateType = ResourceType.CROP;
			}
			// check if one set
			if (candidateType != null) {
				// select as candidate
				village.gotoMainPage();
				candidate = candidatePerType.get(candidateType);
				// get field information 
				candidate.fetch(util);
				log.debug("Priority Candidate: "+candidate.toString());
				// check for food shortage
				if ((candidate != null) && candidate.getFoodShortage()) {
					candidate = candidatePerType.get(ResourceType.CROP);
					log.debug("Food Shortage Candidate: "+candidate.toString());
				}
				// check if submittable and < max
				if ((candidate.isSubmittable()) &&
					(candidate.getCurrentLevel() < resourceMaxLevel.get(candidateType)) ) {
					toSubmit = candidate;
				} else {
					// check rest - use least so build any can
					// growCheapest = "cheapest";
					growCheapest = "least";
					candidate = null;
				}
			}
			if ( toSubmit != null ) {
				// have already got a valid field to grow
			} else if (growCheapest.toLowerCase().equals("least")) {

				// Evaluate the best field to level
				ResourceTypeMap actualResources = this.village.getAvailableResources();

				double bestEval = Double.NEGATIVE_INFINITY;

				for (ResourceType resourceType : ResourceType.values()) {
					if (resourceType == ResourceType.FOOD) {
						continue;
					}
					// Calculate the time we need to produce to grow all fields
					double eval = -actualResources.get(resourceType);
					village.gotoMainPage();
					Field field = candidatePerType.get(resourceType);
					field.fetch(util);
					// log.debug("GAC least " + field.getName() + " max " + resourceMaxLevel.get(resourceType)) ;
					
					//if (eval > bestEval) {
					if ((eval > bestEval) &&
						(field.getCurrentLevel() < resourceMaxLevel.get(resourceType)) ) {
						candidate = field;

						if ((field != null) && field.getFoodShortage()) {
							candidate = candidatePerType.get(ResourceType.CROP);
							log.debug("Food Shortage Candidate: "+candidate.toString());
						}

						// Find submittable field, which might not be the cheapest
						if (candidate.isSubmittable()) {
							bestEval = eval;
							toSubmit = candidate;
							log.debug("Least candidate: " + candidate);
						}
					}
				}
			} else if (growCheapest.toLowerCase().equals("cheapest")) {
				for (Field field : candidatePerType.values()) {
					village.gotoMainPage();
					field.fetch(util);

					if ((field.getCurrentLevel() < resourceMaxLevel.get(field.getResourceType())) &&
						( (candidate == null) || (field.getTotResourcesNeeded() < candidate.getTotResourcesNeeded()) )  ) {
						// There must be enough food, or it must be crop
						if (!field.getFoodShortage() || field.isCrop()) {
							// It must be submittable
							if (!field.isMaxLevelReached()) {
								candidate = field;
							}
							// log.debug("Candidate Total Resources Needed "+ candidate.getTotResourcesNeeded());
						}
					}
					// original (toSubmit.getTotResourcesNeeded() < candidate.getTotResourcesNeeded()))
					if (candidate == null) {
						// no action as at max or short of food
					} else if (( (toSubmit == null) || (candidate.getTotResourcesNeeded() < toSubmit.getTotResourcesNeeded())) && candidate.isSubmittable() ) {
						toSubmit = candidate;
						log.debug("Cheapest Submittable: " + candidate.getName() + " needs " + candidate.getTotResourcesNeeded());
					}
				}
			} else if (growCheapest.toLowerCase().equals("earliest")) {
				// check earliest field
				Date nextRunningTime = Util.getIncrediblyLongTime();
				Date candidateAvailableTime = null;
				
				for (Field field : candidatePerType.values()) {
					village.gotoMainPage();
					field.fetch(util);

					candidateAvailableTime = util.calcWhenAvailable(village.getProduction(), village.getAvailableResources(), field.getNeededResources());
					log.debug(field + " available at " + candidateAvailableTime);
					
					// check if not at max
					if (field.getCurrentLevel() < resourceMaxLevel.get(field.getResourceType())) {
						// check if earlier - not as loop takes while to execute there may be a few seconds error 
						if (nextRunningTime.after(candidateAvailableTime))  {
							log.debug("Earlier candidate: " + field.getName());
							// this one is earlier
							nextRunningTime = candidateAvailableTime;
							if ((field != null) && field.getFoodShortage()) {
								candidate = candidatePerType.get(ResourceType.CROP);
								log.debug(field.getName()+" Food Shortage Candidate now: "+candidate.getName());
							} else {
								candidate = field;								
							}
						}
						// check for submittable field - use url but time should be 0 as well
						// guards where all can be done now but takes time to process and last one is the only submittable due to other problem
						if (field.isSubmittable()) {
							toSubmit = field;
							log.debug("Submittable: " + field.getName());
						}
					}
				}
				// check if real candidate is submittable field - use url but time should be 0 as well
				if ((toSubmit != candidate) && candidate.isSubmittable()) {
					toSubmit = candidate;
					log.debug("Earliest candidate ready: " + candidate);
				}				
			} else {
				String s = "Invalid configuration; disabling strategy.";
				log.debug(s);
				EventLog.log(s);
				return TimeWhenRunnable.NEVER;
			}
			if (toSubmit == null) {
				/* 
				String s = "No fields can be upgraded now";
				log.info(s);
				EventLog.log(s);
				if (log.isDebugEnabled()) {
					log.debug("Fields: ");
					for (Field item : village.getFields()) {
						log.debug(item);
					}
					log.debug("Candidates: ");
					for (Field field : candidatePerType.values()) {
						log.debug(field);
					}
				} */
				// Find the time at which we will have enough resources
				if ((candidate == null) || (candidate.isMaxLevelReached()) ) {
					// Don't call it again
					EventLog.log("evt.disabled", this.getClass());
					village.strategyDone.setFinished (this.getId(), true); //register it as done			
					return TimeWhenRunnable.NEVER;
				} else {
					log.debug("No fields can be upgraded now - best candidate was: " + candidate);
				}

				Date nextRunningTime = util.calcWhenAvailable(village.getProduction(), village.getAvailableResources(), candidate.getNeededResources());
				/* gac - selecting earlier conflicts with growPriority="cheapest" which will not build it
				 * so just commenting out for the moment
				Date candidateAvailableTime = null;

				for (Field field : candidatePerType.values()) {
					candidateAvailableTime = util.calcWhenAvailable(village.getProduction(), village.getAvailableResources(), field.getNeededResources());
					if (nextRunningTime.after(candidateAvailableTime)) {
						nextRunningTime = candidateAvailableTime;
						// possible changes selected candidate as may be earlier for individual resource than total or least 
						candidate = field;
						log.debug("Earliest candidate: " + candidate);
					}
					// log.debug(field);
				}
				 */
				// check if time is now - problem with submit string?
				Date now = new Date(); // now
				if (!nextRunningTime.after(now)) {
					log.warn("Candidate for FieldGrowth Ready at "+nextRunningTime+" < now - check submitUrl for "+candidate);
				}
				return new TimeWhenRunnable(nextRunningTime);
			}
			candidate = null; // Don't be triggered by resources
			String s = "Upgrading " + toSubmit.getName();
			EventLog.log(s);
			log.info(s);
			// gac uncomment so dont build if checking logic
			// System.exit(0);
			village.gotoMainPage();
			// Simulate return to field page
			util.httpGetPage(toSubmit.getUrlString());
			// Upgrade
			String page = util.httpGetPage(toSubmit.getSubmitUrlString());
			// Find completion time
			Date localCompletionTime = util.getCompletionTime(toSubmit.getCompletionTimeString(util, page));
			log.info(String.format("Strategy %s done for now", getDesc()));
			return new TimeWhenRunnable(localCompletionTime);
		} finally {
			NDC.pop();
		}
	}

	public boolean modifiesResources() {
		return true;
	}

	/**
	 * Return the minimum resources needed to run, or null if not applicable
	 * 
	 * @return
	 */
	public ResourceTypeMap getTriggeringResources() {
		return candidate != null ? candidate.getNeededResources() : null;
	}

}

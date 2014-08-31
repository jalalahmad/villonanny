package net.villonanny.strategy;

import java.util.Collection;
import java.util.Date;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.Building;
import net.villonanny.entity.EmptySite;
import net.villonanny.entity.UpgradeableSite;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class CapacityIncreaser extends Strategy {

	private final static Logger log = Logger.getLogger(CapacityIncreaser.class);

	public TimeWhenRunnable execute() throws ConversationException {
		// <strategy class="CapacityIncreaser" enabled="true">
		// <warehouse id="19" overflowTime="6" />
		// <granary id="24" overflowTime="6" />
		// </strategy>
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());

		try {
			if (!village.buildingQueueAvailable()) {
				String s = "Construction queue full for " + village.getDesc();
				log.debug(s);
				EventLog.log(s);
				return new TimeWhenRunnable(village.getBuildingQueueAvailableTime());
			}
			double warehouseOverflowTime = config.getDouble("/warehouse/@overflowTime", Double.MAX_VALUE);
			double granaryOverflowTime = config.getDouble("/granary/@overflowTime", Double.MAX_VALUE);

			ResourceTypeMap resourceProduction = village.getProduction();
			ResourceTypeMap resourceAvailable = village.getAvailableResources();
			ResourceTypeMap resourceCapacity = village.getMaxResources();

			boolean needWarehouse = (resourceCapacity.getWood() - resourceAvailable.getWood())
					/ resourceProduction.getWood() < warehouseOverflowTime
					|| (resourceCapacity.getClay() - resourceAvailable.getClay())
							/ resourceProduction.getClay() < warehouseOverflowTime
					|| (resourceCapacity.getIron() - resourceAvailable.getIron())
							/ resourceProduction.getIron() < warehouseOverflowTime;
			boolean needGranary = (resourceCapacity.getCrop() - resourceAvailable.getCrop())
					/ Math.max(resourceProduction.getCrop(), 1) < granaryOverflowTime;

			Building toSubmit = null;

			if (needWarehouse) {
				toSubmit = findBuildingToUpgrade(BuildingType.WAREHOUSE, config.getString("/warehouse/@id", null));
			} else if (needGranary) {
				toSubmit = findBuildingToUpgrade(BuildingType.GRANARY, config.getString("/granary/@id", null));
			} else {
				String s = "No need to increase capacity now";
				log.info(s);
				EventLog.log(s);
				return new TimeWhenRunnable(System.currentTimeMillis() + Util.MILLI_HOUR);
			}
			if (toSubmit==null || !toSubmit.isSubmittable()) {
				String s = "Can't increase capacity now";
				EventLog.log(s);
				return new TimeWhenRunnable(System.currentTimeMillis() + Util.MILLI_HOUR);
			}
			village.gotoSecondPage();
			String s = "Upgrading " + toSubmit.getName();
			EventLog.log(s);
			Date localCompletionTime = toSubmit.upgrade(util);
			log.info(String.format("Strategy %s done for now", getDesc()));
			return new TimeWhenRunnable(localCompletionTime);
		} finally {
			NDC.pop();
		}
	}

	private Building findBuildingToUpgrade(BuildingType buildingType, String slotId) throws ConversationException {
		Building result = null;
		Collection<Building> buildings = village.getBuildings();
		// Search for the lower level item
		Building lowestItem = null;
		for (Building item : buildings) {
			if (item.getType() == buildingType	&& item.getCurrentLevel() < 20) {
				if (lowestItem == null || item.getCurrentLevel() < lowestItem.getCurrentLevel()) {
					lowestItem = item;
				}
			}
		}
		if (lowestItem == null) {
			// No bulding below level 20
			EmptySite emptySite = null;
			UpgradeableSite site = village.getItem(slotId);
			if (site instanceof EmptySite) {
				emptySite = (EmptySite) site;
				emptySite.fetch(util);
				emptySite.setDesiredBuildingType(buildingType);
				result = emptySite;
			} else {
				String msg = "Non an empty slot at id=" + (slotId==null?"unset":slotId);
				EventLog.log(msg);
			}
		} else {
			village.gotoSecondPage();
			lowestItem.fetch(util);
			if (lowestItem.isUpgradeable() && lowestItem.isSubmittable()) {
				result = lowestItem;
			}
		}
		return result;
	}
}

package net.villonanny.strategy;

import java.util.List;
import java.util.NoSuchElementException;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.AcademySite;
import net.villonanny.entity.UpgradeSite;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.TroopType;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 * 	Upgrade Troop Armour or Weapons
 */
public class ResearchTroop extends Strategy {

	private final static Logger log = Logger.getLogger(TroopMaker.class);

	public TimeWhenRunnable execute() throws ConversationException {
//     <strategy desc="Upgrade" class="ResearchTroop" enabled="true" uid="scomxV3ut">
//        <keepResources wood="0" clay="0" iron="0" crop="0" />
//        <troops type="Phalanx"  delayMinutes="10" />
//     </strategy>
//		Researches the troop type in academy if possible.
//		Strategy will not know of a troop type is missing in academy because of a missing prerequisite
//		or because its already researched, so it will exit when all possible researches are done
//		next todo here is to somehow check wether prerequisite missing, or troop researched. fofo
		long myTimeToRun=Long.MAX_VALUE;
		long newTime=0;
		int sumTime=0;
		int level=0;
		int maxLevel=0;
		boolean debugg=true;
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());

		try {
			ResourceTypeMap resourcesToKeep = new ResourceTypeMap();
			resourcesToKeep.put(ResourceType.WOOD, config.getInt("/keepResources/@wood", 0)); 
			resourcesToKeep.put(ResourceType.CLAY, config.getInt("/keepResources/@clay", 0));
			resourcesToKeep.put(ResourceType.IRON, config.getInt("/keepResources/@iron", 0)); 
			resourcesToKeep.put(ResourceType.CROP, config.getInt("/keepResources/@crop", 0));
			ResourceTypeMap available_res = village.getAvailableResources();
			int buildDelayMinutes = 0;
				int count = 0;
				int maxed = 0;
				if (debugg) EventLog.log("Starting TroopResearch");
								
				List<SubnodeConfiguration> troopsNodes = super.config.configurationsAt("/troops");
				long nextTime=0;
				boolean doneFlag=false;
				boolean sFlag=false;
				village.gotoMainPage();
				for (SubnodeConfiguration troopsNode : troopsNodes) {
					if (doneFlag)
						continue;
					if (debugg) EventLog.log("In researchTroop for loop");
					String type = troopsNode.getString("/@type");
					String fullkey = util.getTranslator().getKeyword(type); // romans.troop1
					String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
					if (debugg) EventLog.log("Troop type: "+fullkey+" typekey: "+typeKey);
					TroopType troopType = TroopType.fromString(typeKey);
					
					long twr=0;
					AcademySite academy=null;
					academy = (AcademySite) village.getBuildingMap().getOne(BuildingType.ACADEMY);
					if (academy == null) {
						EventLog.log("Cant not find academy");
						continue;
					}
					academy.researchFetch(util); //changed from .fetch to avoid replacing UpgradeableSite.fetch. fofo
					if (academy.getTimeToReady()!=0.0) {
						doneFlag=true;
						sFlag=true;
						int hrs, min, sec;
						double ttt;
						ttt=academy.getTimeToReady();
						hrs=(int)ttt;
						min=(int)((ttt-(double)hrs)*60);
						sec=(int)((ttt-(double)hrs-(double)min/(double)60)*(double)60*(double)60);
						sumTime=hrs*60+min+buildDelayMinutes+sec/60;
						EventLog.log("Academy site busy. Ready in "+Integer.toString(hrs)+":"+
																   Integer.toString(min)+":"+
																   Integer.toString(sec));
						continue;
					}
					ResourceTypeMap troopCost= academy.getTroopCost(troopType);
					count++;
					if (troopCost == null) {
						EventLog.log("No Research Cost found for " + troopType);
						maxed++;
						continue;
					}
					if (debugg) EventLog.log("Looking for resources");
					boolean enoughResources=true;
					for (ResourceType resourceType : ResourceType.values()) {
						if (resourceType == ResourceType.FOOD) {
							continue;
						}
						if ((resourcesToKeep.get(resourceType)+troopCost.get(resourceType))>=available_res.get(resourceType)){
							enoughResources=false;
							log.info("Not enough "+resourceType+
									" Need "+(resourcesToKeep.get(resourceType)+troopCost.get(resourceType))+
									" Got "+available_res.get(resourceType));
						}
					}
					if (debugg) EventLog.log("ResearchTroop check if upgradable");
					if ((academy.getTimeToReady()==0.0)&&(enoughResources)) {  //Nothing in upgrade queue actually + troop type upgradable...
						twr = academy.researchTroop (util,troopType); 
						doneFlag=true;
						if ((twr>0)&&(twr<nextTime))
							nextTime=twr;
					}
				}
			if (doneFlag==true && sFlag==false)
				village.update();
			if (count==maxed && sFlag==false) {
				EventLog.log(String.format("Strategy %s disabled.", getDesc()));
				village.strategyDone.setFinished (this.getId(), true);
				return TimeWhenRunnable.NEVER;
			} else if (count==maxed && sFlag==true) {
				newTime = System.currentTimeMillis() + sumTime * Util.MILLI_MINUTE;
				return new TimeWhenRunnable (newTime);
			} else {
				buildDelayMinutes = config.getInt("/@minPauseMinutes", 30 );
				myTimeToRun = System.currentTimeMillis() + buildDelayMinutes * Util.MILLI_MINUTE;
				return new TimeWhenRunnable (myTimeToRun);
			}
		} finally {
			NDC.pop();
		}
	}

	public boolean modifiesResources() {
		return false;
	}

	// public String createId(SubnodeConfiguration strategyConfig) {
	// StringBuffer result = new
	// StringBuffer(super.createId(strategyConfig));
	// // Target coordinates uniquely identify this strategy instance
	// String x = super.config.getString("/target/@x");
	// String y = super.config.getString("/target/@y");
	// result.append("#").append(x).append("#").append(y);
	// return result.toString();
	// }
}

package net.villonanny.strategy;

import java.util.List;
import java.util.NoSuchElementException;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
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
public class UpgradeTroop extends Strategy {

	private final static Logger log = Logger.getLogger(TroopMaker.class);

	public TimeWhenRunnable execute() throws ConversationException {
//     <strategy desc="Upgrade" class="UpgradeTroop" enabled="true" uid="scomxV1ut">
//        <keepResources wood="0" clay="0" iron="0" crop="0" />
//        <troops type="Phalanx" armor="true" maxLevel="20" delayMinutes="10" />
//     </strategy>

//     <strategy desc="Upgrade" class="UpgradeTroop" enabled="true" uid="scomxV2ut">
//		  <keepResources wood="0" clay="0" iron="0" crop="0"/>
//        <troops type="Swordsman" weapon="true" maxLevel="10" delayMinutes="10" />
//     </strategy>

		
		long myTimeToRun=Long.MAX_VALUE;
		long newTime=0;
		int sumTime=0;
		int level=0;
		int maxLevel=0;
		boolean debugg=false;
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
				if (debugg) EventLog.log("Starting TroopUpgrade");
								
				List<SubnodeConfiguration> troopsNodes = super.config.configurationsAt("/troops");
				long nextTime=0;
				boolean doneFlag=false;
				boolean sFlag=false;
				for (SubnodeConfiguration troopsNode : troopsNodes) {
					if (doneFlag)
						continue;
					if (debugg) EventLog.log("In upgradeArmor for loop");
					String type = troopsNode.getString("/@type");
					String fullkey = util.getTranslator().getKeyword(type); // romans.troop1
					String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
					if (debugg) EventLog.log("Troop type: "+fullkey+" typekey: "+typeKey);
					TroopType troopType = TroopType.fromString(typeKey);
					
					try {
						maxLevel = troopsNode.getInt("/@maxLevel",0);
						buildDelayMinutes = troopsNode.getInt("/@minPauseMinutes", troopsNode.getInt("/@delayMinutes", 0));
					} catch (NoSuchElementException nseE) {
						// just ignore continue; skips to end of loop
					}
					if (maxLevel <= 0) {
						continue;
					}
					boolean armor=troopsNode.getBoolean("/@armour",troopsNode.getBoolean("/@armor",false));
					boolean weapon=troopsNode.getBoolean("/@weapon",false);
					if (debugg) EventLog.log("maxlevel= "+Integer.toString(maxLevel)+" armor= "+Boolean.toString(armor)+" weapon= "+Boolean.toString(weapon));
					long twr=0;
					UpgradeSite armory=null;
					if (armor) {
						armory = (UpgradeSite) village.getBuildingMap().getOne(BuildingType.ARMOURY);
						if (armory == null) {
							EventLog.log("Armour upgrade site not found!");
							continue;
						}
					}
					if (weapon) {
						armory = (UpgradeSite) village.getBuildingMap().getOne(BuildingType.BLACKSMITH);
						if (armory == null) {
							EventLog.log("Weapon upgrade site not found!");
							continue;
						}
					}
					if (armory == null) {
						EventLog.log("No upgrade types set");
						continue;
					}
					village.gotoMainPage();
					armory.fetch(util);
					if (armory.getTimeToReady()!=0.0) {
						doneFlag=true;
						sFlag=true;
						int hrs, min, sec;
						double ttt;
						ttt=armory.getTimeToReady();
						hrs=(int)ttt;
						min=(int)((ttt-(double)hrs)*60);
						sec=(int)((ttt-(double)hrs-(double)min/(double)60)*(double)60*(double)60);
						sumTime=hrs*60+min+buildDelayMinutes+sec/60;
						EventLog.log("Upgrade site busy. Ready in "+Integer.toString(hrs)+":"+
																   Integer.toString(min)+":"+
																   Integer.toString(sec));
						continue;
					}
					ResourceTypeMap troopCost= armory.getTroopCost(troopType);
					if (troopCost == null) {
						EventLog.log("No Upgrade Cost found for " + troopType);
						continue;
					}
					level=armory.getCurrentLevel(troopType);
					EventLog.log("Current level for "+type+" is "+Integer.toString(level)+". Wanted level for "+type+" is "+Integer.toString(maxLevel));
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
					count++;
					if (level==maxLevel)
						maxed++;
					if ((armory.getTimeToReady()==0.0)&&(enoughResources)&&(level<maxLevel)) {  //Nothing in upgrade queue actually + troop type upgradable...
						twr = armory.upgradeTroop (util,troopType); //...in this case it will return "0" everytime
						doneFlag=true;
						if ((twr>0)&&(twr<nextTime))
							nextTime=twr;
					}
				}
			if (doneFlag==true && sFlag==false)
				village.update();
			if (count==maxed && sFlag==false) {
				EventLog.log(String.format("Strategy %s disabled.", getDesc()));
				return TimeWhenRunnable.NEVER;
			} else if (count==maxed && sFlag==true) {
				newTime = System.currentTimeMillis() + sumTime * Util.MILLI_MINUTE;
				return new TimeWhenRunnable (newTime);
			} else {
				buildDelayMinutes = config.getInt("/@minPauseMinutes", buildDelayMinutes );
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

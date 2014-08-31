package net.villonanny.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.NoSuchElementException;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.RallyPoint;
import net.villonanny.entity.TrainerSite;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.TribeType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class TroopMaker extends Strategy {

	private final static Logger log = Logger.getLogger(TroopMaker.class);
	
	//@SuppressWarnings("unchecked")
	public TimeWhenRunnable execute() throws ConversationException {
		// <strategy desc="TM01" class="TroopMaker" enabled="true" minPauseMinutes="60">
		// <keepResources wood="" clay="" iron="" crop=""/>
		// <minProductionRate wood="0" clay="0" iron="0" crop="0"  sets minimum required production for troops to be made default 0, always enough
		// <troops type="Praetorians" maxQueueHours="4" desc="Defence" percent="PERCENT OF MAX AVAILABLE TROOPS TO TRAIN" great="true">50</troops>
		// <troops type="Paladins" maxQueueHours="2" desc="Cavalry" maxTroops="3" endAfterMax="false" >20</troops>
		// </strategy>
		// note that percent calculates again after the previous troop have been trained
		//so percent 33,50,100 in that order will train 33% of each 3 diff troops
		// maxTroops gets sendable troops fromRallyPoint, and limits stops making new troops when maxTroops is reached. whatever is in the queue will still be made of course
		// endAfterMax=true sets TimeWhenRunnable=NEVER and registers done in StrategyStatus when maxTroops have been reached for all of the troops types
		
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());
		try {
/*
			String StrictTime = super.config.getString("/@stricttime", "true");
			if (StrictTime.equals("true")){                          //if stricttime is true (default) keep strictly to the scheduled time				   
				long curentTimeMillis = System.currentTimeMillis();
				double tempDou3 = (double) ( MyTimeToRun - curentTimeMillis)/60000;
				if ((MyTimeToRun != 0) && (MyTimeToRun > (curentTimeMillis+20000)))  {  //add 20 sec to current time as margin
					EventLog.log(String.format("Aborting. minutes to next run is: %s ", Double.toString (tempDou3)));
					return new TimeWhenRunnable (MyTimeToRun);  // just return the old TimeWhenRunnable if called prematurely
				}
			}
*/

			EnumMap<ResourceType, Double> resourceLimit = new EnumMap<ResourceType, Double>(ResourceType.class);
			//deleted productionRate keyword. if anyone still uses it after 2 years, its time to change. fofo
			resourceLimit.put(ResourceType.WOOD, config.getDouble("/minProductionRate/@wood", 0));
			resourceLimit.put(ResourceType.CLAY, config.getDouble("/minProductionRate/@clay", 0));
			resourceLimit.put(ResourceType.IRON, config.getDouble("/minProductionRate/@iron", 0));
			resourceLimit.put(ResourceType.CROP, config.getDouble("/minProductionRate/@crop", 0));
			boolean EnoughProduction=true;
			if ((resourceLimit.get(ResourceType.WOOD) > 0) || (resourceLimit.get(ResourceType.CLAY) > 0)||
					(resourceLimit.get(ResourceType.IRON) > 0) || (resourceLimit.get(ResourceType.CROP) > 0)) {
				ResourceTypeMap resourceProduction = village.getProduction();
				for (ResourceType resourceType : ResourceType.values()) {
					if (resourceType == ResourceType.FOOD) {
						continue;
					}
					if ((double) resourceProduction.get(resourceType) < resourceLimit.get(resourceType)){
						EventLog.log("Not enough production "+resourceType);
						EnoughProduction=false;
					}
				}
				
			}
			int buildDelayMinutes = 0; // For backwards compatbility (to be removed in the future)
			if (EnoughProduction) {
				int troopsTrained = 0;
				EventLog.log("Starting TroopMaker");
				/*
				TrainerSite barracks = (TrainerSite) village.getBuildingMap().getOne(BuildingType.BARRACKS);
				TrainerSite stable = (TrainerSite) village.getBuildingMap().getOne(BuildingType.STABLE);
				TrainerSite workshop = (TrainerSite) village.getBuildingMap().getOne(BuildingType.WORKSHOP);
				*/				
				List<SubnodeConfiguration> troopsNodes = super.config.configurationsAt("/troops");
				RallyPoint rallyPoint = village.getRallyPoint();
				boolean endAfterMax = config.getBoolean("/@endAfterMax", false);
				int maxCountTotal=0;
				int maxCountChecked=0;
				int troopListCount=0;
				boolean maxTroopsSet=false;
		        for (SubnodeConfiguration troopsNode : troopsNodes) { 
		        	troopListCount++;
					Integer maxTroops = troopsNode.getInteger("/@maxTroops", 0);
					String type = troopsNode.getString("/@type");
					log.debug("Make max "+maxTroops+" of type "+type);
					if (maxTroops>0) {
						maxTroopsSet=true;
						maxCountTotal++;
					}
				}
		        TroopTypeMap haveTropsInVillage = new TroopTypeMap();
				if (rallyPoint != null) {
					if (maxTroopsSet) {
						haveTropsInVillage = rallyPoint.fetchSendableTroops(super.util, false);
						if (haveTropsInVillage == null)
							log.debug(this.getDesc()+" haveTropsInVillage = null");
					} else {
						log.debug(this.getDesc()+" rallypoint is null");
					}
				}
				TrainerSite trainer = null;
				int previousBuilding = -1;
				for (SubnodeConfiguration troopsNode : troopsNodes) {
					log.debug("In TroopMaker for loop");
					String type = troopsNode.getString("/@type");
					// Look for the type in the current tribe section of the configuration, to avoid mistakes
					TribeType tribeType = util.getTribeType();
					String typeKey = util.getTranslator().getKeyword(type, tribeType.toString()); // troop1
					log.debug("Troop type: key." + tribeType.toString() + "." + typeKey + " = " + type);
					TroopType troopType = TroopType.fromString(typeKey);
					Integer maxTroops = troopsNode.getInteger("/@maxTroops", -1);
					if (troopType==null) {
						log.error("Not a valid troop type (ignored): " + type);
						continue;
					}
					int train = 0;
					try {
						train = troopsNode.getInt("/");
						buildDelayMinutes = troopsNode.getInt("/@delayMinutes", 45); // For backwards compatbility (to be removed in the future)
					} catch (NoSuchElementException nseE) {
						continue;
					}
					buildDelayMinutes = troopsNode.getInt("/@delayMinutes", 45); // For backwards compatbility (to be removed in the future)
					log.trace ("want to train "+Integer.toString (train)+" of type "+type );
					if (train <= 0) {
						continue;
					}
					double maxQueue=troopsNode.getDouble("/@maxQueueHours", -1);
					//trainer = null;
					BuildingType building = troopType.getBuilding(util.getTribeType());
					// log.debug("Building "+type+" "+building.toString());
					// GAC check for great version
					if ( troopsNode.getBoolean("/@great", false) ) {
						building = troopType.getAlternativeBuilding(util.getTribeType());						
						// log.debug("Building Great "+type+" "+building.toString());
					}
					if (previousBuilding!=-1 && trainer !=null) {
						if (building.ordinal() != previousBuilding) {
							log.debug("Different trainersite, setting trainersite from building");
							trainer = (TrainerSite) village.getBuildingMap().getOne(building); //only change trainer if its a new building
						} else {
							log.debug("keeping trainersite from previous run");
						}
					} else {//previousBuilding==null
						log.debug("prev or trainer is null, setting trainersite from building");
						trainer = (TrainerSite) village.getBuildingMap().getOne(building); //only change trainer if its a new building
					}
					previousBuilding=building.ordinal();
					/*
					if (building == BuildingType.BARRACKS) {
						trainer = barracks;
					} else if (building == BuildingType.STABLE) {
						trainer = stable;
					} else if (building == BuildingType.WORKSHOP) {
						trainer = workshop;
					} else if (building == BuildingType.RESIDENCE || building == BuildingType.HEROS_MANSION) {
						log.error("This troop can't be trained by the current version of VilloNanny: " + type);
						continue;
					}
					*/
					// log.debug("Trainer "+type+" "+trainer);
					if (trainer == null) {
						log.error("Training site not found for " + type);
						continue;
					}
					// update troop cost and max information
					//village.gotoMainPage();
					trainer.trainerFetch(util);
					endAfterMax = troopsNode.getBoolean("/@endAfterMax", endAfterMax);
					log.debug("TroopMaker. queue lenght is"+trainer.getTrainerQueueHours()+" queue limit is "+maxQueue);
					if (trainer.getTrainerQueueHours()>maxQueue) { 
						//log.info(String.format("Strategy %s done for now", getDesc()));
						EventLog.log("Too long queue, "+trainer.getTrainerQueueHours()+" Hours");
						continue;
						//int minPauseMinutes = config.getInt("/@minPauseMinutes", buildDelayMinutes);
						//long myTimeToRun = System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE;
						//return new TimeWhenRunnable (myTimeToRun);
					}
					int inTraining=trainer.getTypeInTraining(troopType);
					ResourceTypeMap unitCost = new ResourceTypeMap();
					if (maxTroopsSet==true) {
						log.debug("maxCountTotal:"+maxCountTotal+" maxCountChecked:"+maxCountChecked);
						if (haveTropsInVillage!=null) {
							int inVillage=haveTropsInVillage.get(troopType);
							log.debug("Have "+inVillage+" Troops in village, "+inTraining+" in training, maxTroops is "+maxTroops);
							log.debug("maxCountChecked "+maxCountChecked+" maxCountTotal "+maxCountTotal+" endAfterMax:"+endAfterMax);
							if (((inVillage+inTraining) >= maxTroops) && (maxTroops>0)) { // we have enough troops of that type
								maxCountChecked++;
								if ((maxCountChecked >= maxCountTotal) && (endAfterMax==true)) {
									village.strategyDone.setFinished (this.getId(), true);
									EventLog.log ("Max number of troops reached, ending and disabling "+this.getDesc());
									return new TimeWhenRunnable (false);
								} else {
									continue;
								}
							}
						}
					}
					// check for keep resources
					// leave original check for moment - check if anyone uses?
					//I seriously doubt it. I used it when I first made this, when I didnt know how to 
					//get it from trainersite. commenting away may 25-2010 for deletion at will. fofo
					/*
					unitCost.put(ResourceType.WOOD, troopsNode.getInt("/@woodcost", 0));
					unitCost.put(ResourceType.CLAY, troopsNode.getInt("/@claycost", 0));
					unitCost.put(ResourceType.IRON, troopsNode.getInt("/@ironcost", 0));
					unitCost.put(ResourceType.CROP, troopsNode.getInt("/@cropcost", 0));
					woodcost = unitCost.get(ResourceType.WOOD);
					claycost = unitCost.get(ResourceType.CLAY);
					ironcost = unitCost.get(ResourceType.IRON);
					cropcost = unitCost.get(ResourceType.CROP);
					// if not using config, use values from trainer site - note different if great barracks/stable
					int canTrain=0;
					
					if ((woodcost+claycost+ironcost+cropcost) == 0) {
						unitCost = trainer.getTroopCost(troopType);
					}
					*/
					unitCost = trainer.getTroopCost(troopType);
					ResourceTypeMap keep = new ResourceTypeMap();
					keep.put(ResourceType.WOOD, config.getInt("/keepResources/@wood", 0));
					keep.put(ResourceType.CLAY, config.getInt("/keepResources/@clay", 0));
					keep.put(ResourceType.IRON, config.getInt("/keepResources/@iron", 0));
					keep.put(ResourceType.CROP, config.getInt("/keepResources/@crop", 0));
					int canTrain=0;
					int maxTrain=Integer.MAX_VALUE;
					log.debug ("want to train "+Integer.toString (train)+" of type "+type );
					if (unitCost != null) { //avoid nullpointer exception if trying to train a troop that is not researched yet 
						ResourceType resLimit = null;
						for (ResourceType resourceType : ResourceType.values()) {
							if (resourceType == ResourceType.FOOD) {
								continue;
							}
							if (unitCost.get(resourceType) > 0) {
								canTrain = (trainer.getAvailableResources(util).get(resourceType)-keep.get(resourceType)) / unitCost.get(resourceType);
								if (canTrain < 0) {
									maxTrain=0;
									train = 0;
								} else {
									if (canTrain < train) {
									train = canTrain;
									}
									if (canTrain < maxTrain) {
										maxTrain = canTrain;
										}
								}
							}
							log.debug(resourceType.name()
									+ " have "
									+ trainer.getAvailableResources(util).get(
											resourceType) + " keep "
									+ keep.get(resourceType) + " cost "
									+ unitCost.get(resourceType) + " training "
									+ train);
							// if hit 0 remember first shortage and exit loop
							if (train == 0) {
								resLimit = resourceType;
								break;
							}
						}
						// no point going on with this troop type with no troops to train
						if (train == 0) {
							EventLog.log("Not Enough "+resLimit.name()+" for "+type);
							continue;
						}
					}
					else {
						train=0;
						EventLog.log ("Cant find cost for "+type);
					}
					if (((unitCost==null) || unitCost.getSumOfValues()==0) && //no unit cost found
							(troopType==TroopType.TROOP10) &&                 //unit is settler
							(endAfterMax==true)) {                            //quit strategy when troops are made
						village.strategyDone.setFinished (this.getId(), true); //end strategy when the last settler is started. fofo
						EventLog.log ("Last settler is started "+this.getDesc());
						return new TimeWhenRunnable (false);

					}
					if (haveTropsInVillage != null) {
						log.debug(this.getDesc()+" have "+haveTropsInVillage.get(troopType)+ " troops of type"+type+" trooplimit is "+maxTroops);
					}
					log.debug ("after keepresources, train "+Integer.toString (train)+" of type "+type );
					double percent;
					int percentTrain=0;
					percent = troopsNode.getDouble("/@percent", 0.0);
					if ((percent >0.0)&& (canTrain>0)) {  //percent holds the percentage of max possible troops to train, 
						percentTrain=(int)Math.round(percent/(double)100*(double)maxTrain); //train is max number of troops to train
						if (percentTrain<train)
							train=percentTrain;
						EventLog.log ("train "+Integer.toString((int) percent)+" percent equals "+Integer.toString (train)+" of type "+type );
					}
					// Deleted original code since the above does the same job more elegantly. fofo
					
					if (train==0) { //no point going on with no troops to train
						EventLog.log("Too Small Percentage "+percent+" for "+type);
						continue;
					}
					
					// check max available and also some left
					Integer trainable = trainer.getTroopTrainMax(troopType);
					if (trainable == null || trainable.intValue() <= 0) {
						log.debug("trainable = " + trainable + "of type" + troopType.name());
						continue;
					}
					troopsTrained++;
					log.trace ("available resources before training"+trainer.getAvailableResources(util).toStringNoFood());
					EventLog.log ("Training "+train+" "+troopType);
					trainer.trainTroop(util, troopType, train, false, -1.0); //set quick to false after reuse of pages in trainerSite. fofo
					log.trace ("available resources  after training"+trainer.getAvailableResources(util).toStringNoFood());
					
					trainer.trainerFetch(util); //update data in trainer. page should be refreshed from the post in trainTroop. fofo
					                     //or from the fetch above
					//village.update(); // not needed with setAvailableResources from TrainerSite
				}

				if (troopsTrained == 0) {
					EventLog.log("No troop trained");
				}

			}
			// For backwards compatibility, if "minPauseMinutes" is not defined then revert to old algorithm with "delayMinutes" (to be removed in the future)
			int minPauseMinutes = config.getInt("/@minPauseMinutes", buildDelayMinutes);
			log.info(String.format("Strategy %s done for now", getDesc()));
			long myTimeToRun = System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE;
			return new TimeWhenRunnable (myTimeToRun);
		} finally {
			NDC.pop();
		}
	}

	public boolean modifiesResources() {
		return true;
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
/*				
//****** Start weighted troop making
				TroopTypeMap wantToTrain = new TroopTypeMap();
				for (SubnodeConfiguration troopsNode : troopsNodes) { //read all the troop names, and number to train
					String type = troopsNode.getString("/@type");     //and store in wantToTrain
					// Look for the type in the current tribe section of the configuration, to avoid mistakes
					TribeType tribeType = util.getTribeType();
					String typeKey = util.getTranslator().getKeyword(type, tribeType.toString()); // troop1
					log.debug("Troop type: key." + tribeType.toString() + "." + typeKey + " = " + type);
					TroopType troopType = TroopType.fromString(typeKey);
					if (troopType==null) {
						log.error("Not a valid troop type (ignored): " + type);
						continue;
					}
					int train = 0;
					try {
						train = troopsNode.getInt("/");
						buildDelayMinutes = troopsNode.getInt("/@delayMinutes", 45); // For backwards compatbility (to be removed in the future)
					} catch (NoSuchElementException nseE) {
						continue;
					}
					log.trace ("want to train "+Integer.toString (train)+" of type "+type );
					if (train <= 0) {
						continue;
					}
					wantToTrain.put(troopType, train); //collect the 
				}
				int sumTrained=0;   //sum the total number of troops trained
				int sumWantToTrain=0; //sum the total number of troops we want to train, from config file
				int noOfTrainees=0; //to count the number of different troops to train
				for (TroopType troopType : TroopType.values()) {
					if (wantToTrain.get(troopType)>0) {
						sumTrained = sumTrained + troopsProduced.get(troopType);
						sumWantToTrain = sumWantToTrain + wantToTrain.get(troopType);
						noOfTrainees++;
					}
				}
				TroopTypeMap weightedToTrain = new TroopTypeMap();
				for (TroopType troopType : TroopType.values()) {
					if (wantToTrain.get(troopType)>0) {
						int toTrain = wantToTrain.get(troopType);
						int weight = (toTrain*1000)/sumWantToTrain;  //since we're working with integer, multiply by 1000 to increase resolution
						weightedToTrain.put(troopType, weight);
						noOfTrainees++;
					}
				}
//****** End weighted troop making				
*/

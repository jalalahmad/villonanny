package net.villonanny.strategy;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.NoSuchElementException;

import net.villonanny.ConfigManager;
import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.entity.Village;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;

abstract public class Strategy {
	private final static Logger log = Logger.getLogger(Strategy.class);
	protected Village village;
	protected Util util;
	protected Translator translator;
	protected TimeWhenRunnable timeWhenRunnable;
	protected SubnodeConfiguration config;
	protected String id; // Uniquely identifies the instance
	protected boolean deleted = true; // Deleted unless init() is called
	protected boolean waiting=false;

	/**
	 * Executes the strategy 
	 * @return the time when it should be run again
	 * @throws ConversationException
	 * @throws InvalidConfigurationException 
	 */
	abstract public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException;
	
	
	/**
	 * 
	 * @param strategyConfig the part of the configuration that pertains to this strategy
	 * @param village
	 */
	public void init(SubnodeConfiguration strategyConfig, Village village) {
		if (village!=null) {
			init(strategyConfig, village.getUtil(), village);
		} else {
			throw new StrategyFatalException("Village should not be null");
		}
	}
	
	/**
	 * 
	 * @param strategyConfig the part of the configuration that pertains to this strategy
	 * @param util
	 * @param village can be null when this is a ServerStrategy
	 */
	public void init(SubnodeConfiguration strategyConfig, Util util, Village village) {
		this.config = strategyConfig;
		this.village = village;
		this.util = util;
		this.translator = util.getTranslator();
		this.id = getIdFromConfig(strategyConfig);
		this.deleted = false;
	}
	
	protected void checkConsoleFlags() {
		util.getConsole().checkFlags();
	}
	
	public String getId() {
		return id;
	}
	
	public TimeWhenRunnable  getTimeWhenRunnablePassive () {
		return this.timeWhenRunnable;
	}
	public TimeWhenRunnable getTimeWhenRunnable() {

		//this routine gets called every time villonanny checks timeWhenRunnable for the village this strategy belongs to,
		//I believe it also gets called when server checks timeWhenRunnable for the villages
		//so EventLog.log or any other log in here gets spammed a lot. fofo
		log.trace(this.village.getVillageName()+" strategy \"" + this.getDesc() + " TWR was:"+this.timeWhenRunnable);
		
		// first check if this strategy is finished
		if (village.strategyDone.getFinished(this.id) == true) {
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + " Finished. "+this.timeWhenRunnable);
			return TimeWhenRunnable.NEVER;
		}
		String waitingFor = this.config.getString("/@waitFor", null);
		waitingFor = this.config.getString("/@waitfor", waitingFor); //a bit more lenient on uppercase after a few failed tests due to typo's. fofo
		waiting=false;
		// log.debug(this.getDesc()+" getting timeWhenRunnable "+timeWhenRunnable);
		if (timeWhenRunnable != null) { 
			// [xtian] can we remove this?
			//if we are waiting for something and opportunist is true,
			//village will execute the strategy regardless because of opportunist flag
			//opportunist flag is read further down, if we arent waiting for anything. fofo
			this.timeWhenRunnable.setOpportunist(false);
		}
		if (waitingFor == null){ 
			// log.debug("strategy \"" + this.getDesc() + "\" is waiting for nothing");
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" waiting until "+this.timeWhenRunnable);
		} else{
			int minLevel = this.config.getInt("/@minLevel", -1);
			if (minLevel >= 0) {
				log.trace("waiting for builder");
				int currentLevel = village.getMinLevel(waitingFor);
				// check it
				if (currentLevel < minLevel) {
					log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" waiting for \"" + waitingFor + "\""
								+" Level "+minLevel+" Current "+currentLevel);
					waiting=true;
					// return setTimeWhenRunnable(new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
					return (new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
				}
				// combine string for debug display
				waitingFor.concat(Integer.toString(minLevel));
			} else {
				if (village.strategyDone.getFinished(waitingFor) == false) {
					log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" is waiting for \"" + waitingFor + "\"");
					waiting=true;
					// return setTimeWhenRunnable(new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
					return (new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
				}
			}
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" was waiting for \"" + waitingFor + "\" now waiting until "+this.timeWhenRunnable);
		}
		
		// should runwhile check if runWhile is waiting for something?
		// currently have to do both which gives more control but means is more a runUntil

		String runWhile = this.config.getString("/@runWhile", null);
		if (runWhile != null) {
			// check if should still be running
			if (village.strategyDone.getFinished (runWhile) == true) {
				// finished
				// waiting=true;
				
				// has this been stopped already
				if (village.strategyDone.getFinished(this.getId())) {
					// it has
					log.trace(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" runWhile \"" + runWhile + "\" already ended");
				} else {
					// stop it
					if (this.timeWhenRunnable == null ) {
						log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" runWhile \"" + runWhile + "\" never run");
					}
					// mark finished
					log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" running while \"" + runWhile + "\" ended");
	                village.strategyDone.setFinished(this.getId(), true); //register it as done
				}
				// update never
				this.timeWhenRunnable = new TimeWhenRunnable(false);
				return this.timeWhenRunnable;  // the strategy we 'run while is running' is done, and so are we
			} else {
				log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + "\" running while "+runWhile+" running");			
			}
		}
		
		// we may not be waiting for some event but change in conditions - check if still waiting 
		if (!this.avgResLevelHighEnough()) { //
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + " waits for field level high enough");
			waiting=true;
			// return setTimeWhenRunnable(new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
			return (new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
		}
		if (!this.prodRateHighEnough()) { //
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + " waits for prod rate high enough");
			waiting=true;
			// return setTimeWhenRunnable(new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
			return (new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
		}
		if (!this.prodRateLowEnough()) { //
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + " waits for lower prod rate");
			waiting=true;
			// return setTimeWhenRunnable(new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
			return (new TimeWhenRunnable(Util.NotInTheNearFuture()));  //since we wait for another strategy to end and that strategy will trigger wakeup
		}
		// following check for minResource - may trigger on resources so set shorter check time
		// should always be last if waiting for harder criterion above
        if (!this.resHighEnough()){
			// resource sender on options tag, otherwise use strategy
			int minPauseMinutes = config.getInt("/options/@minPauseMinutes", 180);
			minPauseMinutes = config.getInt("/@minPauseMinutes", minPauseMinutes );
			// see if min pause is set, if not use 3hours which is level 10 build on speed
            // EventLog.log("Not enough resource available");            	
			log.debug(this.village.getVillageName()+" strategy \"" + this.getDesc() + " waiting for resources");
			// dont set waiting and set a real time as waiting for production or merchant to arrive
			// waiting=true;
			return setTimeWhenRunnable(new TimeWhenRunnable(System.currentTimeMillis() + (minPauseMinutes*Util.MILLI_MINUTE))); // try again later
        }
		// we are not waiting for some event but change in conditions - check TWR will be valid at some point
		if ((timeWhenRunnable != null) && (timeWhenRunnable.getTime() == Util.NotInTheNearFuture())) {
			// not - so reset in case condition met
			log.trace("Reseting TWR as no longer waiting");
			timeWhenRunnable = null;
		}

		// GetOpportunistStatus();
		Boolean opportunist = this.config.getBoolean("/@opportunist", false);
		// EventLog.log("strategy ." + this.getDesc() + ". opportunist " + Boolean.toString(opportunist));
		if (timeWhenRunnable != null) {
			this.timeWhenRunnable.setOpportunist(opportunist);
		}
		log.trace(this.village.getVillageName()+" strategy \"" + this.getDesc() + " returning timeWhenRunnable "+this.timeWhenRunnable);
		return timeWhenRunnable;
	}

	public TimeWhenRunnable	setTimeWhenRunnable(TimeWhenRunnable timeWhenRunnable) {
		// check > never
		// check valid
		if (timeWhenRunnable.after(timeWhenRunnable.NEVER)) {
			log.error("timeWhenRunnable > NEVER "+timeWhenRunnable);
			timeWhenRunnable = timeWhenRunnable.NEVER;
		}
		if (this.waiting != true) {//keep the old time when we're waiting
			this.timeWhenRunnable = timeWhenRunnable;
		}
		Boolean opportunist = this.config.getBoolean("/@opportunist", false);
		this.timeWhenRunnable.setOpportunist(opportunist);
		//log.debug(this.getDesc()+" setting timeWhenRunnable "+timeWhenRunnable);
		return this.timeWhenRunnable;
	}

	public String getDesc() {
		String desc = "unknownDesc";
		try {
			String className = config.getString("/@class");
			className = config.getString("/@uid", className);  //changed to using uid instead of classname as default when desc is not present. fofo
			desc = config.getString("/@desc", className);
		} catch (Exception e) {
			log.error("Error while getting server description", e);
		}
		return desc;
	}

	/**
	 * Return the minimum resources needed to run regardless of time, or null if not applicable
	 * @return
	 */
	public ResourceTypeMap getTriggeringResources() {
		return null;
	}
	
	/**
	 * Return true if this strategy modifies village resources
	 * @return
	 */
	public boolean modifiesResources() {
		return false;
	}
	
	public synchronized void updateConfig(SubnodeConfiguration strategyConfig, Util util) {
		this.util = util;
		this.config = strategyConfig;
		this.id = getIdFromConfig(strategyConfig);
		Boolean force=this.config.getBoolean ("/@forceOnReload", null);
		if (force==null) {
			force=village.forceOnReload; //use village override if not set. fofo
		}
		
		if (force) {
			this.timeWhenRunnable = TimeWhenRunnable.NOW;
			village.strategyDone.setFinished (this.getId(), false); //I believe this is prudent, since we now restart it. fofo
		}
		this.deleted = false;
		log.debug("Updating strategy config for " + getDesc());
	}
	
	/**
	 * Returns a string that uniquely represents the strategy instance
	 * with values taken from the configuration. Should be refined by subclasses when needed. 
	 * @param strategyConfig
	 * @return the string ID
	 */
	 public static String getIdFromConfig(SubnodeConfiguration strategyConfig) {
		 String result =  strategyConfig.getString("/@uid", null);
		 if (result==null) { // fallback to legacy id
			 String className = strategyConfig.getString("/@class", null);
			 EventLog.log("msg.nouid", Strategy.class, "Strategy \"" + className + "\"");
			 // The basic form of ID is made of class + desc
			 String desc = strategyConfig.getString("/@desc", "");
			 // Enabled not used because a strategy should not change identity when disabled
			 // String enabled = strategyConfig.getString("/@enabled");
			 result = new StringBuffer(className).append("#").append(desc).toString();
		 }
		 return result;
	 }

	 /**
	  * @return true if the strategy has been removed from the configuration, or it hasn't been initialised yet
	  */
	public boolean isDeleted() {
		return deleted;
	}

	public boolean isWaiting() {
		return waiting;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}
	
	// "s2343 (Grow Cheapest Field - FieldGrowth)"
	public synchronized String toString() {
		return String.format("%s (%s - %s)", getId(), getDesc(), getClass().getSimpleName());
	}
	
	/**
	 * 
	 * @param itemIds a list of ids that can be ranges like "m-n"
	 * @return the list of ids "unrolled"
	 */
	public List<String> itemIdsToList(List<String> itemIds) {
		List<String> result = new ArrayList<String>();
		for (String idComponent : itemIds) {
			boolean valid = false;
			try {
				if (idComponent.indexOf("-")==-1) {
					 // Single number
					Integer.parseInt(idComponent.trim()); // Check if integer
					if (!result.contains(idComponent)) {
						result.add(idComponent);
					}
					valid = true;
				} else {
					// Range x-y
					String[] parts = idComponent.split("-");
					if (parts.length==2) {
						int from = Integer.parseInt(parts[0].trim());
						int to = Integer.parseInt(parts[1].trim());
						for (int j = from; j <= to; j++) {
							String s = String.valueOf(j);
							if (!result.contains(s)) {
								result.add(s);
							}
						}
						valid = true;
					}
				}
			} catch (NumberFormatException e) {
				// Nothing to do
			}
			if (!valid) {
				log.error("Invalid id skipped: " + idComponent);
			}
		}
		return result;
	}

	public boolean prodRateHighEnough () {
		try { //minProductionRate Start
			ResourceTypeMap resourceLimit = new ResourceTypeMap();
			resourceLimit.put(ResourceType.WOOD, config.getInt("/minProductionRate/@wood", -1));
			resourceLimit.put(ResourceType.CLAY, config.getInt("/minProductionRate/@clay", -1));
			resourceLimit.put(ResourceType.IRON, config.getInt("/minProductionRate/@iron", -1));
			resourceLimit.put(ResourceType.CROP, config.getInt("/minProductionRate/@crop", -1));
			ResourceTypeMap resourceProduction = village.getProduction();
			if (resourceProduction.getSumOfValues()==0) {
				try {
					village.update();
					log.debug("prodRateHighEnough updating village ");
					resourceProduction = village.getProduction();
				} catch (ConversationException e) {
					e.printStackTrace();
				}
			}
			for (ResourceType resourceType : ResourceType.values()) {
				if (resourceType == ResourceType.FOOD) {
					continue;
				}
				if ((resourceLimit.get(resourceType)>0) && 
					(resourceProduction.get(resourceType) < resourceLimit.get(resourceType))) {
					log.debug(this.getDesc()+" Not enough production "+resourceType);
					log.debug(this.getDesc()+" production is "+resourceProduction.toStringNoFood());
					log.debug(this.getDesc()+" min limits are    "+resourceLimit.toStringNoFood());
					return false; // try again later
				}
			}
			log.trace(this.getDesc()+" production is "+resourceProduction.toStringNoFood());
			log.trace(this.getDesc()+" min limits are    "+resourceLimit.toStringNoFood());
		} catch (NoSuchElementException nseE) {
			log.trace(this.getDesc()+" minProductionRate no limits set");
		}  //minProductionRate end

		return true;
	}

	public boolean prodRateLowEnough () {
		try { //maxProductionRate Start.
			ResourceTypeMap resourceLimit = new ResourceTypeMap();
			resourceLimit.put(ResourceType.WOOD, config.getInt("/maxProductionRate/@wood", -1));
			resourceLimit.put(ResourceType.CLAY, config.getInt("/maxProductionRate/@clay", -1));
			resourceLimit.put(ResourceType.IRON, config.getInt("/maxProductionRate/@iron", -1));
			resourceLimit.put(ResourceType.CROP, config.getInt("/maxProductionRate/@crop", -1));
			ResourceTypeMap resourceProduction = village.getProduction();
			if (resourceProduction.getSumOfValues()==0) {
				try {
					village.update();
					log.debug("prodRateLowEnough updating village *****");
					resourceProduction = village.getProduction();
				} catch (ConversationException e) {
					e.printStackTrace();
				}
			}
			for (ResourceType resourceType : ResourceType.values()) {
				if (resourceType == ResourceType.FOOD) {
					continue;
				}
				if ((resourceLimit.get(resourceType)>0) && 
					( resourceProduction.get(resourceType) >= resourceLimit.get(resourceType))) {
					log.debug(this.getDesc()+" Too much production "+resourceType);
					log.debug(this.getDesc()+" production is "+resourceProduction.toStringNoFood());
					log.debug(this.getDesc()+" max limits are    "+resourceLimit.toStringNoFood());
					return false; // try again later
				}
			}
			log.trace(this.getDesc()+" max limits are    "+resourceLimit.toStringNoFood());
		} catch (NoSuchElementException nseE) {
			log.trace(this.getDesc()+" maxProductionRate no limits set");
		}  //maxProductionRate end

		return true;
	}

	public boolean avgResLevelHighEnough (  ) {
		//minAverageLevel start
		double woodLevel=config.getDouble("/minAverageLevel/@wood", -1);
		double clayLevel=config.getDouble("/minAverageLevel/@clay", -1);
		double ironLevel=config.getDouble("/minAverageLevel/@iron", -1);
		double cropLevel=config.getDouble("/minAverageLevel/@crop", -1);
		double lvl;
		//EventLog.log(String.format("min res levels wood:%s clay:%s iron:%s crop:%s",Double.toString (woodLevel),Double.toString (clayLevel),Double.toString (ironLevel),Double.toString (cropLevel)));
		if (woodLevel>0) {
			lvl=village.getAverageFieldLevel (BuildingType.WOODCUTTER);
			//EventLog.log(String.format("avg wood lvl is ", Double.toString (lvl)));
			if (lvl < woodLevel) {
				log.debug(String.format("Average Woodcutter level too low. have %s want %s",Double.toString (lvl), Double.toString (woodLevel)));
				return false;
			}
		}
		if (clayLevel>0) {
			lvl=village.getAverageFieldLevel (BuildingType.CLAY_PIT);
			//EventLog.log(String.format("avg clay lvl is ", Double.toString (lvl)));
			if (lvl < clayLevel) {
				log.debug(String.format("Average Claypit level too low. have %s want %s",Double.toString (lvl), Double.toString (clayLevel)));
				return false;
			}
		}
		if (ironLevel>0) {
			lvl=village.getAverageFieldLevel (BuildingType.IRON_MINE);
			//EventLog.log(String.format("avg iron lvl is ", Double.toString (lvl)));
			if (lvl < ironLevel) {
				log.debug(String.format("Average Iron mine level too low. have %s want %s",Double.toString (lvl), Double.toString (ironLevel)));
				return false;
			}
		}
		if (cropLevel>0) {
			lvl=village.getAverageFieldLevel (BuildingType.CROPLAND);
			//EventLog.log(String.format("avg crop lvl is ", Double.toString (lvl)));
			if (lvl < cropLevel) {
				log.debug(String.format("Average Cropland level too low. have %s want %s",Double.toString (lvl), Double.toString (cropLevel)));
				return false;
			}
		}
		return true;
	}
	
	public boolean resHighEnough () { //returns false if a set limit is over the available resource
		//previous version returns true if one of the four limits is not set, or if all is set, and all res are at or above limit
		EnumMap<ResourceType, Double> resourceLimit = new EnumMap<ResourceType, Double>(ResourceType.class);
		boolean oneIsEnough = config.getBoolean("/minResources/@oneIsEnough", false);
		resourceLimit.put(ResourceType.WOOD, config.getDouble("/minResources/@wood", -1.0));
		resourceLimit.put(ResourceType.CLAY, config.getDouble("/minResources/@clay", -1.0));
		resourceLimit.put(ResourceType.IRON, config.getDouble("/minResources/@iron", -1.0));
		resourceLimit.put(ResourceType.CROP, config.getDouble("/minResources/@crop", -1.0));
		ResourceTypeMap resourceAvailable = village.getAvailableResources();
		if (resourceAvailable.getSumOfValues()==0) { //is zero if this is the first strategy to be called. fofo
			try {
				village.update();
				log.debug("resHighEnough updating village *****");
				resourceAvailable = village.getAvailableResources();
			} catch (ConversationException e) {
				e.printStackTrace();
			}
		}
		for (ResourceType resourceType : ResourceType.values()) {
			if (resourceType == ResourceType.FOOD) {
				continue;
			}
			if (oneIsEnough==false) {
				if ((double) resourceAvailable.get(resourceType) < resourceLimit.get(resourceType) &&
						(resourceLimit.get(resourceType)>=0)){
					log.debug(this.getDesc()+" Not Enough "+resourceType);
					return false; // try again later
				}
			}else { //oneIsEnoug=true. return true if one of the resources is above the limit
				if ((double) resourceAvailable.get(resourceType) > resourceLimit.get(resourceType) &&
						(resourceLimit.get(resourceType)>=0)){
					log.debug(this.getDesc()+" Enough "+resourceType+", Running");
					return true; // try again later
				}
			}
		}
		if (oneIsEnough==false) {
			return true;
		} else {
			return false;
		}
	}
}


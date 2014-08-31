package net.villonanny.strategy;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.SevenBySeven;
import net.villonanny.entity.Village;
import net.villonanny.entity.SevenBySeven.OutputType;
import net.villonanny.misc.Coordinates;


/**
 * Server strategy to implement CropFinder
 * 
 * @author GAC
 *
 */
public class MapReader extends ServerStrategy {
    private final static Logger log = Logger.getLogger(MapReader.class);
	private	CropFinder mapReader = null;
	
	public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
            Village village = this.util.getServer().getVillages().iterator().next();        
            // check for first use
            if (mapReader == null) {
                if (this.util.getServer().getVillages().iterator() != null) {
                    // make sure position set
                    village.update();
                    log.debug("search from Village " + village.getVillageName() + " (" + village.getPosition().x + "," + village.getPosition().y + ")");
        			// String className = strategyConfig.getString("/@class");
        			// String fullClassName = Strategy.class.getPackage().getName() + "." + className;
        			// result = (Strategy) Class.forName(fullClassName).newInstance();
        			// String fullClassName = Strategy.class.getPackage().getName() + ".MapReader";
        			// Strategy mapReader = (Strategy) Class.forName(fullClassName).newInstance();
        			try {
            			String fullClassName = Strategy.class.getPackage().getName() + ".CropFinder";
            			EventLog.log("MapReader creating "+fullClassName);
        				mapReader = (CropFinder) Class.forName(fullClassName).newInstance();
        			} catch (InstantiationException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			} catch (IllegalAccessException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			} catch (ClassNotFoundException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			}
                } else {
                	EventLog.log("No Villages Configured for Server, need 1 as default, aborting");
                }
            }
			// explore map - returns true if more to do
        	if (mapReader.explore(village, super.config, super.util)) {
                log.info(String.format("Strategy %s done for now", getDesc()));
                Long minPauseMinutes = super.config.getLong("/@minPauseMinutes", super.config.getLong("/start/@minPauseMinutes", 2));
                return new TimeWhenRunnable(System.currentTimeMillis() + (minPauseMinutes * Util.MILLI_MINUTE), true); // Try again later
        	}
            // finished or errors set a long time or disable this strategy
            log.info(String.format("Strategy %s Finished", getDesc()));
            village.strategyDone.setFinished(this.getId(), true); //register it as done
            return TimeWhenRunnable.NEVER;
        } finally {
            NDC.pop();
        }
	}
	
    
	
}

package net.villonanny.strategy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.SevenBySeven;
import net.villonanny.entity.Village;
import net.villonanny.entity.SevenBySeven.OutputType;
import net.villonanny.misc.Coordinates;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 *	Simple CropFinder to find vacant resource fields
 *		checks each valley in a grid around nominated start point and writes finding to cropfinder.csv
 *		default with no parameters is to check the single 7x7 around the village owning the strategy
 *		optional parameters to specify start of search location, maximum radius in terms of individual squares
 *		also can specify a point of origin to calculate distance each site is from that defaults to the start
 *
 *	TODO	Integrate with language files
 *
 *   @author gac
 */
// public class CropFinder extends ServerStrategy {

public class CropFinder extends Strategy {

    private final static Logger log = Logger.getLogger(CropFinder.class);

    private int startX = -9999, startY = -9999;			// start location invalid
    private int fromX = -9999, fromY = -9999;			// as is distance from location
    private int maxRange = 0;							// maximun search in squares
    private int lastX = -9999, lastY = -9999;			// start location - use invalid co-ordinates
    private int sCount = 0;								// count of grids checked, could use for state, set -1 if finished

    private enum State {

        CREATED, RUNNING, FINISHED
    };	// simple c like enum
    private State state = State.CREATED;				// separate state
    private OutputType outputType = OutputType.TXT;		// output file type
   
    
    public TimeWhenRunnable execute() throws ConversationException {
        /*  strategy configuration - start and from are optional and default to location of village owning strategy
        <strategy class="CropFinder" desc="Find 9 and 15 Croppers around given location" enabled="true"  maxRadius="10" minPauseMinutes="2" uid="sV1cf">
        <start x="XCOORD" y="YCOORD" maxRadius="10" minPauseMinutes="2"/>
        <from  x="XCOORD" y="YCOORD" />
        <output append="true" format="csv" />
        </strategy>
        note maxradius is same units as co-ordinates but searching is done in 7x7 blocks
        current output formats supported are .txt default or .csv
         */
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());

        // TODO GAC Make Check map location for croppers
        // TODO GAC In loading config found that does not set TimeWhenRunnable and old action was waiting 54mins

        try {
            log.debug("CropFinder from Village " + this.village.getVillageName() + " (" + this.village.getPosition().x + "," + this.village.getPosition().y + ")");
			// explore map - returns true if more to do
        	if (explore(this.village, super.config, super.util)) {
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

    public Boolean  explore(Village village, SubnodeConfiguration config, Util util) throws ConversationException {
        // check if run before
        if (state == State.CREATED) {
            // get start location
        	Coordinates startCoord = new Coordinates(config, "start", 999, 999);
        	startX = startCoord.getIntX();
        	startY = startCoord.getIntY();
            // allow maxRadius on top or start line & also old version which did not have capital!
            maxRange = config.getInt("/start/@maxradius", 1);
            maxRange = config.getInt("/@maxRadius", config.getInt("/start/@maxRadius", maxRange));
            // start by getting this start location, only need to do this if start not specified
            if (startX == 999) {
                // village co-ords only set after first update so make sure villages run at least once
                startX = village.getPosition().x;
                startY = village.getPosition().y;
                EventLog.log("Coordinates invalid or absent. Using current village's ones: " + startX + "," + startY);
                // System.exit(0);
            }
            // get origin to calculate distance from, default is the same as start
			Coordinates coord = new Coordinates(config, "from", startX, startY);
			fromX = coord.getIntX();
			fromY = coord.getIntY();

            // get output file format 0 used as file extension
            String outputExt = config.getString("/output/@format", "");
            if (outputExt != "") {
                outputType = OutputType.fromKey(outputExt);
            }
            // as each map block is written it will be appended to the output file
            // however at the start of the strategy allow user to start a clean file
            Boolean fileAppend = config.getBoolean("/output/@append", true);
            // to use this as dont want to expose filename outside 7x7 class need to create an instance and clear if needed
            // TODO - consider if file ownership should be other way round and this passes into 7x7 class
            if (!fileAppend) {
                // SevenBySeven map = new SevenBySeven(super.util, village.getVillageName(), village.getVillageUrlString(), village.getTranslator());
                SevenBySeven map = new SevenBySeven(util, village.getVillageName(), village.getVillageUrlString(), village.getTranslator());
                map.setOutputType(outputType, fileAppend);
            }
            // reprt what doing
            log.debug("Village " + village.getVillageName() + " URL " + village.getVillageUrlString());
            EventLog.log("Start Searching Around " + startX + ", " + startY + " max Range " + maxRange);
            if (!fileAppend) {
                EventLog.log("Results will overwrite ." + outputType.toString() + " file");
            }

            // first time
            lastX = (int) (startX - (Math.floor(maxRange / 7) * 7));
            lastY = (int) (startY - (Math.floor(maxRange / 7) * 7));
            // everything initialised
            state = State.RUNNING;
        }
        // check if something to do
        if (state == State.RUNNING) {
            // create a new 7x7 for this search - they are not kept but perhaps should be or else check in output file
            // Valley	valley = new Valley(super.util, village.getVillageName(), village.getVillageUrlString(), village.getTranslator());
        	// village.getVillageName(); 
        	// village.getVillageUrlString(); 
        	// village.getTranslator();
            SevenBySeven map = new SevenBySeven(util, village.getVillageName(), village.getVillageUrlString(), village.getTranslator());
            sCount++;
            // set output if not default
            if (outputType != OutputType.TXT) {
                map.setOutputType(outputType);
            }

            EventLog.log("Search Map " + sCount + " from " + lastX + ", " + lastY);
            // note if we get a page error then skips and repeats whole block - is this the desired action?
            map.search(util, lastX, lastY, fromX, fromY);

            // go back to main page - simpler for other routines and also emulate person checking what happening
            log.trace("Going Back to Main Page");
            village.gotoMainPage();

            // advance along row and check if finished
            lastX += 7;
            if (lastX > (startX + maxRange)) {
                // step Y down a 7x7 check if finished
                lastY += 7;
                if (lastY > (startY + maxRange)) {
                    // mark done
                    lastX = lastY = -999;
                    state = State.FINISHED;
                    EventLog.log("Finished Searching");
                } else {
                    lastX = startX - maxRange;
                }
            }
            log.debug("Cropfinder NextX " + Integer.toString(lastX) + " NextY " + Integer.toString(lastY));
        }
        // check if more for next time
        if (state != State.FINISHED) {
            // log.info(String.format("Strategy %s done for now", getDesc()));
            // Long minPauseMinutes = super.config.getLong("/@minPauseMinutes", super.config.getLong("/start/@minPauseMinutes", 2));
            // return new TimeWhenRunnable(System.currentTimeMillis() + (minPauseMinutes * Util.MILLI_MINUTE), true); // Try again later
            return true;
        } else {
        	log.trace("Finished Exploring");
            // set a long time or disable this strategy
            // log.info(String.format("Strategy %s Finished", getDesc()));
            // village.strategyDone.setFinished(this.getId(), true); //register it as done
            // return TimeWhenRunnable.NEVER;
        }
        // return TimeWhenRunnable.NEVER;
        return false;
    }
    
    public boolean modifiesResources() {
        // EventLog.log("Cropfinder modifies Resources called");
        return false;
    }
}

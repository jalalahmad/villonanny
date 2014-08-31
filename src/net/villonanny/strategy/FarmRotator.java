package net.villonanny.strategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.ReportMessageReader;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.RallyPoint;
import net.villonanny.entity.Valley;
import net.villonanny.type.TroopTransferType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 *
 */
public class FarmRotator extends Strategy {

    private final static Logger log = Logger.getLogger(FarmRotator.class);
    private int totErrors = 0;
    private final int MAX_ERRORS = 5;
    private final double RANDOMISE_RATIO = 0.1; // +-10%
    private Random random = new Random();
    private int nextVillage = 0; 
    private Map <Integer, Long> targetReturnTimes = new HashMap <Integer, Long>();
    private boolean firstRun=true;
    
    public TimeWhenRunnable execute() throws ConversationException {
        // <strategy class="FarmRotator" desc="DESCRIPTION" enabled="true" movement="REINFORCE|RAID|ATTACK" 
    	//  spy="RESOURCES|DEFENSES" minPause="PAUSE" runMultiple="true" runOnce="true">
        //   <troops type="TROOP_NAME" allowLess="ALLOWLESS" min="MINIMUM_TROOPS" randomise="RANDOMISE" enabled="true">TROOP_AMOUNT</troops>
        //    <target x="XCOORD" y="YCOORD" village="TARGET_VILLAGE" item="CATA_TARGET1, CATA_TARGET2" movement="REINFORCE|RAID|ATTACK" spy="RESOURCES|DEFENSES"/>
        // </strategy>
    	// runOnce will make one attack on each target, and return timeWhenRunnable.NEVER
    	// runMultiple will make it continue through the list as long as there are enough troops , 
    	// but never more than once through the list each time its called. fofo
    	//<startTarget startAt="3" />, to avoid sending a huge number of raids to the first villages on the list when debugging
        
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());

        // TODO Per le volte successive:
        // TODO Controlla il report dell'attacco (difficile!) - read and respond to the attack report
        // TODO Se sono state perse troppe truppe, cancella questa strategy - cancel strategy if troop loss it too great

    try {
           long myTimeToRun = 0; //<startTarget startAt="3" />, to avoid sending a huge number of raids to the first villages on the list when debugging
           if (firstRun) {
        	   nextVillage=super.config.getInt("/startTarget/@startAt", 0);
        	   if (nextVillage<0) nextVillage=0;
        	   firstRun=false;
           }
           RallyPoint rallyPoint = village.getRallyPoint();
           if (rallyPoint == null) {
               log.debug("No rally point");
               int minPauseMinutes = super.config.getInt("/@minPause", 25); //changed to 25. 5 min seemed a bit fast when no rallypoint exist. fofo
               if (minPauseMinutes <16) {
            	   minPauseMinutes = 16; //set pause to minimum 16 min when no rallypoint to avoid spamming. fofo
               }
               return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
           }
           boolean runMultiple = super.config.getBoolean ("/@runMultiple", false); //default backward compatible. fofo
           boolean runOnce = super.config.getBoolean ("/@runOnce", false); //true if only run once and then quit
           boolean keepGoing=true;
           boolean endStrategy=false;
     	   long waitMillis = 0;
    	   double rate=0;
    	   int wavesSent=0;
    	   List<SubnodeConfiguration> targetNodes = super.config.configurationsAt("/target");
           while (keepGoing) {
            	//start check troops
        	   TroopTypeMap availablePerType = rallyPoint.fetchSendableTroops(super.util, false);
            	String defaultMovement = super.config.getString("/strategy/@movement", "normal"); // Default is "normal" unless specified on strategy node
            	String defaultSpy = super.config.getString("/strategy/@spy", null);
            	TroopTypeMap toSend = getTroopsToSend (availablePerType, defaultMovement, defaultSpy);
            	int minPauseMinutes = super.config.getInt("/@minPause", 3);
            	if (toSend==null) { //not enough troops
            		if (myTimeToRun == 0) { //no troops here on the first iteration               	
            			EventLog.log(String.format("Strategy %s done for now: Not enough troops", getDesc()));
            			return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
            		}
            		else { //we have sent one or more attacks, and ran out of troops as planned
            			 long retTime=getNextReturnTime(targetNodes.size());
            	     	 EventLog.log(String.format("Strategy %s done for now: Next run in %s minutes at rate %s", getDesc(), Long.toString((retTime-System.currentTimeMillis())/(long) 60000), Double.toString(rate)));
            	     	 return new TimeWhenRunnable(retTime);
            		}
            	}
            	// end check troops
            	int secondsToArrive = 0;

            	if (targetNodes != null) {  //start send troops
            		if (wavesSent>=targetNodes.size()) {
            			long retTime=getNextReturnTime(targetNodes.size());
            			EventLog.log(String.format("Sent once to all targets. Next run in %s minutes at rate %s", getDesc(), Long.toString((retTime-System.currentTimeMillis())/(long) 60000), Double.toString(rate)));
            			return new TimeWhenRunnable(retTime);
            		}
            		if (nextVillage >= targetNodes.size()) { //moved inside if to avoid nullpointer if targetNodes == null
            			nextVillage = 0;
            		}
            		SubnodeConfiguration target = (SubnodeConfiguration) targetNodes.get(nextVillage);
            		/* Travian Style Coordinates in configuration
            		 * @author Czar
            		 */
            		String x;
            		String y;
            		String coordsTravianStyle = target.getString("/@coords", null);
            		if (null != coordsTravianStyle) { // using travian-style configuration, if exists
             	      EventLog.log("Coords travian style...");
             	      String[] coords = coordsTravianStyle.split("[(|)]");
             	      x = coords[1];
             	      y = coords[2];
            		} else { // if not reverting to old style
            			x = target.getString("/@x", "");
            			y = target.getString("/@y", "");
            		}
            		// end of travian-style modification
            		String village = target.getString("/@village", "");
            		String targetdesc = target.getString("/@desc", "");
            		//String targetdesc = target.getString("/@desc", "");
            		String movement = target.getString("/@movement", defaultMovement);
            		String fullkey = "movement." + translator.getKeyword(movement, "movement"); // movement.normal
            		TroopTransferType transferType = TroopTransferType.fromKey(fullkey);
            		String itemString1 = target.getString("/@item[1]", null);
            		String itemString2 = target.getString("/@item[2]", null);
            		EventLog.log("cata target 1= " + itemString1 + " cata target 2= " + itemString2);
            		TroopTransferType spyType = null;
            		String spyTypeString = target.getString("/@spy", defaultSpy);
            		if (spyTypeString != null) {
            			fullkey = "movement." + translator.getKeyword(spyTypeString, "movement"); // movement.spy.resources
            			spyType = TroopTransferType.fromKey(fullkey);
            		}
            		// check for previous bounty
            		else if ((!x.equals("")) && (!y.equals(""))) {
            			// log.debug("checking reports for " + x + "," + y + " id " + util.getMapIdFromPosition(x, y));
            			// ReportMessageReader.getInstance().getAverageBounty(util.getServerId(), util.getMapIdFromPosition(x, y));
            			Valley v = new Valley(util, x, y);
            			log.debug("Average Bounty " + v.getAverageBounty());
            		}
            		// all seems fine, try to send troops
            		super.village.gotoMainPage(); // Ensure you do it from the right village (someone might have clicked to a different village meanwhile)
            		try {
            			nextVillage++; //move to next village before we try to post
            			wavesSent++;   //count how many targets we send to so we can limit it to one send to each target
            			secondsToArrive = rallyPoint.sendTroops(util, x, y, village, toSend, transferType, new String[]{itemString1, itemString2}, spyType);
            			totErrors = 0;
        				if (runOnce==true) {//only run once and exit
        					if (nextVillage >=targetNodes.size()) {//at the end of the target list
            					EventLog.log(String.format("All targets served, %s ending", this.id));
            					return TimeWhenRunnable.NEVER;
            				}
            			}
            		} catch (ConversationException e) {
            			log.error(e);
            			totErrors++;
            			if (totErrors <= MAX_ERRORS) {
            				EventLog.log(String.format("Strategy has error; retrying later (%s left)", MAX_ERRORS - totErrors));
            				util.shortestPause(false); // Just to be safe
            				minPauseMinutes = super.config.getInt("/@minPause", 5); 
            				if (minPauseMinutes <12) {
            					minPauseMinutes = 12; //set pause to minimum 12 min when error to avoid spamming if config is bad. fofo
            				}
            				return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
            			} else {
            				log.error("Strategy has too many errors; disabling", e);
            				EventLog.log("Strategy has too many errors; disabling");
                	       return TimeWhenRunnable.NEVER;
            			}
            		}
        	   } // end send troops
       
            	//*****fofo start
        	   double rawRate = super.config.getDouble("/target/@rate", 1);
        	   rate = Math.max(rawRate, Double.MIN_VALUE);
        	   double raidTime = (2 * secondsToArrive * Util.MILLI_SECOND);
        	   waitMillis = (long) (raidTime / rate);
        	   long raidBeBack = System.currentTimeMillis() + waitMillis+minPauseMinutes;
        	   targetReturnTimes.put (nextVillage, raidBeBack);
        	   if (myTimeToRun == 0) {
        	   if (myTimeToRun > 0) { //not the first run
        		   //EventLog.log(String.format("Not first wave. this wave back in %s min", Long.toString((getNextReturnTime(targetNodes.size())-System.currentTimeMillis())/(long) 60000)));
        		   if ((raidBeBack < myTimeToRun) && (myTimeToRun != 0)) { //the current raid sent returns before the previous ones
        			   myTimeToRun = raidBeBack;
        		   }
        	   } 
        	   else {
        		   //EventLog.log(String.format("First wave. first wave back in %s min", Long.toString((getNextReturnTime(targetNodes.size())-System.currentTimeMillis())/(long) 60000)));
        		   myTimeToRun = raidBeBack;
        	   }
        	}
			if (!runMultiple) { //do only one raid per time strategy is run
        		   keepGoing=false;
        	   }
           }
           long retTime=getNextReturnTime(targetNodes.size());
     	   EventLog.log(String.format("Strategy %s done for now: Next run in %s minutes at rate %s", getDesc(), Long.toString((retTime-System.currentTimeMillis())/(long) 60000), Double.toString(rate)));
    	   return new TimeWhenRunnable(retTime);
            //*****fofo end
        } finally {
            NDC.pop();
        }
    }

    private long getNextReturnTime(int noOfTargets) { //find the soonest return time from a target. fofo
    	long returnTime = util.NotInTheNearFuture();
    	for (int i=1;i<=noOfTargets;i++) { //nextVillage gets increased before time is put in targetReturnTimes. fofo
    		Long temp=targetReturnTimes.get(i);
    		if (temp != null) {
    			long tt = (temp - System.currentTimeMillis())/(long) 60000; // get minutes for log
    			if (tt>0) {
    				EventLog.log(String.format("Raid nr %d return in %s minutes", i, Long.toString(tt)));
        			}
    			else {
    				log.info(String.format("Raid nr %d returned  %s minutes ago", i, Long.toString(-tt)));
    			}
    			if ((temp < returnTime) && (temp > System.currentTimeMillis())) { //dont use return times from raids that have returned already. fofo
    				returnTime=temp;
    			}
    		}
    	}
    	return returnTime;
    }
    
    private TroopTypeMap getTroopsToSend (TroopTypeMap availablePerType, String defaultMovement, String defaultSpy) {
	    //start check troops
        TroopTypeMap toSend = new TroopTypeMap();
        boolean sendable = true;
        int totTroops = 0;
        
        List<SubnodeConfiguration> troopsNodes = super.config.configurationsAt("/troops");
        for (SubnodeConfiguration troopsNode : troopsNodes) {
            boolean enabled = troopsNode.getBoolean("/@enabled", true); // Enabled by default
            if (!enabled) {
                continue;
            }
            String type = troopsNode.getString("/@type", null);
            if (type == null) {
                log.error("Missing \"type\" attribute in strategy \"" + super.getDesc() + "\"");
                continue;
            }
            String fullkey = util.getTranslator().getKeyword(type); // romans.troop1
            String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
            TroopType troopType = TroopType.fromString(typeKey);
            //EventLog.log(String.format("config troop %s TroopType %s", type, troopType.toString()));
            int val = troopsNode.getInt("/", 0);
            boolean allowLess = troopsNode.getBoolean("/@allowLess", false);
            Integer available = availablePerType.get(troopType);
            log.info(String.format("found troop %s %d present",troopType.toString(),available));
            // Check if we have enough troops
            if (val > available && !allowLess) {
                sendable = false;
                break;
            }
            // Check if we can send at least min troops
            String minimum = troopsNode.getString("/@min", "0");
            int min = 0;
            boolean percent = false;
            if (minimum.endsWith("%")) {
                percent = true;
                minimum = minimum.replace("%", "");
            }
            try {
                min = Integer.parseInt(minimum);
            } catch (NumberFormatException e) {
                throw new FatalException(String.format("Invalid numeric value for %s: \"%s\"", type, minimum));
            }
            if (percent) {
                min = (val * min) / 100;
            }
            if (available < min) {
                sendable = false;
                break;
            }
            // Randomise
            // Accept both "randomise" and "randomize", with "true" as default
            boolean randomise = troopsNode.getBoolean("/@randomise", troopsNode.getBoolean("/@randomize", true));
            if (randomise) {
                int maxDelta = (int) (val * RANDOMISE_RATIO);
                if (!allowLess) {
                    // value can't be less than what specified
                    val = val + random.nextInt(maxDelta + 1);
                } else {
                    // value can be +- randVal
                    val = val - maxDelta + random.nextInt(2 * maxDelta + 1);
                }
            }
            // Add troops to send
            val = val > available ? available : val; // Upper limit
            val = val < min ? min : val; // Lower limit
            toSend.put(troopType, val);
            totTroops += val;
        }
        
        if (sendable == false || totTroops == 0) {
            //EventLog.log("Not enough troops");
            return null;
        }
        return toSend;
        // end check troops
    }
    
    public boolean modifiesResources() {
        return false;
    }
//   public String createId(SubnodeConfiguration strategyConfig) {
//      // Coordinates are not enough to identify a strategy, because one could send many different attacks
//      // to the same village. We therefore use desc (from super) and troop types as well.
//      StringBuffer result = new StringBuffer(super.createId(strategyConfig));
//      // Target coordinates
//         //String x = strategyConfig.getString("/target/@x");
//         //String y = strategyConfig.getString("/target/@y");
//         //result.append("#").append(x).append("#").append(y);
//      // Targets
//      List<SubnodeConfiguration> troopsNodes = strategyConfig.configurationsAt("/troops");
//      for (SubnodeConfiguration troopsNode : troopsNodes) {
//         String type = troopsNode.getString("/@type");
//         result.append("#").append(type);
//      }
//      return result.toString();
//   }
}


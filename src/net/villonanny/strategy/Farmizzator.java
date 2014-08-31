package net.villonanny.strategy;

import java.util.List;
import java.util.Random;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.RallyPoint;
import net.villonanny.entity.Valley;
import net.villonanny.misc.Coordinates;
import net.villonanny.type.TroopTransferType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 *
 */
public class Farmizzator extends Strategy {

    private final static Logger log = Logger.getLogger(Farmizzator.class);
    private int totErrors = 0;
    private final int MAX_ERRORS = 5;
    private final double RANDOMISE_RATIO = 0.1; // +-10%
    private Random random = new Random();

    public TimeWhenRunnable execute() throws ConversationException {
        // <strategy class="Farmizzator" desc="DESCRIPTION" enabled="true">
        //   <target x="XCOORD" y="YCOORD" village="TARGET_VILLAGE" movement="REINFORCE|RAID|ATTACK" spy="RESOURCES|DEFENSES" rate="RATE" item="CATA_TARGET1, CATA_TARGET2"/>
        //   <troops type="TROOP_NAME" allowLess="ALLOWLESS" min="MINIMUM_TROOPS" randomise="RANDOMISE" enabled="true">TROOP_AMOUNT</troops>
        //   <minPauseMinutes>MIN_PAUSE</minPauseMinutes>
        // </strategy>
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());

        // TODO Per le volte successive:
        // TODO Controlla il report dell'attacco (difficile!)
        // TODO Se sono state perse troppe truppe, cancella questa strategy

        try {
            // EventLog.log("checking reports for 92,-95 d=396988&c=50 = " + util.getMapIdFromPosition("92","-95"));
            // ReportMessageReader.getInstance().getAverageBounty(util.getMapIdFromPosition("92","-95"));
            // EventLog.log("Bounty "+ReportMessageReader.getInstance().getLastBounty(util.getServerId(),util.getMapIdFromPosition("92","-95")));
            // EventLog.log("Average Bounty "+ReportMessageReader.getInstance().getAverageBounty(util, "92","-95"));
            // EventLog.log("Last Bounty "+ReportMessageReader.getInstance().getLastBounty(util.getServerId(),util.getMapIdFromPosition("92","-95")));
            // System.exit(0);

            RallyPoint rallyPoint = village.getRallyPoint();
            if (rallyPoint == null) {
                log.debug("No rally point");
                int minPauseMinutes = super.config.getInt("/minPauseMinutes", 25);
                minPauseMinutes = super.config.getInt("/@minPauseMinutes", minPauseMinutes);	// support on strategy line as well
                return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
            }
            TroopTypeMap availablePerType = rallyPoint.fetchSendableTroops(super.util, false);
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
                int val = troopsNode.getInt("/", 0);
                boolean allowLess = troopsNode.getBoolean("/@allowLess", false);
                Integer available = availablePerType.get(troopType);
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
            int minPauseMinutes = super.config.getInt("/minPauseMinutes", 5);
            minPauseMinutes = super.config.getInt("/@minPauseMinutes", minPauseMinutes);	// support on strategy line as well
            if (sendable == false || totTroops == 0) {
                EventLog.log("Not enough troops");
                return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
            }
            Coordinates coord = new Coordinates(config, "target"); // Handles "travian style" coords
            String x = coord.getX();
            String y = coord.getY();
            String village = super.config.getString("/target/@village", "");
            String movement = super.config.getString("/target/@movement", "attack");
            String fullkey = "movement." + translator.getKeyword(movement, "movement"); // movement.attack
            TroopTransferType transferType = TroopTransferType.fromKey(fullkey);
            String itemString1 = super.config.getString("/target/@item[1]", null);
            String itemString2 = super.config.getString("/target/@item[2]", null);
            TroopTransferType spyType = null;
            String spyTypeString = super.config.getString("/target/@spy", null);
            if (spyTypeString != null) {
                fullkey = "movement." + translator.getKeyword(spyTypeString, "movement"); // movement.spy.resources
                spyType = TroopTransferType.fromKey(fullkey);
            } // check for previous bounty if not spying
            else if ((!x.equals("")) && (!y.equals(""))) {
                // log.debug("checking reports for " + x + "," + y + " id " + util.getMapIdFromPosition(x,y));
                // ReportMessageReader.getInstance().getAverageBounty(util.getServerId(),util.getMapIdFromPosition(x,y));
                // log.debug("Average Bounty " + ReportMessageReader.getInstance().getAverageBounty(util, x, y));
            	Valley v = new Valley(util, x, y);
                log.debug("Average Bounty " + v.getAverageBounty());
                // EventLog.log("Last Bounty "+ReportMessageReader.getInstance().getLastBounty(util.getServerId(),util.getMapIdFromPosition( x, y)));
            }

            super.village.gotoMainPage(); // Ensure you do it from the right village (someone might have clicked to a different village meanwhile)
            int secondsToArrive;
            try {
                secondsToArrive = rallyPoint.sendTroops(util, x, y, village, toSend, transferType, new String[]{itemString1, itemString2}, spyType);
                totErrors = 0;
            } catch (ConversationException e) {
                log.error(e);
                totErrors++;
                if (totErrors <= MAX_ERRORS) {
                    EventLog.log(String.format("Strategy has error; retrying later (%s left)", MAX_ERRORS - totErrors));
                    util.shortestPause(false); // Just to be safe
                    return new TimeWhenRunnable(System.currentTimeMillis() + minPauseMinutes * Util.MILLI_MINUTE); // Try again later
                } else {
                    log.error("Strategy has too many errors; disabling", e);
                    EventLog.log("Strategy has too many errors; disabling");
                    return TimeWhenRunnable.NEVER;
                }
            }
            if (secondsToArrive > 0) {
	            log.info(String.format("Strategy %s done for now", getDesc()));
	            double rate = Math.max(super.config.getDouble("/target/@rate", 1), Double.MIN_VALUE);
	            double raidTime = (2 * secondsToArrive * Util.MILLI_SECOND);
	            long waitMillis = (long) (raidTime / rate);
	            long timeTemp = waitMillis / 60000;
	            EventLog.log(String.format("Strategy %s done for now: Next run in %s minutes at rate %s", getDesc(), Long.toString(timeTemp), Double.toString(rate)));
	            long MyTimeToRun = System.currentTimeMillis() + waitMillis;
	            return new TimeWhenRunnable(MyTimeToRun);
            } else {
            	log.error("Error in Farmizzator");
            	EventLog.log(String.format("Strategy %s disabled on error", getDesc()));
            	return new TimeWhenRunnable(false);
            }
        } finally {
            NDC.pop();
        }
    }

    public boolean modifiesResources() {
        return false;
    }
//	public String createId(SubnodeConfiguration strategyConfig) {
//		// Coordinates are not enough to identify a strategy, because one could send many different attacks
//		// to the same village. We therefore use desc (from super) and troop types as well.
//		StringBuffer result = new StringBuffer(super.createId(strategyConfig));
//		// Target coordinates
//		String x = strategyConfig.getString("/target/@x");
//		String y = strategyConfig.getString("/target/@y");
//		result.append("#").append(x).append("#").append(y);
//		// Targets
//		List<SubnodeConfiguration> troopsNodes = strategyConfig.configurationsAt("/troops");
//		for (SubnodeConfiguration troopsNode : troopsNodes) {
//			String type = troopsNode.getString("/@type");
//			result.append("#").append(type);
//		}
//		return result.toString();
//	}
}

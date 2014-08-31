package net.villonanny.type;

import java.util.EnumMap;
import java.util.List;
import java.util.Random;

import net.villonanny.FatalException;
import net.villonanny.strategy.FarmRotator;
import net.villonanny.Util;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;

/**
 * A map of Integers with TroopType as key.
 * For each type of troop, holds the amount.
 */
public class TroopTypeMap extends EnumMap<TroopType, Integer> {
    private final static Logger log = Logger.getLogger(TroopTypeMap.class);
    private final double RANDOMISE_RATIO = 0.1; // +-10%
    private Random random = new Random();			// used to parse config params

	public TroopTypeMap() {
		super(TroopType.class);
		init();
	}

	public TroopTypeMap(TroopTypeMap m) {
		super(m);
		init();
	}
	
	private void init() {
		// Initialise with zero to avoid nullpointers when auto(un)boxing from Integer
		for (TroopType type : TroopType.values()) {
			put(type, 0);
		}
	}
	

	
	/**
	 * @return the sum of all values, which is the total resources contained in this map
	 */
	public int getSumOfValues() {
		int tot = 0;
		for (int val : values()) {
			tot += val;
		}
		return tot;
	}
	
	/**
	 * parse config to set values
	 * @param util
	 * @param strategy	name of calling strategy for any error messages
	 * @param config	config block containing repeated troops entries
	 * @param availablePerType	map of available troops to check min against
	 * @return
	 */
	public int	getConfigValues(Util util, String strategy, SubnodeConfiguration config, TroopTypeMap availablePerType) {
        // String coordsTravianStyle = config.getString("/" + tagName + "/@coords", null); // e.g. /target/@coords
        int totTroops = 0;
        // need tagname not strategy or just get troops?
        // String defaultMovement = config.getString("/strategy/@movement", "normal"); // Default is "normal" unless specified on strategy node
        // String defaultSpy = config.getString("/strategy/@spy", null);
        List<SubnodeConfiguration> troopsNodes = config.configurationsAt("/troops");
        for (SubnodeConfiguration troopsNode : troopsNodes) {
            boolean enabled = troopsNode.getBoolean("/@enabled", true); // Enabled by default
            if (!enabled) {
                continue;
            }
            String type = troopsNode.getString("/@type", null);
            if (type == null) {
                log.error("Missing \"type\" attribute in strategy \"" + strategy + "\"");
                continue;
            }
            String fullkey = util.getTranslator().getKeyword(type); // romans.troop1
            String typeKey = fullkey.substring(fullkey.indexOf(".") + 1);
            TroopType troopType = TroopType.fromString(typeKey);
            int val = troopsNode.getInt("/", 0);
            boolean allowLess = troopsNode.getBoolean("/@allowLess", false);
            Integer available = availablePerType.get(troopType);
            // Check if we have enough troops
            if ((val > available) && !allowLess) {
            	// don't send any, if want different behaviour simply break out of loop or continue to next troop
            	log.debug(available+" Less than Specified Troops "+val);
            	return 0;
                // break;
            	// continue;
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
            	// don't send any - if any type less than min, if want different behaviour simply break out of loop
            	log.debug(available+" Less than Minimum Troops "+min);
            	return 0;
                // break;
            	// continue;
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
            this.put(troopType, val);
            totTroops += val;
            log.trace("Adding "+val+" "+type+" Total "+totTroops);
        }	
		return totTroops;		// return no to send
	}


//	@Override
//	public String toString() {
////		StringBuffer result ? n
////		return String.format("[%s|%s|%s|%s", );
//		return "TODO"; // TODO
//	}
	
	
	
	
}

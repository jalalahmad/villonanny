package net.villonanny.type;

import java.util.Hashtable;
import java.util.Map;

public enum TroopTransferType {
	// First value must match language configuration keys, like in "key.movement.normal = normale"
	// Second value must match HTML post value
	REINFORCE("movement.reinforcement", "2"),
	ATTACK("movement.normal", "3"),
	RAID("movement.raid", "4"),
	SPY_RESOURCES("movement.spy.resources", "1"),
	SPY_DEFENSES("movement.spy.defenses", "2")
	;
    private final String key;
	private final String htmlValue;
	private final static Map<String, TroopTransferType> fromStringMap = new Hashtable<String, TroopTransferType>();

	static {
		TroopTransferType[] values = TroopTransferType.values();
		for (int i = 0; i < values.length; i++) {
			TroopTransferType m = values[i];
			fromStringMap.put(m.key.toLowerCase(), m);
		}
	}

	private TroopTransferType(String key, String htmlValue) {
		this.key = key;
		this.htmlValue = htmlValue;
	}

	public String toString() {
		return key;
	}

	public static TroopTransferType fromInt(int num) {
		return TroopTransferType.values()[num];  // Starts at 0
	}
	
	public int toInt() {
		return ordinal(); // Starts at 0
	}

	/**
	 * Returns the TroopTransferType given the key
	 * @param key the language key (case insensitive), like "movement.normal"
	 * @return
	 */
	public static TroopTransferType fromKey(String key) {
		if (key==null) {
			return null;
		}
		String lowKey = key.toLowerCase();
		TroopTransferType result = fromStringMap.get(lowKey);
//		if (result==null) {
//			result = fromStringSearch(lowKey);
//			if (result!=null) {
//				fromStringMap.put(lowKey, result);
//			}
//		}
		return result;
	}
	
//	private static TroopTransferType fromStringSearch(String lowKey) {
//		for (TroopTransferType type : TroopTransferType.values()) {
//			if (type.key.equalsIgnoreCase(lowKey)) {
//				return type;
//			}
//		}
//		return null;
//	}

	public String getHtmlValue() {
		return htmlValue;
	}	
}
package net.villonanny.type;

import java.util.Hashtable;
import java.util.Map;

public enum PartyType {
    SMALLPARTY("smallParty"),
    BIGPARTY("bigParty");

    private final String stringValue;
	// This map is lazily initialised because I can't see other ways
	private final static Map<String, PartyType> fromStringMap = new Hashtable<String, PartyType>();
	
	private PartyType(String name) {
		this.stringValue = name;
//		PartyType.fromStringMap.put(name, this); // Gives compile error
	}
	
	public String toString() {
		return stringValue;
	}

	public static PartyType fromInt(int num) {
		return PartyType.values()[num];  // Starts at 0
	}
	

	public static PartyType fromString(String name) {
		String lowKey = name.toLowerCase();
		PartyType result = fromStringMap.get(lowKey);
		if (result==null) {
			result = fromStringSearch(lowKey);
			if (result!=null) {
				fromStringMap.put(lowKey, result);
			}
		}
		return result;
	}
	
	private static PartyType fromStringSearch(String name) {
		for (PartyType partyType : PartyType.values()) {
			if (partyType.stringValue.equals(name)) {
				return partyType;
			}
		}
		return null;
	}
	
	public int toInt() {
		return ordinal(); // Starts at 0
	}
}

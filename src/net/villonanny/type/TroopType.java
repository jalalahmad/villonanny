package net.villonanny.type;

import java.util.HashMap;
import java.util.Map;


public enum TroopType {
	// Enum values have a generic TROOPX name until a more specific name becomes useful
	// Strings must match the language config key with no tribe, like in "key.romans.troop2 = ..."
	TROOP1("troop1", BuildingType.BARRACKS, BuildingType.GREAT_BARRACK),	// legionnaire
	TROOP2("troop2", BuildingType.BARRACKS, BuildingType.GREAT_BARRACK),	// praetorian
	TROOP3("troop3", BuildingType.BARRACKS, BuildingType.GREAT_BARRACK),	// imperian
	TROOP4("troop4", BuildingType.STABLE, BuildingType.GREAT_STABLE),	// equitesLegati
	TROOP5("troop5", BuildingType.STABLE, BuildingType.GREAT_STABLE),	// equitesImperatoris
	TROOP6("troop6", BuildingType.STABLE, BuildingType.GREAT_STABLE),	// equitesCaesaris
	TROOP7("troop7", BuildingType.WORKSHOP),	// batteringRam
	CATAPULT("troop8", BuildingType.WORKSHOP),	// fireCatapult
	TROOP9("troop9", BuildingType.RESIDENCE, BuildingType.PALACE),	// senator
	TROOP10("troop10", BuildingType.RESIDENCE, BuildingType.PALACE),	// settler
	HERO("troop11", BuildingType.HEROS_MANSION) // hero
	;
	
	private final BuildingType building, alternativeBuilding;
	private final String key;

	private final static Map<String, TroopType> fromStringMap;
	static {
		fromStringMap = new HashMap<String, TroopType>();
		TroopType[] values = TroopType.values();
		for (int i = 0; i < values.length; i++) {
			TroopType m = values[i];
			fromStringMap.put(m.key.toLowerCase(), m);
		}
	}
	
	private TroopType(String key, BuildingType building) {
		this(key, building, building);
//		TroopType.fromStringMap.put(key, this); // Gives compile error
	}
	
	private TroopType(String key, BuildingType building, BuildingType alternativeBuilding) {
		this.building = building;
		this.alternativeBuilding = alternativeBuilding;
		this.key = key;
	}
	
	public static TroopType fromInt(int num) {
		return TroopType.values()[num]; // Starts at 0
	}
	
	public int toInt() {
		return ordinal(); // Starts at 0
	}
	
	public BuildingType getBuilding(TribeType tribe) {
		// There are 2 exceptions to the general rules
		if (this.equals(TROOP3) && tribe.equals(TribeType.GAULS)) {
			return BuildingType.STABLE; // troop3 for Gauls (Pathfinder) is a horse -> Stable
		}
		if (this.equals(TROOP4) && tribe.equals(TribeType.TEUTONS)) {
			return BuildingType.BARRACKS; // troop4 for Teutons (Scout) is a human -> Barracks
		}
		return building;
	}
	
	public BuildingType getAlternativeBuilding(TribeType tribe) {
		// There are 2 exceptions to the general rules
		if (this.equals(TROOP3) && tribe.equals(TribeType.GAULS)) {
			return BuildingType.GREAT_STABLE; // troop3 for Gauls (Pathfinder) is a horse -> Stable
		}
		if (this.equals(TROOP4) && tribe.equals(TribeType.TEUTONS)) {
			return BuildingType.GREAT_BARRACK; // troop4 for Teutons (Scout) is a human -> Barracks
		}
		return alternativeBuilding;
	}

	public static TroopType getLastValue() {
		int totValues = values().length;
		return values()[totValues-1];
	}

	/**
	 * Returns the TroopType given the key
	 * @param key the language key without tribe (case insensitive), like "troop2"
	 * @return
	 */
	public static TroopType fromString(String key) {
		String lowKey = key.toLowerCase();
		TroopType result = fromStringMap.get(lowKey);
//		if (result==null) {
//			result = fromStringSearch(lowKey);
//			if (result!=null) {
//				fromStringMap.put(lowKey, result);
//			}
//		}
		return result;
	}
	
//	private static TroopType fromStringSearch(String lowKey) {
//		for (TroopType troopType : TroopType.values()) {
//			if (troopType.key.equalsIgnoreCase(lowKey)) {
//				return troopType;
//			}
//		}
//		return null;
//	}
}
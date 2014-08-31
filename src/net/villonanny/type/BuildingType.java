package net.villonanny.type;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.Translator;
import net.villonanny.entity.Building;
import net.villonanny.entity.EmptySite;
import net.villonanny.entity.Field;
import net.villonanny.entity.MarketSite;
import net.villonanny.entity.RallyPoint;
import net.villonanny.entity.TownHallSite;
import net.villonanny.entity.TrainerSite;
import net.villonanny.entity.UpgradeSite;
import net.villonanny.entity.AcademySite;
import net.villonanny.entity.UpgradeableSite;


public enum BuildingType implements LocalizedType {
	// String values must match language configuration keys, like in "key.cropland = campo di grano"
    EMPTYSITE("emptySite", EmptySite.class),
    // WallEmptySite and RallyPointEmptySite are handled as exceptions to avoid the use of more configuration keys
    WOODCUTTER("woodcutter", Field.class),
    CLAY_PIT("clayPit", Field.class),
    IRON_MINE("ironMine", Field.class),
    CROPLAND("cropland", Field.class),
    SAWMILL("sawmill"),
    BRICKYARD("brickyard"),
    IRON_FOUNDRY("ironFoundry"),
    GRAIN_MILL("grainMill"),
    BAKERY("bakery"),
    WAREHOUSE("warehouse"),
    GRANARY("granary"),
    BLACKSMITH("blacksmith", UpgradeSite.class),
    ARMOURY("armoury", UpgradeSite.class),
    TOURNAMENT_SQUARE("tournamentSquare"),
    MAIN_BUILDING("mainBuilding"),
    RALLY_POINT("rallyPoint", RallyPoint.class),
    MARKETPLACE("marketplace", MarketSite.class),
    EMBASSY("embassy"),
    BARRACKS("barracks", TrainerSite.class),
    STABLE("stable", TrainerSite.class),
    WORKSHOP("workshop", TrainerSite.class),
    ACADEMY("academy", AcademySite.class),
    CRANNY("cranny"),
    CITY_HALL("cityHall", TownHallSite.class),
    RESIDENCE("residence", TrainerSite.class),
    PALACE("palace", TrainerSite.class),
    TREASURE_CHAMBER("treasureChamber"),
    TRADE_OFFICE("tradeOffice"),
    GREAT_BARRACK("greatBarrack", TrainerSite.class),
    GREAT_STABLE("greatStable", TrainerSite.class),
    CITY_WALL("cityWall"),
    EARTH_WALL("earthWall"),
    PALISADE("palisade"),
    STONEMASON("stonemason"),
    BREWERY("brewery"),
    TRAPPER("trapper"),
    HEROS_MANSION("herosMansion"),
    HORSE_DRINKING_THROUGH("horseDrinkingTrough"),
    GREAT_GRANARY("greatGranary"),
    GREAT_WAREHOUSE("greatWarehouse"),
    WONDER_OF_THE_WORLD("wonderOfTheWorld")
	;

    private final String key;
    private final Constructor<? extends UpgradeableSite> classConstructor;

	private final static Map<String, BuildingType> fromStringMap;
	static {
		fromStringMap = new HashMap<String, BuildingType>();
		BuildingType[] values = BuildingType.values();
		for (int i = 0; i < values.length; i++) {
			BuildingType m = values[i];
			fromStringMap.put(m.key.toLowerCase(), m);
		}
	}

	
	private BuildingType(String key) {
		this(key, Building.class);
	}
	
	private BuildingType(String key, Class<? extends UpgradeableSite> theClass) {
		this.key = key;
		// BuildingType.fromStringMap.put(key, this); // Gives compile error
		try {
			this.classConstructor = theClass.getConstructor(new Class[]{String.class, String.class, Translator.class});
		} catch (Exception e) {
			throw new FatalException(e);
		}
	}
	
	public String toString() {
		return key;
	}

	public static BuildingType fromInt(int num) {
		return BuildingType.values()[num];  // Starts at 0
	}
	
	public int toInt() {
		return ordinal(); // Starts at 0
	}

	/**
	 * Returns the BuildingType given the key
	 * @param key the language key (case insensitive), like "woodcutter"
	 * @return
	 */
	public static BuildingType fromKey(String key) {
		if (key==null) {
			return null;
		}
		String lowKey = key.toLowerCase();
		BuildingType result = fromStringMap.get(lowKey);
		if (result == null) {
			EventLog.log("msg.notBuildingKey", BuildingType.class, key);
		}
		return result;
	}
	
	public String getLanguageKey() {
		return key;
	}
	
	public UpgradeableSite getInstance(String name, String urlString, Translator translator) {
		try {
			return this.classConstructor.newInstance(new Object[]{name, urlString, translator});
		} catch (Exception e) {
			throw new FatalException(e);
		}
	}

	public boolean isField() {
		return this == WOODCUTTER
			|| this == CLAY_PIT
			|| this == IRON_MINE
			|| this == CROPLAND
			;
	}
	
}

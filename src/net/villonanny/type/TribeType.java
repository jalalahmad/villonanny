package net.villonanny.type;

import java.util.HashMap;
import java.util.List;

import net.villonanny.InvalidConfigurationException;
import net.villonanny.Translator;

public enum TribeType {
	// String values must match language keyword, e.g. key.romans = Romer
	ROMANS("romans"),
	GAULS("gauls"),
	TEUTONS("teutons") 
	;
	// This is needed to convert from String to enum
	private static HashMap<String, TribeType> fromStringMap;
	
	static {
		fromStringMap = new HashMap<String, TribeType>();
		TribeType[] values = TribeType.values();
		for (int i = 0; i < values.length; i++) {
			TribeType m = values[i];
			fromStringMap.put(m.stringValue.toLowerCase(), m);
		}
	}
	private final String stringValue;

	private TribeType(String name) {
		this.stringValue = name;
	}
	
	public String toString() {
		return stringValue;
	}

	public static TribeType fromInt(int num) {
		return TribeType.values()[num]; // Starts at 0
	}
	
	public int toInt() {
		return ordinal();
	}
	
	/**
	 * Convert from string to TribeType
	 * @param tribe the tribe ("romans", "gauls", "teutons")
	 * @return
	 */
	public static TribeType fromString(String tribe) {
		if (tribe==null) {
			return null;
		}
		return fromStringMap.get(tribe.toLowerCase());
	}
	
	/**
	 * Convert a localised tribe name into a TribeType. Better than using Translator.getKeyword() because it isn't fooled by aliases
	 * in other keywords.
	 * @param tribeName
	 * @param translator
	 * @return
	 * @throws InvalidConfigurationException
	 */
	public static TribeType fromLanguageValue(String tribeName, Translator translator) throws InvalidConfigurationException {
		TribeType[] values = TribeType.values();
		for (int i = 0; i < values.length; i++) {
			TribeType m = values[i];
			List<String> langValues = translator.getAll(m.stringValue);
			if (langValues !=null && langValues.size()>0) {
				for (String value : langValues) {
					if (value.equalsIgnoreCase(tribeName)) {
						return m;
					}
				}	
			}
		}
		throw new InvalidConfigurationException("Invalid tribe: \"" + tribeName + "\"");
	}
}

package net.villonanny;

import java.util.Iterator;
import java.util.List;

import net.villonanny.type.BuildingType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

public class Translator {
	Logger log = LoggerFactory.getLogger(this.getClass());
	public static String LEVEL = "level";
	public static String FOOD_SHORTAGE = "foodShortage";
	private String languageCode;
	private ConfigManager configManager;

//	private Map<String, String> translation = new HashMap<String, String>(); // tag -> translation

	public Translator(ConfigManager newConfigManager) {
		this(null, newConfigManager);
	}
	
	public Translator(String language, ConfigManager newConfigManager) {
		this.configManager = newConfigManager;
		this.languageCode = language;
	}
	
	/**
	 * Tells if an item is a field
	 * @param text the travian localised name of the item
	 * @return true if field
	 */
	public boolean isField(String text) {
		String keywordForText = getKeyword(text);
		BuildingType fieldType = BuildingType.fromKey(keywordForText);
		return (fieldType != null && fieldType.isField());
	}

//	private void setTag(String language, String tag) {
//		String value = ConfigManager.getString(language + "/key/" + tag);
//		if (value!=null) {
//			translation.put(tag, value.toLowerCase());
//		}
//	}

	/**
	 * Translate from keyword to travian localised string.
	 * If there are many values for the key, only the first one is returned.
	 * @param keyword the language keyword, without the "key" part
	 * @return
	 */
	public String getFirst(String keyword) {
		String result = configManager.getString(languageCode + "/key/" + keyword.replace('.', '/'));
		if (result==null) {
			String msgSkel = Util.getLocalMessage("msg.noLanguageKeyword", this.getClass());
			String message = MessageFormatter.format(msgSkel, languageCode, "key."+keyword);
			throw new TranslationException(message);
		}
		return result;
	}
	
	/**
	 * Returns a pattern that matches all aliases of the given keyword
	 * @param keyword
	 * @return a string like (?:xxx|yyy|zzz) or just xxx
	 */
	public String getMatchingPattern(String keyword) {
		List<String> words = this.getAll(keyword);
		StringBuffer result = new StringBuffer();
		if (words.size()==1) {
			result.append(words.get(0));
		} else {
			result.append("(?:");
			for (String word : words) {
				result.append(word + "|");
			}
			result.deleteCharAt(result.length()-1);
			result.append(")");
		}
		return result.toString();
	}
	
	/**
	 * Translate from keyword to travian localised strings.
	 * Returns all values defined for the given key, like in "key.romans.troop1 = Combattente, Bastoni"
	 * @param keyword the language keyword, without the "key" part
	 * @return All values associated with the key (if any)
	 */
	public List<String> getAll(String keyword) {
		List<String> result = configManager.getStringList(languageCode + "/key/" + keyword.replace('.', '/'));
		if (result==null || result.size()==0) {
			String msgSkel = Util.getLocalMessage("msg.noLanguageKeyword", this.getClass());
			String message = MessageFormatter.format(msgSkel, languageCode, "key."+keyword);
			throw new TranslationException(message);
		}
		return result;
	}

	public String getKeyword(String valueToFind) {
		return getKeyword(valueToFind, null);
	}

	/**
	 * Find the keyword for a given value, also considering synonyms when defined. 
	 * By specifying a non-null prefix, you confine the search to a subset of the configuration.
	 * @param valueToFind the value to find, like "Legionnaire" in "key.romans.troop1 = Legionnaire"
	 * @param prefix the initial part of the keyword after "key.", like "romans" in "key.romans.troop1"
	 * @return the last part of the keyword, like "troop1" in the above example
	 */
	public String getKeyword(String valueToFind, String prefix) {
		if (valueToFind==null) {
			return null;
		}
		Iterator<String> allKeys = configManager.getLanguageKeys(languageCode, prefix);
		while (allKeys.hasNext()) {
			String key = allKeys.next().replace('.', '/');
			if (key.length()==0) {
				continue;
			}
			List<String> values = configManager.getStringList(languageCode + "/key/" + (prefix!=null?prefix+"/":"")+ key);
			if (values !=null && values.size()>0) {
				for (String value : values) {
					if (value.equalsIgnoreCase(valueToFind)) {
						return key.replace('/', '.');
					}
				}	
			}
		}
		log.error("Value \"" + valueToFind + "\" not found in section \"" + prefix + "\" of language file \"" + languageCode + "\"");
		String msgSkel = Util.getLocalMessage("msg.noLanguageValue", this.getClass());
		String message = MessageFormatter.format(msgSkel, languageCode, valueToFind);
		throw new TranslationException(message);
	}

	public void setLanguage(String languageCode) {
		this.languageCode = languageCode;
	}
	
	public String getLanguage() {
		return languageCode;
	}

}

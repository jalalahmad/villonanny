package net.villonanny.entity;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.type.LocalizedType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.log4j.Logger;

public class ConstructionData {
	private final static Logger log = Logger.getLogger(ConstructionData.class);
	private String name; // "miniera di ferro livello 1"
	private String descriptionUrlString;
	private String submitUrlString;
	private ResourceTypeMap neededResources; // Wood, Clay, Iron, Crop, Food
	private int currentLevel=-1;
	private int nextLevel;
	private int secondsForNextLevel;
	private boolean upgradeable = true;
	private boolean foodShortage = false;
	protected Translator translator;
//	private String typeString; // e.g. "miniera di ferro"
	private boolean maxLevelReached = false;
	private LocalizedType type;
	
	public ConstructionData(String name, String urlString, Translator translator) {
		super();
		this.name = name;
		this.descriptionUrlString = urlString;
		this.neededResources = new ResourceTypeMap();
		this.translator = translator;
	}
	
//	public String getTypeString() { 
//		return typeString; // e.g. "miniera di ferro"
//	}
	
//	public void setTypeString(String typeString) {
//		this.typeString = typeString;
//	}
	
//	public void setTypeString(LocalizedType type) {
//		setTypeString(translator.get(type.getLanguageKey()));
//	}

	public int getFoodNeeded() {
		return neededResources.get(ResourceType.FOOD);
	}
	
	public void setNeededResource(ResourceType type, int amount) {
		neededResources.put(type, amount);
	}

	/**
	 * Return true if the item can be posted for upgrade
	 * @return
	 */
	public boolean isSubmittable() {
		return submitUrlString != null;
	}
	
	public int getTotResourcesNeeded() {
		return neededResources.getSumOfValues();
	}

	public int getCurrentLevel() {
		return currentLevel;
	}

	public void setCurrentLevel(int currentLevel) {
		this.currentLevel = currentLevel;
	}

	public void setCurrentLevel(String currentLevel) throws ConversationException {
		try {
			this.currentLevel = Integer.parseInt(currentLevel);
		} catch (NumberFormatException e) {
			throw new ConversationException("Invalid level for " + name + ": " + currentLevel, e);
		}
	}

	public int getNextLevel() {
		return nextLevel;
	}

	public void setNextLevel(int nextLevel) {
		this.nextLevel = nextLevel;
	}

	public int getSecondsForNextLevel() {
		return secondsForNextLevel;
	}

	public void setSecondsForNextLevel(int secondsForNextLevel) {
		this.secondsForNextLevel = secondsForNextLevel;
	}

	public String getName() {
		return name;
	}

	public String getUrlString() {
		return descriptionUrlString;
	}

	public String getSubmitUrlString() {
		return submitUrlString;
	}

	public void setSubmitUrlString(String submitUrlString) {
	    // replace "&amp;" with "&"
	    if(submitUrlString != null) {
	         //log.debug("SUS1: " + submitUrlString);
	         submitUrlString = submitUrlString.replaceAll("&amp;", "&");
	         //log.debug("SUS2: " + submitUrlString);
	    }
		this.submitUrlString = submitUrlString;
	}
	
	public ResourceTypeMap getNeededResources() {
		return neededResources;
	}

	public void setUpgradeable(boolean upgradeable) {
		this.upgradeable = upgradeable;
	}

	public void setFoodShortage(boolean foodShortage) {
		this.foodShortage = foodShortage;
	}

	/**
	 * Return true if there are enough resources to upgrade and max level has not been reached;
	 * true doesn't mean this item is submittable.
	 * @return
	 */
	public boolean isUpgradeable() {
		return upgradeable;
	}
	
	public Date upgrade(Util util) throws ConversationException {
		 // Simulate return to field page
		 util.httpGetPage(getUrlString());
		 // Upgrade
		 if (!isSubmittable()) {
			 log.warn("Not submittable: " + getName());
			 return new Date();
		 }
		 String page = util.httpGetPage(getSubmitUrlString());
		 // Find completion time
		 return util.getCompletionTime(getCompletionTimeString(util, page));
	}

	public String getCompletionTimeString(Util util, String page) throws ConversationException {
		// TODO this element is also available on the upgrade page and should be taken from there
		Pattern p;
		Matcher m;
		String localNamePattern = translator.getMatchingPattern(type.getLanguageKey());
		// <img src="img/un/a/del.gif" width="12" height="12" title="interrompi"></a></td><td>Segheria (livello 2)</td><td><span id=timer1>0:00:30</span> h.</td><td>Terminato alle 17:53</span>
		// p = Pattern.compile(Util.P_FLAGS + "<img src=\".*?img/un/a/del.gif\".*?<td>" + localNamePattern + " \\(.*?<span id=timer.*?>(\\d?\\d:\\d?\\d:\\d?\\d)</span> ");
		p = util.getPattern("constructionData.completionTimeString", localNamePattern);
		m = p.matcher(page);
		if (m.find()) {
			return m.group(1).trim();
		} else {
			util.saveTestPattern("CompletionTimeString", p, page);
			throw new ConversationException("Can't find completion time ");
		}
	}
	
	public boolean getFoodShortage() {
		return foodShortage;
	}
	
	public String toString() {
		return name +
			" (" + 
			neededResources.toString() +
			")" +
		    " [upgradeable=" + upgradeable +  ", foodShortage=" + foodShortage + ", url=" + submitUrlString + "]";
	}

	public Translator getTranslator() {
		return translator;
	}

	public boolean isMaxLevelReached() {
		return maxLevelReached;
	}

	public void setMaxLevelReached(boolean maxLevelReached) {
		this.maxLevelReached = maxLevelReached;
		if (maxLevelReached) {
			setUpgradeable(false);
		}
	}

	public LocalizedType getType() {
		return type;
	}

	public void setType(LocalizedType type) {
		this.type = type;
	}
	
}

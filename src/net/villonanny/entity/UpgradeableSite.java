package net.villonanny.entity;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.type.LocalizedType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.log4j.Logger;

public abstract class UpgradeableSite {
	private final static Logger log = Logger.getLogger(UpgradeableSite.class);
	private ConstructionData constructionData;
	public Translator translator;

	protected UpgradeableSite(String name, String urlString, Translator translator) {
		this.translator = translator;
		constructionData = new ConstructionData(name, urlString, translator);
	}
	
	public LocalizedType getType() {
		return constructionData.getType();
	}

	public void setType(LocalizedType type) {
		constructionData.setType(type);
	}

	public void fetch(Util util) throws ConversationException {
		setUpgradeable(true);
		setFoodShortage(false);
		constructionData.setMaxLevelReached(false);
		
		Pattern p;
		Matcher m;
		String page = util.httpGetPage(getUrlString());
		// Level
		String level = translator.getMatchingPattern(Translator.LEVEL);
		// p = Pattern.compile(String.format("(?s)(?i)<h1><b>(.*?) %s (\\d+)</b></h1>", level));
		p = util.getPattern("upgradeableSite.level", level);
		m = p.matcher(page);
		if (m.find()) {
//			setTypeString(m.group(1)); Not needed anymore because typeString set when village fetches all slots [xtian]
			setCurrentLevel(m.group(1));
		} else {
			util.saveTestPattern("upgradeableSite.level", p, page);
			throw new ConversationException("Can't find level for \"" + this.getName() + "\"");
		}
		// check if already building by looking for a next level
		p = util.getPattern("upgradeableSite.nextLevel", level);
		m = p.matcher(page);
		if (m.find()) {
			/* 
            EventLog.log(m.groupCount()+" upgradeableSite.nextLevel "+m.group(0));
			String dInfo = "";
            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
            EventLog.log("upgradeableSite.nextLevel: "+dInfo);
			*/
			String nextLevel = m.group(1);
			if (Integer.valueOf(nextLevel) >  (getCurrentLevel()+1) ) {
				log.debug(constructionData.getName()+" Building Next Level "+nextLevel);
				setCurrentLevel(nextLevel);
			} else {
				log.trace(constructionData.getName()+" next level "+nextLevel);				
			}
		} else {
			// will not exist if fully extended
			log.trace(constructionData.getName()+"Cannot find nextLevel");
			// System.exit(0);
		}
		// Needed resources
		// very similar to EmptySite can this be refactored so use a common ConstructionData.neededResources or is error to different
		int lastPos=0;
		boolean noResources = true;
		for (ResourceType resourceType : ResourceType.values()) {
			int res = resourceType.toInt() + 1; // Travian is 1-based
			// p = Pattern.compile("(?s)<img .*? src=\".*?img/un/r/"+res+".gif\"[^>]*>(\\d\\d*) \\|");
			// GAC - PROBLEM with pattern for Market when sending resource it is captured as build cost!
			// TODO - fix it!
			p = util.getPattern("upgradeableSite.resources", res);
			m = p.matcher(page);
			// using //s on front of pattern - may be better to remove region set and use a pattern with id = contract
			m.region(lastPos, page.length());
			if (m.find()) {
				noResources = false;
				String stringNumber = m.group(1);
				lastPos = m.end();
				try {
					this.setNeededResource(resourceType, Integer.parseInt(stringNumber));
					// log.debug(resourceType + " needed = " + stringNumber);
				} catch (NumberFormatException e) {
					util.saveTestPattern("upgradeableSite.resources", p, page);
					throw new ConversationException("Invalid number for \"" + this.getName() + "\": " + stringNumber);
				}
			} else {
				// Travian 3.6 introduced little_res on field without enough that confused logic - change test
				log.trace(this.getName()+" Cannot find upgradeableSite.resources:"+resourceType.name());
			}
		}
		// Check found none
		if (noResources) {
			// TODO - find a way to check, 5 or 20 in some items or infinite for fields in capital
			if (getCurrentLevel()<5) {
				util.saveTestPattern("upgradeableSite.resources", p, page);
				throw new ConversationException("Can't find needed resources for \"" + this.getName() + "\"");
			} else {
				log.debug(this.getName()+" Max level reached");
				constructionData.setMaxLevelReached(true);
			}			
		}
		// Check food needed
		lastPos=0;
		// p = Pattern.compile(String.format("(?s)(?i)<span class=\"c\">%s[^>]*</span>", translator.getMatchingPattern(Translator.FOOD_SHORTAGE)));
		p = util.getPattern("upgradeableSite.foodNeeded", translator.getMatchingPattern(Translator.FOOD_SHORTAGE));
		m = p.matcher(page);
		if (!m.find()) {
			// Needed time
			// p = Pattern.compile("(?s)<img .*? src=\".*?img/un/a/clock.gif\"[^>]*>([^<]*)<");
			p = util.getPattern("upgradeableSite.timeNeeded");
			m = p.matcher(page);
			m.region(lastPos, page.length());
			if (m.find()) {
				String timeString = m.group(1).trim();
				this.setSecondsForNextLevel(Util.timeToSeconds(timeString));
				log.debug(this.getName()+" timeString "+timeString);
			} else {
				if (getCurrentLevel()<10 && !constructionData.isMaxLevelReached()) { // TODO should be specific for the item (5 or 20 in some items)
					util.saveTestPattern("upgradeableSite.timeNeeded", p, page);
					throw new ConversationException("Can't find time to complete " + this.getName());
				} else {
					log.debug(this.getName()+" Max level reached");
					constructionData.setMaxLevelReached(true);
				}
			}
		} else {
			log.debug(this.getName()+" Food Shortage");
			EventLog.log(this.getName()+" Food Shortage");
			setFoodShortage(true);
		}
		if (!isUpgradeable()) {
			log.debug(this.getName()+" not upgradeable");
			this.setSubmitUrlString(null);
			return;
		}
		// Find submit url
		int pos = m.end(); // Keep last matcher position
		// p = Pattern.compile("(?s)<a href=\"(dorf\\d\\.php\\?.*?)\">");
		p = util.getPattern("upgradeableSite.submitUrl");
		m = p.matcher(page);
		m.region(pos, page.length());
		if (m.find()) {
			String submitUrlString = m.group(1);
			//fofo begin. 
			//on danish server page source says "dorf2.php?a=21&amp;c=8ec52c" 
			//while mozilla shows "dorf2.php?a=21&c=8ec52c" to be the clickable link url.
			//adding this to remove "amp;"
			// tested it, and it works. Should have no impact at all if amp; is not present
			submitUrlString=util.cleanSubmitString (submitUrlString, "upgradeableSite.removeFromsubmitUrl");
			//fofo end
			this.setSubmitUrlString(Util.getFullUrl(this.getUrlString(), submitUrlString));
			log.trace(this+" submitUrl ");
		} else {
			log.trace(this+" cannot find submitUrl");
			// util.saveTestPattern("upgradeableSite.submitUrl", p, page);
			// No url because can't grow level
			this.setSubmitUrlString(null);
		}
	}
	
//	public String getTypeString() { 
//		return constructionData.getTypeString(); // e.g. "miniera di ferro"
//	}
	
//	public void setTypeString(String typeString) {
//		constructionData.setTypeString(typeString);
//	}

	public int getFoodNeeded() {
		return constructionData.getFoodNeeded();
	}
	
	public void setNeededResource(ResourceType type, int amount) {
		constructionData.setNeededResource(type, amount);
	}

	/**
	 * Return true if the item can be posted for upgrade
	 * @return
	 */
	public boolean isSubmittable() {
		return constructionData.isSubmittable();
	}
	
	public int getTotResourcesNeeded() {
		return constructionData.getTotResourcesNeeded();
	}

	public int getCurrentLevel() {
		return constructionData.getCurrentLevel();
	}

	public void setCurrentLevel(int currentLevel) {
		constructionData.setCurrentLevel(currentLevel);
	}

	public void setCurrentLevel(String currentLevel) throws ConversationException {
		constructionData.setCurrentLevel(currentLevel);
	}

	public int getNextLevel() {
		return constructionData.getNextLevel();
	}

	public void setNextLevel(int nextLevel) {
		constructionData.setNextLevel(nextLevel);
	}

	public int getSecondsForNextLevel() {
		return constructionData.getSecondsForNextLevel();
	}

	public void setSecondsForNextLevel(int secondsForNextLevel) {
		constructionData.setSecondsForNextLevel(secondsForNextLevel);
	}

	public String getName() {
		return constructionData.getName();
	}

	public String getUrlString() {
		return constructionData.getUrlString();
	}

	public String getSubmitUrlString() {
		return constructionData.getSubmitUrlString();
	}

	public void setSubmitUrlString(String submitUrlString) {
		constructionData.setSubmitUrlString(submitUrlString);
	}
	
	public ResourceTypeMap getNeededResources() {
		return constructionData.getNeededResources();
	}

	public void setUpgradeable(boolean upgradeable) {
		constructionData.setUpgradeable(upgradeable);
	}

	public void setFoodShortage(boolean foodShortage) {
		constructionData.setFoodShortage(foodShortage);
	}

	/**
	 * Return true if there are enough resources to upgrade and max level has not been reached;
	 * true doesn't mean this item is submittable.
	 * @return
	 */
	public boolean isUpgradeable() {
		return constructionData.isUpgradeable();
	}
	
	public Date upgrade(Util util) throws ConversationException {
		return constructionData.upgrade(util);
	}

	public String getCompletionTimeString(Util util, String page) throws ConversationException {
		return constructionData.getCompletionTimeString(util, page);
	}
	
	public boolean getFoodShortage() {
		return constructionData.getFoodShortage();
	}

	public boolean isMaxLevelReached() {
		return constructionData.isMaxLevelReached();
	}
	
	public Translator getTranslator() {
		return constructionData.getTranslator();
	}
	
	protected void setConstructionData(ConstructionData constructionData) {
		this.constructionData = constructionData;
	}
	
	public String toString() {
		return constructionData.toString();
	}

	abstract public boolean needsFieldsQueue();

//	public void setTypeString(LocalizedType type) {
//		constructionData.setTypeString(type);
//	}
}

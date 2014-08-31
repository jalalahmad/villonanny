package net.villonanny.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import net.villonanny.ConversationException;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;

public class EmptySite extends Building {
	private final static Logger log = Logger.getLogger(EmptySite.class);
	// List of buildings that can be built in this empty slot
	private List<ConstructionData> buildings = new ArrayList<ConstructionData>();
	private BuildingType desiredBuildingType = null;
	private boolean oneChoiceOnly = false;
	
	public EmptySite(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
//		super.setTypeString(BuildingType.EMPTYSITE);
	}
	
	public void fetch(Util util) throws ConversationException {
	    buildings.clear();
		List<Integer> positions = new ArrayList<Integer>();
		setUpgradeable(false);
		setFoodShortage(false);
		
		Pattern p;
		Matcher m;
		String page = util.httpGetPage(getUrlString());

		// The valid section is delimited either by id="all_link" or by id="soon_link".
		// We get the one that is higher in the page, if any
		int pageLimit = page.length(); // Default is page bottom
		p = util.getPattern("emptySite.soonLink");
		m = p.matcher(page);
		if (m.find() && pageLimit > m.start()) {
			pageLimit = m.start();
		}
		p = util.getPattern("emptySite.allLink");
		m = p.matcher(page);
		if (m.find() && pageLimit > m.start()) {
			pageLimit = m.start();
		}
		
		// <h2>Warehouse</h2>
		// <h2>2. Warehouse</h2>
		// p = Pattern.compile("(?s)(?i)<h2>(?:\\d+\\.\\s*)?(.*?)</h2>");
		p = util.getPattern("emptySite.name");
		m = p.matcher(page);
		m.region(0, pageLimit);
		while (m.find()) {
			String buildingTypeString = m.group(1);
			String name = buildingTypeString;
			ConstructionData item = new ConstructionData(name, getUrlString(), getTranslator());
			item.setCurrentLevel(0);
//			item.setTypeString(name);
			this.buildings.add(item);
			String key = getTranslator().getKeyword(buildingTypeString);
			BuildingType buildingType = BuildingType.fromKey(key);
			if (buildingType!=null) {
				item.setType(buildingType);
				if ((desiredBuildingType!=null && desiredBuildingType.equals(buildingType)) 
					|| isOneChoiceOnly()) {
					super.setConstructionData(item);
				}
			} else {
				throw new ConversationException("Building \"" + buildingTypeString + "\" not found in language config file");
			}
			positions.add(new Integer(m.start()));
			// add debug to check what listing, found includes soon to build that don't have resource or time info
			// log.debug(positions.size()+" Found:"+name);
		}
		
		// Needed resources
		// very similar to UpgradeableSite can this be refactored so use a common ConstructionData.neededResources or is error to different
		int lastPos = 0;
		int nextPos = pageLimit;
		for (ConstructionData item : buildings) {
			boolean noResources = true;
			for (ResourceType resourceType : ResourceType.values()) {
				int res = resourceType.toInt() + 1; // Travian is 1-based
				// p = Pattern.compile("(?s)<img .*? src=\".*?img/un/r/"+res+".gif\"[^>]*>(\\d\\d*) \\|");
				p = util.getPattern("emptySite.neededResources", res);
				m = p.matcher(page);
				m.region(lastPos, pageLimit);
				if (m.find()) {
					noResources = false;
					String stringNumber = m.group(1);
					lastPos = m.end();
					try {
						item.setNeededResource(resourceType, Integer.parseInt(stringNumber));
					} catch (NumberFormatException e) {
						throw new ConversationException("Invalid number for \"" + this.getName() + "\": " + stringNumber);
					}
				} else {
					// Travian 3.6 introduced little_res on field without enough that confused logic - change test
					log.trace(item.getName()+" Cannot find emptySite.neededResources:"+resourceType.name());
				}	
			}
			// Check found none
			if (noResources) {
				// Empty - caused by no valid building when construction list included show more
				log.warn(item.getName()+" Cannot find emptySite.neededResources");
			} else {
				log.trace(item.getName()+" emptySite.neededResources:"+item.getNeededResources().toStringNoFood());				
			}
		}

		// Check food needed
		lastPos = 0;
		for (int i = 0; i < positions.size();i++) {
			lastPos = positions.get(i).intValue();
			if (i == positions.size() - 1) {
				nextPos = pageLimit;
			} else {
				nextPos = positions.get(i + 1).intValue();
			}
			// p = Pattern.compile(String.format("(?s)(?i)<span class=\"c\">%s[^>]*</span>", translator.getMatchingPattern(Translator.FOOD_SHORTAGE)));
			p = util.getPattern("emptySite.checkFoodNeeded", translator.getMatchingPattern(Translator.FOOD_SHORTAGE));
			m = p.matcher(page);
			m.region(lastPos, nextPos);
			if (!m.find()) {
				// Needed time
				// p = Pattern.compile("(?s)<img .*? src=\".*?img/un/a/clock.gif\"[^>]*>([^<]*)<");
				p = util.getPattern("emptySite.neededTime");
				// log.debug("GAC emptySite.neededTime:" + p );
				m = p.matcher(page);
				m.region(lastPos, nextPos);
				if (m.find()) {
					// log.debug("<"+ m.group(1) +">trimmed<"+ m.group(1).trim()+">");
					String timeString = m.group(1).trim();
					buildings.get(i).setSecondsForNextLevel(Util.timeToSeconds(timeString));
				} else {
					// caused by no valid building when construction list included show more
					// throw new ConversationException("Can't find time to complete " + this.getName());
					// log.warn(buildings.get(i).getName()+" Cannot find emptySite.neededTime");
					util.saveTestPattern("emptySite.neededTime", p, page);
					throw new ConversationException("Can't find time to complete " + buildings.get(i));
				}
			} else {
				buildings.get(i).setUpgradeable(false);
				buildings.get(i).setFoodShortage(true);
			}
			if (!buildings.get(i).isUpgradeable()) {
				buildings.get(i).setSubmitUrlString(null);
				return;
			}
			// Find submit url
			// p = Pattern.compile("(?s)<a href=\"(dorf\\d\\.php\\?.*?)\">");
			p = util.getPattern("emptySite.findSubmitUrl");
			m = p.matcher(page);
			m.region(lastPos, nextPos);
			if (m.find()) {
				String submitUrlString = m.group(1);
				submitUrlString=util.cleanSubmitString (submitUrlString, "emptySite.removeFromSubmitUrl");
				buildings.get(i).setSubmitUrlString(Util.getFullUrl(this.getUrlString(), submitUrlString));
				log.trace(buildings.get(i)+" submitUrl ");
			} else {
				log.trace(buildings.get(i)+" cannot find submitUrl");				
				// No url because can't grow level
				buildings.get(i).setSubmitUrlString(null);
			}
		}
	}
	
//	public ConstructionData getTypeString(BuildingType type) {
//		ConstructionData item = null;
//		for (ConstructionData data : buildings) {
//			String a = data.getName();
//			String buildingKey = getTranslator().getKeyword(a);
//			if (buildingKey.equalsIgnoreCase(type.toString())) {
//				item = data;
//				break;
//			}
//		}
//		return item;
//	}
	
	public Collection<ConstructionData> getTypeStrings() {
		return buildings.subList(0, buildings.size() - 1);
	}

	public void setDesiredBuildingType(BuildingType newDesiredBuildingType) {
		this.desiredBuildingType = newDesiredBuildingType;
		for (ConstructionData item : buildings) {
			if (newDesiredBuildingType!=null && newDesiredBuildingType.equals(item.getType())) {
				super.setConstructionData(item);
			}
		}
	}

	public boolean isOneChoiceOnly() {
		return oneChoiceOnly;
	}

	public void setOneChoiceOnly(boolean oneChoiceOnly) {
		this.oneChoiceOnly = oneChoiceOnly;
	}

	@Override
	public void setCurrentLevel(int currentLevel) {
		// Nothing to do
	}

	@Override
	public void setCurrentLevel(String currentLevel) {
		// Nothing to do
	}
	
	public boolean needsFieldsQueue() {
		if (desiredBuildingType!=null) {
			return desiredBuildingType.isField();
		}
		return true; // TODO ??? when not set, could be either true or false, we don't know yet! ???
	}

	
	public String toString() {
		String extra = "";
		if (desiredBuildingType!=null) {
			extra = desiredBuildingType.toString();
		}
		return "(" + extra + ") " + super.toString();
	}
}

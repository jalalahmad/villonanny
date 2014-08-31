package net.villonanny.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.SevenBySeven;
import net.villonanny.misc.Coordinates;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class Settle extends Strategy {
    private final static Logger log = Logger.getLogger(Settle.class);

	public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
		// <strategy class="Settle" desc="Fonda villo" enabled="true" uid="s2937" minPauseMinutes="60">
		//     <target x="-76" y="-300"/>
		// </strategy>
		// <strategy class="Settle" desc="Fonda villo" enabled="true" uid="s2937" minPauseMinutes="60">
		//     <target coords="(-76|-300)"/>
		// </strategy>
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());
		try {
			long minPauseMinutes = config.getLong("/@minPauseMinutes", 2);
			// 1. Find the url of the target cell
			SevenBySeven map = new SevenBySeven(util, village.getVillageName(), village.getVillageUrlString(), translator);
			Coordinates coord = new Coordinates(config, "target");
			String page = map.moveTo(coord.getX(), coord.getY());
			String mapSlotUrl; 
			Pattern p;
			Matcher m;
			String patternKey = "settle.main";
			p = util.getPattern(patternKey, coord.toStringArray());
			m = p.matcher(page);
			String d;
			if (m.find()) {
				String urlTail = m.group(1); // d=561025&c=86
				d = urlTail.split("[=&]")[1];
				mapSlotUrl = util.getBaseUrl() + "karte.php?" + urlTail;
			} else {
	            util.saveTestPattern(patternKey, p, page);
	            EventLog.log("evt.settlersNotSent", this.getClass(), coord.toString());
				throw new ConversationException("Can't find map element at coordinates " + coord);
			}
			// 2. Check that the cell can be settled
			page = util.httpGetPage(mapSlotUrl);
			patternKey = "settle.isValid";
			p = util.getPattern(patternKey, d);
			m = p.matcher(page);
			if (!m.find()) {
				log.debug("Can't send settlers to " + coord + ": link on map not active");
	            EventLog.log("evt.settlersNotSent", this.getClass(), coord.toString());
				return TimeWhenRunnable.minutesFromNow(minPauseMinutes);
			}
			// 3. Click on the link
			String confirmationFormUrl = util.getBaseUrl() + m.group(1); // http://speed.travian.it/a2b.php?id=421713&amp;s=1
			confirmationFormUrl = confirmationFormUrl.replaceAll("&amp;", "&"); // http://speed.travian.it/a2b.php?id=421713&s=1
			page = util.httpGetPage(confirmationFormUrl);
			// 4. Confirm
			patternKey = "settle.confirmationForm";
			p = util.getPattern(patternKey);
			m = p.matcher(page);
			if (!m.find()) {
				log.error("Can't send settlers to " + coord + ": confirmation form is missing");
	            EventLog.log("evt.settlersNotSent", this.getClass(), coord.toString());
				return TimeWhenRunnable.minutesFromNow(minPauseMinutes);
			}
			List<String> postNames = new ArrayList<String>();
			List<String> postValues = new ArrayList<String>();
			util.addHiddenPostFields(page, patternKey, postNames, postValues);
			Util.addButtonCoordinates("s1", 40, 20, postNames, postValues);
			String postUrl = util.getBaseUrl() + m.group(1);
			page = util.httpPostPage(postUrl, postNames, postValues, true);
			// 5. Check all done
			patternKey = "settle.done";
			p = util.getPattern(patternKey);
			m = p.matcher(page);
			if (!m.find()) {
				log.error("Can't send settlers to " + coord + ": final confirmation not found");
	            EventLog.log("evt.settlersNotSent", this.getClass(), coord.toString());
	            return TimeWhenRunnable.minutesFromNow(minPauseMinutes);
			}
            EventLog.log("evt.settlersSent", this.getClass(), coord.toString());
            setDeleted(true);
            village.strategyDone.setFinished (this.getId(), true); //register it as done. fofo
            return TimeWhenRunnable.NEVER;
        } finally {
            NDC.pop();
        }
	}

}

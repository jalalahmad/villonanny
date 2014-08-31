package net.villonanny.entity;

import java.util.EnumMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.type.PartyType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.log4j.Logger;

public class TownHallSite extends Building {
	private final static Logger log = Logger.getLogger(TownHallSite.class);
	private TimeWhenRunnable partyFinishTime;
	
	private EnumMap<PartyType, ResourceTypeMap> partyCost;

	public TownHallSite(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
	}

	public void fetch(Util util) throws ConversationException {
		super.fetch(util);
		partyFinishTime = TimeWhenRunnable.NOW;
		partyCost = new EnumMap<PartyType, ResourceTypeMap>(PartyType.class);

		Pattern p;
		Matcher m;
		String page = util.httpGetPage(getUrlString());

		// Party progress
		// <tr>\\s*<td width="44%" class="s7">([^<]+)</td>\\s*<td width="25%"><span id=timer\\d+>([\\d:]+)</span></td>\\s*<td width="25%">([\\d:]+)</span><span>[^<]+</td>\\s*</tr>
		// 1 - Time to finish
		// 2 - Time when finished
		// p = Pattern.compile(Util.P_FLAGS + "<tr>[^<]*<td[^>]+>[^<]*</td>[^<]*<td[^>]+><span id=timer1>(\\d?\\d:\\d?\\d:\\d?\\d)</span></td>[^<]*<td[^>]+>[^<]+</span><span>[^<]+</td>");
		p = util.getPattern("townHallSite.partyFinishTime");
		m = p.matcher(page);
		if (m.find()) {
			String timeString = m.group(1).trim();
			try {
				partyFinishTime = new TimeWhenRunnable(util.getCompletionTime(timeString));
				log.debug("Party will end in " + timeString);
			} catch (Exception e) {
				throw new ConversationException("Can't parse running party in " + this.getName());
			}
		// There's no need to parse party cost if we can't make a party
		} else {
			// Party cost
			// <table width="100%" class="f10" cellspacing="2" cellpadding="0">\\s*<tr>\\s*<td class="s7"><div><a href="#" onClick="[^"]+">([^<]+)</a> <span class="f8">\\([^)]+\\)</span></div></td>\\s*</tr>\\s*<tr><td class="s7" nowrap>\\s*<img src="img/un/a/x\\.gif" width="1" height="15"><img src="img/un/r/1\\.gif">(\\d+)\\|<img src="img/un/r/2\\.gif">6650|<img src="img/un/r/3\\.gif">(\\d+)\\|<img src="img/un/r/4\\.gif">(\\d+)\\| <img src="img/un/a/clock\.gif" width="18" height="12"> ([\\d:]+)</td></tr></table>\\s*</td>\\s*<td width="28%"><div class="c">[^<]+</div></td>\\s*</tr>
			// 1 - Celebration name
			// 2 - Wood cost
			// 3 - Clay cost
			// 4 - Iron cost
			// 5 - Crop cost
			// 6 - Celebration time
			// p = Pattern.compile("(?s)(?i)<table width=\"100%\" class=\"f10\" cellspacing=\"2\" cellpadding=\"0\">\\s*<tr>\\s*<td class=\"s7\"><div><a href=\"#\" onClick=\"[^\"]+\">([^<]+)</a> <span class=\"f8\">\\([^)]+\\)</span></div></td>\\s*</tr>\\s*<tr><td class=\"s7\" nowrap>\\s*<img src=\"img/un/a/x\\.gif\" width=\"1\" height=\"15\"><img src=\"img/un/r/1\\.gif\">(\\d+)\\|<img src=\"img/un/r/2\\.gif\">6650|<img src=\"img/un/r/3\\.gif\">(\\d+)\\|<img src=\"img/un/r/4\\.gif\">(\\d+)\\| <img src=\"img/un/a/clock\\.gif\" width=\"18\" height=\"12\"> ([\\d:]+)</td></tr></table>\\s*</td>\\s*<td width=\"28%\"><div class=\"c\">[^<]+</div></td>\\s*</tr>");
			p = util.getPattern("townHallSite.partyCost");
			m = p.matcher(page);
			while (m.find()) {
				String celebrationName = m.group(1).trim();
				String stringNumber1 = m.group(2).trim();
				String stringNumber2 = m.group(3).trim();
				String stringNumber3 = m.group(4).trim();
				String stringNumber4 = m.group(5).trim();
				try {
					PartyType partyType = PartyType.fromString(getTranslator().getKeyword(celebrationName));
					ResourceTypeMap resources = new ResourceTypeMap();
					resources.put(ResourceType.WOOD, Integer.parseInt(stringNumber1));
					resources.put(ResourceType.CLAY, Integer.parseInt(stringNumber2));
					resources.put(ResourceType.IRON, Integer.parseInt(stringNumber3));
					resources.put(ResourceType.CROP, Integer.parseInt(stringNumber4));
					partyCost.put(partyType, resources);
					log.debug("Party found with cost: " + resources);
				} catch (NumberFormatException nfe) {
					throw new ConversationException("Can't parse running party in " + this.getName());
				}
			}
		}
	}
	
	public TimeWhenRunnable getPartyFinishTime() {
		return partyFinishTime;
	}
}

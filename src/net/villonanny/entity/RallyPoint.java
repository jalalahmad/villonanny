package net.villonanny.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.TranslationException;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.misc.TravianVersion;
import net.villonanny.type.TribeType;
import net.villonanny.type.TroopTransferType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.log4j.Logger;

public class RallyPoint extends Building {
	private final static Logger log = Logger.getLogger(RallyPoint.class);
	private String sendTroopsPageUrlString;
	private TroopTypeMap troops = new TroopTypeMap();
//	private static final int TYPE_HERO = 11; // travian type = 10+1
//	private static final int TYPE_CATAPULT = 8; // travian type = 7+1
	private String SEND_URL_SUFFIX = "a2b.php";
	

	public RallyPoint(String name, String urlString, Translator translator) {
		super(name, urlString, translator);
		this.sendTroopsPageUrlString = Util.getFullUrl(getUrlString(), SEND_URL_SUFFIX);
	}
	
	public TroopTypeMap fetchSendableTroops(Util util, boolean sharp) throws ConversationException {
		Pattern p;
		Matcher m;
		// Go to main rallypoint page
		String page = util.httpGetPage(getUrlString(), sharp);
		// Go to troop transfer page
		page = util.httpGetPage(sendTroopsPageUrlString, sharp);
		// Find available troops
		// <input class="fm" type="Text" name="t1" value="" size="2" maxlength="6"></td><td class="f8"><a href="#" onClick="document.snd.t1.value=393; return false;">(393)</a></td>
		// <input class="fm" type="Text" name="t11" value="" size="2" maxlength="6"></td><td class="f8"><a href="#" onClick="document.snd.t11.value=1; return false;">(1)</a></td></tr
		for (TroopType troopType : TroopType.values()) {
			int type = troopType.toInt() + 1; // Travian is 1-based
			if (util.isTravianVersionAbove(TravianVersion.V35)) {
				if (util.getTribeType() == TribeType.GAULS) {
					type = type + 20; //gaul type numbers are unit u21 for phalanx and so forth in v 3.6
					                  //Romans is the same old, I haven't tested teutons. fofo
				}
				if (util.getTribeType() == TribeType.TEUTONS) {
					type = type + 10; //gaul type numbers are unit u21 for phalanx and so forth in v 3.6
					                  //Romans is the same old, I haven't tested teutons, taking a chance on those fofo
				}
			}
			// log.debug("fetchSendable "+type+" "+troopType.toString());
			// p = Pattern.compile(String.format("(?s)(?i)<input.*?name=\"t%s\".*?onClick=\"document.snd.t%s.value=(\\d+);", type, type));
			if (troopType==TroopType.HERO) {
				p = util.getPattern("rallypoint.TroopHero");
			}
			else {
				p = util.getPattern("rallyPoint.troopsTot", type, type);
			}
			m = p.matcher(page);
			if (m.find()) {
				//if (troopType==TroopType.HERO) util.saveTestPattern("rallypoint.TroopHero", p, page);
				String tot = m.group(1);
				try {
					troops.put(troopType, Integer.parseInt(tot));
				} catch (NumberFormatException e) {
					throw new ConversationException("Invalid troop amount: \"" + tot + "\"");
				}
			} else {
				// Maybe there are no units of this type
				// p = Pattern.compile(String.format("(?s)(?i)<input class=\"fm\"[^>]*name=\"t%s\" value=\"\"[^>]*></td><td class=\"[^\\\"]*\"><b>\\(0\\)</b></td>", type));
				p = util.getPattern("rallyPoint.noTroops", type);
				m = p.matcher(page);
				if (m.find()) {
					troops.put(troopType, 0);
				} else if (troopType!=TroopType.HERO) {
					// Throw exception only if not checking for hero
					throw new ConversationException("Can't find troop number " + (type));
				}
				else { //troopType is HERO
					troops.put(troopType, 0);
				}
			}
		}
		return troops;
	}

	public int sendTroops(Util util, String x, String y, String village, TroopTypeMap troopsToSend, TroopTransferType type, String[] item) throws ConversationException {
		return sendTroops(util, x, y, village, troopsToSend, type, item, false, false, null);
	}

	public int sendTroops(Util util, String x, String y, String village, TroopTypeMap troopsToSend, TroopTransferType type, String[] item, TroopTransferType spyType) throws ConversationException {
		return sendTroops(util, x, y, village, troopsToSend, type, item, false, false, spyType);
	}

	/**
	 * 
	 * @param util
	 * @param x
	 * @param y
	 * @param troopsToSend
	 * @param type
	 * @param itemString 
	 * @param itemString2 
	 * @param testOnly
	 * @param sharp
	 * @return seconds to arrive at destination
	 * @throws ConversationException
	 */
	public int sendTroops(Util util, String x, String y, String village, TroopTypeMap troopsToSend, TroopTransferType type, String[] item, boolean testOnly, boolean quick, TroopTransferType spyType) throws ConversationException {
		Pattern p;
		Matcher m;
		String page;
		
		// Go to troop transfer page
		page = util.httpGetPage(sendTroopsPageUrlString);
		String submitUrlString = sendTroopsPageUrlString; // Same page
		List<String> postNames = new ArrayList<String>();
		List<String> postValues = new ArrayList<String>();
		// Find hidden fields
		// util.addHiddenPostFields(page, "<form method=\"POST\" name=\"snd\" action=\"a2b.php\">", postNames, postValues);
		util.addHiddenPostFields(page, "rallyPoint.hiddenPostFields", postNames, postValues);
		// log.debug("GAC SendTroops spy " + spyType + " test " + testOnly );

		// Log message
		StringBuffer messageTroopSend = new StringBuffer();
		
		// Add troop amounts
		boolean spyOnly = (spyType != null) && (type != TroopTransferType.REINFORCE);
		// GAC - check for tribe
		TroopType spyTroop = TroopType.TROOP4;		// spys depend on race - default roman/teuton
		if (util.getTribeType() == TribeType.GAULS) {
			spyTroop = TroopType.TROOP3;
		}
				
		for (TroopType troopType : TroopType.values()) {
			int toSend = troopsToSend.get(troopType)<=troops.get(troopType)?troopsToSend.get(troopType):troops.get(troopType);
			if ((troopType != spyTroop) && (toSend > 0)) {
				spyOnly = false;
			}
			if ((troopType == spyTroop) && (toSend <= 0)) {
				spyOnly = false;
			}
			postNames.add("t" + (troopType.toInt()+1));
			if (toSend>0) {
				postValues.add(Integer.toString(toSend));
			} else {
				postValues.add("");
			}
			// Append to log
			messageTroopSend.append( toSend );
			if (troopType != TroopType.getLastValue()) {
				messageTroopSend.append(",");
			}
		}
		
		postNames.add("c");
		postValues.add(type.getHtmlValue());
		postNames.add("x");
		postValues.add(x);
		postNames.add("y");
		postValues.add(y);
		Util.addButtonCoordinates("s1", 50, 20, postNames, postValues);
		postNames.add("dname");
		postValues.add(village);
		
		// First post
		page = util.httpPostPage(submitUrlString, postNames, postValues, quick);
		postNames.clear();
		postValues.clear();
		
		// Find hidden fields
		// int startPos = util.addHiddenPostFields(page, "<form method=\"POST\" action=\"a2b.php\">", postNames, postValues);
		int startPos = util.addHiddenPostFields(page, "rallyPoint.hiddenPostFields2", postNames, postValues);
		Util.addButtonCoordinates("s1", 50, 20, postNames, postValues);

		String action = "Troop movement";
		// p = Pattern.compile("(?s)(?i)<h1>(.*?)</h1>");
		p = util.getPattern("rallyPoint.action");
		m = p.matcher(page);
		if (m.find()) {
			action = m.group(1);
		}

		// Log line
		if (!testOnly) {
			String coordinates = "";
			if (x!=null && y!=null && !"".equals(x+y)) {
				coordinates = String.format(" (%s|%s)", x, y);
			}
			StringBuffer message = new StringBuffer(action + coordinates + ": ");
			message.append(messageTroopSend);
			EventLog.log(message.toString());
		}
		
		// Time
		int secondsToArrive = 0;
		// p = Pattern.compile("(?s)(?i)<td width=\"50%\">\\D*? (\\d?\\d:\\d?\\d:\\d?\\d) \\D*?</td>");
		p = util.getPattern("rallyPoint.timeString");
		m = p.matcher(page);
		m.region(startPos, page.length());
		if (m.find()) {
			String timeString = m.group(1).trim();
			secondsToArrive=Util.timeToSeconds(timeString);
		} else {
			// p = Pattern.compile("(?s)(?i)There is no village at those coordinates");
			p = util.getPattern("rallyPoint.noVillage");
			m = p.matcher(page);
			if (m.find()) {
				String errorMessage = m.group(1).trim();
				log.error(errorMessage);
				EventLog.log(errorMessage);
				return -1;
			} else {
	            util.saveTestPattern("rallyPoint.timeString", p, page);
				throw new ConversationException("Can't find rallyPoint.timeString or rallyPoint.noVillage");
			}
		}
		
		// Arrival time
		// TODO the value found here is generally a couple of seconds earlier than the real one
		// TODO We should check for it after sending the troops, or maybe adjust it by adding the time that it takes to get
		// TODO from here to the end of this method 
		int newStartPos = m.end();
		// p = Pattern.compile("(?s)(?i)<td width=\"50%\">.*?<span id=tp2>(\\d?\\d:\\d?\\d:\\d?\\d)</span>");
		p = util.getPattern("rallyPoint.arrivalTimeString");
		m = p.matcher(page);
		m.region(newStartPos, page.length());
		String arrivalTimeString;
		if (m.find()) {
			arrivalTimeString = m.group(1).trim();
		} else {
            util.saveTestPattern("rallyPoint.arrivalTimeString", p, page);
			throw new ConversationException("Can't find rallyPoint.arrivalTimeString");
		}

		// If is only a test
		if (testOnly) {
			return secondsToArrive;
		}

		// If spy, spy
		if (spyOnly) {
			postNames.add("spy");
			postValues.add(spyType.getHtmlValue());
		}
		// log.debug("GAC SendTroops spyOnly " + spyOnly + " spyValue " + spyType.getHtmlValue() );
		
		// If catapults, add catapult target
		if (type != TroopTransferType.REINFORCE){   //if reinforce, there are no cata targets
			if (troopsToSend.get(TroopType.CATAPULT)>0) {
//				String kataPattern = "(?s)(?i)<select name=\"([^\"]*)\" size=\"\"[^>]*>";
				String paramName = "kata";
				String paramName2 = null;
				// Search kata parameter
				// p = Pattern.compile(kataPattern);
				p = util.getPattern("rallyPoint.kata");
				m = p.matcher(page);
				if (m.find()) {
					paramName = m.group(1).trim();
				} else {
					throw new ConversationException("Failed to find catapult target parameter in form (troops not sent)");
				}
				newStartPos = m.end();
				
				// Search target id
				// Normalize target item
				if (item == null || item.length == 0) {
					item = new String[] { null, null };
				} else if (item.length == 1) {
					item = new String[] { item[0], null };
				}
				if (item[0] == null) {
					EventLog.log("Catapult target not set: using random");
					try {
						item[0] = translator.getFirst("random"); // There can't be more than one translation for random, as it is used just here
					} catch (TranslationException e) {
						// Try anyway
						// p = Pattern.compile("(?s)(?i)<option value=\"99\">([^<]*)</option>");
						p = util.getPattern("rallyPoint.target");
						m = p.matcher(page);
						if (m.find()) {
							item[0] = m.group(1).trim();
							log.warn("Random target keyword identified from the web page");
							log.warn("The value for key.random should be \"" + item[0] + "\" in your language configuration file");
							log.warn("Please edit your language configuration file adding key.random = " + item[0]);
						}
					}
				}
				// p = Pattern.compile("(?s)(?i)<option value=\"([^\"]*)\">"+item[0]+"</option>");
				p = util.getPattern("rallyPoint.targetId", item[0]);
				m = p.matcher(page);
				if (m.find()) {
					String targetId = m.group(1).trim();
					postNames.add(paramName);
					postValues.add(targetId);
				} else {
						EventLog.log("Failed to find catapult target ="+item[0]);
					throw new ConversationException("Failed to find catapult target name \"" + item[0] + "\" (troops not sent)");
				}			
				// If second target defined, look for second kata parameter
				if (item[1] != null) {
					p = util.getPattern("rallyPoint.kata");
					m = p.matcher(page);
					m.region(newStartPos, page.length());
					if (m.find()) {
						paramName2 = m.group(1).trim();
					} else {
						log.debug("Second catapult target parameter not found (ignoring)");
					}
				}
				if (paramName2!=null) {
					// p = Pattern.compile("(?s)(?i)<option value=\"([^\"]*)\">" + item[1] + "</option>");
					p = util.getPattern("rallyPoint.targetId2", item[1]);
					m = p.matcher(page);
					if (m.find()) {
						String targetId2 = m.group(1).trim();
						postNames.add(paramName2);
						postValues.add(targetId2);
					} else {
						EventLog.log("Second catapult target \"" + item[1] + "\" not found (ignoring)");
					}					
				}
			}
		}
		
		// Confirmation
		page = util.httpPostPage(submitUrlString, postNames, postValues, quick);
		EventLog.log("Troops sent, on target at " + arrivalTimeString);
//		p = Pattern.compile("(?s)(?i)" + arrivalTimeString);
//		m = p.matcher(page);
//		if (!m.find()) {
//			throw new ConversationException("Can't find troops in rally point reports");
//		}
		return secondsToArrive;
	}

	/**
     * cancel any troop movement
     * @return	true if cancelled ok
     */
    public	boolean cancelMovement(Util util) {
    	boolean result = false;    	
    	try {
    		// Go to main rallypoint page
    		String page = util.httpGetPage(getUrlString(), false);
			String baseUrl = util.getBaseUrl();
            String	pattern = "rallyPoint.cancel";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			// cancel any movements
			while (m.find()) {
				/* 
				String dInfo = "";
	            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            EventLog.log(pattern+": "+dInfo);
				*/
	            
				String cancelUrl = m.group(1);
				page = util.httpGetPage(baseUrl+cancelUrl);
				result = true;
			}
			if (!result) {
				log.warn("cannot find any troop movement in valid state to cancel");
				util.saveTestPattern(pattern, p, page);
			}
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return result;
    }

    public	boolean releasePrisoners(Util util) {
    	boolean result = false;    	
    	try {
    		// Go to main rallypoint page
    		String page = util.httpGetPage(getUrlString(), false);
			String baseUrl = util.getBaseUrl();
    		// get prisoner info page
            String	pattern = "rallyPoint.release";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			// release all of them
			while (m.find()) {
				/* 
				String dInfo = "";
	            for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
	            EventLog.log(pattern+": "+dInfo);
				*/
				// release all - should we check name/playerid for friendly settlers
				EventLog.log("Releasing Prisoners from "+m.group(1));
				String releaseUrl = m.group(2);
				page = util.httpGetPage(baseUrl+releaseUrl);
				result = true;
			}
			if (!result) {
				// no error
				log.debug("cannot find any troops to release");
				// util.saveTestPattern(pattern, p, page);
			}
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return result;
    }
	
	
}

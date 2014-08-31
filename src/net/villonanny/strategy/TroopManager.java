package net.villonanny.strategy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.RallyPoint;
import net.villonanny.misc.Coordinates;
import net.villonanny.type.TroopTransferType;
import net.villonanny.type.TroopType;
import net.villonanny.type.TroopTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

//<strategy desc="Test TroopManager" class="TroopManager" enabled="true" minPauseMinutes="5" sleep="120">
//	<target x="-65" y="26" village="TARGET_VILLAGE" type="reinforce" rate="1" item=""/>
//		<troups2 min="10%" allowLess="true">100</troups2>
//		<time type="arrive" refuse="5" format="dd/MM/yyyy HH:mm:ss">06/08/2008 12:06:00</time>
// <troops randomise="false" allowLess="true" min="10" type="Swordsman">20</troops>
// <troops type="Phalanx" allowLess="true" min="1" randomise="false" enabled="true">2</troops>
// <time type="start" maxLateMinutes="5" movement="reinforce" coords="(-37|137)" village="" desc="desc" format="dd/MM/yyyy HH:mm:ss">22/11/2009 21:50:00</time>
// <time type="arrive" movement="reinforce" coords="(-37|137)" village="" desc="desc" format="dd/MM/yyyy HH:mm:ss">22/11/2009 23:15:00</time>

//	@author gac
//		<dodge> enabled="true"  x="-X" y="-Y" or cords="(X|Y)" recall="30" wait="50" />
//</strategy>
//
// time/@type is "start" or "arrive"
// time/@maxLateMinutes is the maximum time to send if miss start window
public class TroopManager extends Strategy {

	private final static Logger log = Logger.getLogger(TroopManager.class);
	private int minPauseMinutes = -1;
	private int totErrors = 0;
	private final int MAX_ERRORS = 5;
	private final static String TIME_FORMAT = "dd/MM/yyyy HH:mm:ss";
	private final static String START = "start";
	private final static String ARRIVE = "arrive";
	private final static String DODGE = "DODGE";
	private final static long MIN_SUSPEND_TIME = (120);	// min time to use suspend not wait
	private final static long MAX_LATE_MINS = (5);		// max time to send if late
	private final static long RECALL_TIME = (30);		// time to wait before recalling
	private final static long WAIT_TIME = (50);			// wait must be > recall but < 2 * recall 
														// impacts how big gap jumps out of village for
	private Date nextMovementTime = null; // date to move

	long suspendMilli = MIN_SUSPEND_TIME * Util.MILLI_SECOND;			// suspend if waiting longer than this
	long waitMilli = WAIT_TIME * Util.MILLI_SECOND;						// sleep if waiting longer than this 
	long recallMilli = RECALL_TIME * Util.MILLI_SECOND;					// wait this long before recalling troops
	long delayMilli = 0;												// processing delay to calculate troops and send

	// if reinforcing or attacking allow to be slightly late
	long maxLateMilli = MAX_LATE_MINS * Util.MILLI_MINUTE;
	private List<SubnodeConfiguration> timeNodes = null;

	/**
	 * class to hold Troop movement information - used in list of movements
	 * 
	 * @author GAC
	 */
	private class TroopMovement {
		Date startTime = null; // date to move
		Coordinates target = null; // x,y location
		String villageName = null; // name of village or null
		String type = null; // type of movement="REINFORCE|RAID|ATTACK"|DODGE
		boolean active = false; // inuse
		long maxLate = 0L; // max late in msec
		// SubnodeConfiguration config = null; // config entry if dont parse
		// everything first time
		Date nodeTime = null; // time of event

		public TroopMovement(Date value) {
			this.nodeTime = value;
		}

		public void setDone() {
			active = false;
		}

		public String toString() {
			String s = "";
			if (type != null) {
				s += type;
			}
			s += ",";
			if (nodeTime != null) {
				s += nodeTime;
			}
			s += ",";
			if (startTime != null) {
				s += startTime;
			}
			s += "," + maxLate + ",";
			if (villageName != null) {
				s += villageName;
			}
			s += ",";
			if (target != null) {
				s += "(" + target.getX() + "|" + target.getY() + ")";
			}
			s += "," + active;
			return s;
		}
	}

	private List<TroopMovement> movement = new ArrayList<TroopMovement>();
	private int nextOne = -1;

	public TroopManager() {
		// constructor - make sure minimal size set for dodge
		if (movement.size() == 0) {
			TroopMovement dodge = new TroopMovement(null);
			dodge.type = DODGE;
			movement.add(dodge);
			log.trace("Creating Dodge Entry 0");
		}
	}

	public TimeWhenRunnable execute() throws ConversationException {
		log.info("Executing strategy " + super.getDesc());
		NDC.push(super.getDesc());

		try {
			Date now = new Date(); // current time - use for calculations so
									// wont set too late if they take time
			long sleepMilli = 0;
			minPauseMinutes = super.config.getInt("/@minPauseMinutes", 5);

			// check for next action - indicate from execute not check time
			checkNextAction(true);
			// add an auto trap emptier - do at end of dodge but before calculating
			// time
			// should this be called from reportMessageReader when we capture some?
			String release = super.config.getString("/release/@enabled", "false");
			// EventLog.log("release "+release);
			if (!release.equalsIgnoreCase("false")) {
				// was local release
				// moved to rally point method
				RallyPoint rallyPoint = village.getRallyPoint();
				if (rallyPoint != null) {
					rallyPoint.releasePrisoners(util);				
				}
			}
			// loop while checking if need to do something immediately
			while ((nextMovementTime != null) && (sleepMilli == 0)) {
				log.debug("Next Movement " + nextMovementTime.toString());
				// check how long to go
				long remainingMilli = nextMovementTime.getTime() - now.getTime();
				sleepMilli = minPauseMinutes * Util.MILLI_MINUTE; // set a
																	// default
				// changed how sleep works - to recognise this is sharp 
				// however sharp on other village strategy may stop this getting priority
				if (remainingMilli < -maxLateMilli) {
					// too late
					EventLog.log("Too late for " + nextMovementTime
							+ " + maxLate=" + maxLateMilli);
					movement.get(nextOne).active = false;
				} else if (remainingMilli > (11 * Util.MILLI_MINUTE)) {
					// wake up near time of event to try and prempt any other
					// but only if less than minpause
					if (remainingMilli < (sleepMilli + delayMilli)) {
						// strategy using extraPause
						EventLog.log("Sleeping until 10 mins before next Movement");
						// return new TimeWhenRunnable(now.getTime() + nextMilli -
						// (10 * Util.MILLI_MINUTE), true );
						sleepMilli = remainingMilli - (10 * Util.MILLI_MINUTE);
					}
				} else if (remainingMilli > suspendMilli) {
					// still ok to suspend - set TWR with sharp set so uses
					// exact requested time wait set for dodge or is delayMilli for movements
					sleepMilli = remainingMilli - (suspendMilli - waitMilli);
					EventLog.log("Sleeping until "
							+ Util.milliToTimeString(suspendMilli - waitMilli)
							+ " before Movement");
					// return new TimeWhenRunnable(now.getTime() + nextMilli -
					// suspendMilli, true );
				} else {
					// too risky to suspend wait here
					super.village.gotoMainPage(true); // Ensure you do it from
														// the right village
														// (someone might have
														// clicked to a
														// different village
														// meanwhile)
					// check if any troops
					RallyPoint rallyPoint = village.getRallyPoint();
					if (rallyPoint == null) {
						log.warn("No rally point");
						// Try again later - assume building rally point and
						// troops
						sleepMilli = minPauseMinutes * Util.MILLI_MINUTE;
					} else {
						String fullkey = null;
						TroopTransferType transferType = null;
						// check for actual numbers - do before time check
						// risk if some come back in these few seconds,
						// if do time check first then may sit in loop for n
						// secs for no reason
						// or if it takes a long time to parse file and check
						// troops then miss slot - split it?
						TroopTypeMap available = rallyPoint
								.fetchSendableTroops(super.util, true);
						log.debug(available.getSumOfValues()
								+ " troops in village");
						if (available.getSumOfValues() > 0) {
							// mark done to start with in case of errors
							TroopMovement nextMovement = movement.get(nextOne);
							movement.get(nextOne).setDone();
							log.debug("movement " + nextOne + "="
									+ movement.get(nextOne).toString());
							// check how soon - wait must be > recall but < 2 *
							// recall
							// if doing accurate movement wait and recall both 0
							if (remainingMilli > waitMilli) {
								// wait until event
								EventLog.log("Waiting to Send Troops");
								// Util.sleep(remainingMilli - recallMilli);
								Util.sleep(remainingMilli - delayMilli);
							}
							// check latest counts
							available = rallyPoint.fetchSendableTroops(super.util, true);
							// check if dodging
							if (nextOne == 0) {
								// already set
								// String dodgeType = super.config.getString("/dodge/@enabled", null);
								String dodgeType = nextMovement.type;
								// check action type - all use of other than
								// reinforce
								if (!dodgeType.equalsIgnoreCase("false") && !dodgeType.equalsIgnoreCase("true")) {
									fullkey = "movement." + translator.getKeyword(dodgeType, "movement"); // movement.normal
									transferType = TroopTransferType.fromKey(fullkey);
								} else {
									transferType = TroopTransferType.REINFORCE;
								}
								// send all troops
								EventLog.log("Dodging to ("
										+ nextMovement.target.getX() + "|"
										+ nextMovement.target.getY() + ") "
										+ nextMovement.villageName
										+ ", total troops=" + available);
								int secondToArrive = rallyPoint.sendTroops(
										util, nextMovement.target.getX(),
										nextMovement.target.getY(),
										nextMovement.villageName, available,
										transferType, null, false, true, null);
								// check if just dodging or sending somewhere
								if (dodgeType.equalsIgnoreCase("true")) {
									// set time dodged - even if not using sleep in
									// case error occurs
									// lastDodge = now;
									// request run immediately
									// return new TimeWhenRunnable(now.getTime() +
									// recallMilli, true);
									// or wait here to cancel - safer
									Util.sleep(recallMilli);
									EventLog.log("Recall Troops");									
									// cancelMovement();
									rallyPoint.cancelMovement(util);
								} else {
									log.debug("Troops not recalled sent to "+dodgeType);																		
								}
								// reset dodge flag
								// lastDodge = null;
							} else {
								fullkey = "movement." + translator.getKeyword(nextMovement.type, "movement"); // movement.normal
								transferType = TroopTransferType.fromKey(fullkey);
								// get troops to send and send them
								TroopTypeMap toSend = new TroopTypeMap();
								toSend.getConfigValues(super.util, super
										.getDesc(), super.config, available);
								EventLog.log("Sending to ("
										+ nextMovement.target.getX() + "|"
										+ nextMovement.target.getY() + ") "
										+ nextMovement.villageName
										+ ", total troops=" + toSend);
								int secondToArrive = rallyPoint.sendTroops(
										util, nextMovement.target.getX(),
										nextMovement.target.getY(),
										nextMovement.villageName, toSend,
										transferType, null, false, true, null);
							}
							// check for more
							checkNextAction(true);
							now = new Date();
							// execute loop again to set sleep time
							sleepMilli = 0;
						} else {
							// no troops - use a quick pause to see if any come
							// back
							// risk is that will not get round all other
							// villages
							sleepMilli = RECALL_TIME * Util.MILLI_SECOND;
							log.debug("No Troops - sleeping for short time "
									+ Util.milliToTimeString(sleepMilli));
						} // endif sometroops
					} // endif rallypoint
				} // endif wait for event
			} // endwhile
			// check for any more events
			boolean sharp = false;
			if (nextMovementTime == null) {
				// not so use normal pause
				sleepMilli = minPauseMinutes * Util.MILLI_MINUTE;
				sharp = false;
			} else {
				sharp = true;
			}
			log.trace(sleepMilli + "msec sleep sharp=" + sharp);
			return new TimeWhenRunnable(now.getTime() + sleepMilli, sharp);
		} finally {
			NDC.pop();
		}
	}

	private boolean sendBack(String rallyUrl) {
		RallyPoint rallyPoint = village.getRallyPoint();
		try {
			// get page
			String baseUrl = util.getBaseUrl();
			String page = util.httpGetPage(baseUrl + rallyUrl);
			String pattern = "troopManager.sendback";
			Pattern p = util.getPattern(pattern);
			Matcher m = p.matcher(page);
			if (m.find()) {
				/*
				 * String dInfo = ""; for (int i = 0 ; i++ < m.groupCount() ; )
				 * { dInfo = dInfo.concat(","+m.group(i)); }
				 * EventLog.log(pattern+": "+dInfo);
				 */
				// select sendback page
				String cancelUrl = m.group(1);
				page = util.httpGetPage(baseUrl + cancelUrl);
				// hit send back
				// hidden post

				return true;
			} else {
				log
						.warn("cannot find any troop movement in valid state to sendback");
				util.saveTestPattern(pattern, p, page);
			}
		} catch (ConversationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	/*
	 * moved to RallyPoint private boolean cancelMovement() { return
	 * cancelMovement("build.php?gid=16"); }
	 * 
	 * private boolean cancelMovement(String rallyUrl) { RallyPoint rallyPoint =
	 * village.getRallyPoint(); try { // get page String baseUrl =
	 * util.getBaseUrl(); String page = util.httpGetPage(baseUrl+rallyUrl);
	 * String pattern = "troopManager.cancel"; Pattern p =
	 * util.getPattern(pattern); Matcher m = p.matcher(page); if (m.find()) {
	 * String cancelUrl = m.group(1); page =
	 * util.httpGetPage(baseUrl+cancelUrl); return true; } else {
	 * log.warn("cannot find any troop movement in valid state to cancel");
	 * util.saveTestPattern(pattern, p, page); } } catch (ConversationException
	 * e) { // TODO Auto-generated catch block e.printStackTrace(); } return
	 * false; }
	 * 
	 * private boolean release(String rallyUrl) { RallyPoint rallyPoint =
	 * village.getRallyPoint(); return rallyPoint.releasePrisoners(util);
	 * boolean result = false; try { // get page String baseUrl =
	 * util.getBaseUrl(); String page = util.httpGetPage(baseUrl + rallyUrl);
	 * String pattern = "rallyPoint.release"; Pattern p =
	 * util.getPattern(pattern); Matcher m = p.matcher(page); while (m.find()) {
	 * // EventLog.log("Releasing Prisoners"); // String releaseUrl =
	 * m.group(1); EventLog.log("Releasing Prisoners from "+m.group(1)); String
	 * releaseUrl = m.group(2); page = util.httpGetPage(baseUrl+releaseUrl);
	 * result = true; } if (!result) { // no error
	 * log.debug("cannot find any troops to release"); //
	 * util.saveTestPattern(pattern, p, page); } } catch (ConversationException
	 * e) { // TODO Auto-generated catch block e.printStackTrace(); } //
	 * System.exit(0); return result; }
	 */

	/**
	 * @param 
	 */
	
	/**
	 * 
	 */
	private boolean checkNextAction(boolean execute) {
		// always clear?
		nextMovementTime = null;
		nextOne = -1;
		// check for automatic dodging
		String dodgeType = super.config.getString("/dodge/@enabled", null);
		if ((dodgeType != null) && !dodgeType.equalsIgnoreCase("false")) {
			// check if any more incoming attacks
			// only do for execute not check time when runnable 
			// causes problems when called from getTWR this.village.updateTroopMovements();
			if (execute) {
				// ensure village has latest updates first
				this.village.updateTroopMovements();				
			}
			movement.get(0).startTime = village.getNextAttackTime();
			movement.get(0).nodeTime = movement.get(0).startTime;
			movement.get(0).type = dodgeType;
			// movement.set(0, dodge); // do I need to do this?
			nextMovementTime = village.getNextAttackTime();
			if (nextMovementTime != null) {
				log.debug("dodge=" + dodgeType);
				movement.get(0).active = true;
				nextOne = 0;
				// only report when strategy called
				if (execute) {
					EventLog.log("Incoming Attack at " + nextMovementTime);
				}
			}
		}
		// check for time action list
		timeNodes = super.config.configurationsAt("/time");
		int n = 0;
		for (SubnodeConfiguration timeNode : timeNodes) {
			// next node - dodge is always 0
			n++;
			Date now = new Date(); // current time
			String typeOfTime = timeNode.getString("/@type", START);
			String timeFormat = timeNode.getString("/@format", TIME_FORMAT);
			// int refuseTime = timeNode.getInt("/time/@refuse", 5);
			String time = timeNode.getString("/");
			maxLateMilli = timeNode.getInteger("/@maxLateMinutes", 0)
					* Util.MILLI_MINUTE;
			log.debug("type=" + typeOfTime + " maxLate=" + maxLateMilli
					+ " timeFormat=" + timeFormat + " time=" + time);
			Date startDate = null;
			try {
				// Get time of movement
				SimpleDateFormat sdf = new SimpleDateFormat(timeFormat);
				// check if in future
				startDate = sdf.parse(time);
				if ((movement.size() <= n)
						|| (!startDate.equals(movement.get(n).nodeTime))) {
					TroopMovement node;
					if (movement.size() <= n) {
						node = new TroopMovement(startDate);
						movement.add(n, node);
						log.debug("Adding Node " + n + "=" + node.toString());
					} else {
						node = movement.get(n);
						log.debug("Setting Node " + n + " to " + startDate
								+ " was=" + node.toString());
					}
					node.nodeTime = startDate;
					node.type = timeNode.getString("/@movement", "REINFORCE");
					node.maxLate = maxLateMilli;
					// node.config = timeNode;
					if ((startDate.getTime() + maxLateMilli) > now.getTime()) {
						// not too late to start
						// do timing check only - dont send
						node.target = new Coordinates(timeNode, "", "");
						node.villageName = timeNode.getString("/@village", "");
						if (typeOfTime.equals(ARRIVE)) {
							// check if any troops
							RallyPoint rallyPoint = village.getRallyPoint();
							if (rallyPoint == null) {
								log.warn("No rally point");
								// Try again later - assume building rally point
								// and troops
								node.nodeTime = null;
							} else {
								
								
								// gac risky if called from getTWR from a command
								
								
								// want to arrive at specified time
								super.village.gotoMainPage(true); // Ensure you
																	// do it
																	// from the
																	// right
																	// village
																	// (someone
																	// might
																	// have
																	// clicked
																	// to a
																	// different
																	// village
																	// meanwhile)
								// use test mode to find out how long will take
								TroopTypeMap available = rallyPoint
										.fetchSendableTroops(super.util, true);
								TroopTypeMap toSend = new TroopTypeMap();
								// check if any to send
								if (toSend.getConfigValues(super.util, super
										.getDesc(), super.config, available) > 0) {
									int secondToArrive = rallyPoint.sendTroops(
											util, node.target.getX(),
											node.target.getY(),
											node.villageName, toSend,
											TroopTransferType.REINFORCE, null,
											true, true, null);
									Date arriveDate = sdf.parse(time);
									startDate = new Date(
											arriveDate.getTime()
													- (secondToArrive * Util.MILLI_SECOND));
									if ((startDate.getTime() + maxLateMilli) > now.getTime()) {
										EventLog.log("Send Troops at "
												+ startDate + " to arrive for "
												+ time);
										node.startTime = startDate;
										node.active = true;
										// dont save tSend as may be more troops
										// later
									} else {
										EventLog.log("Too late at " + startDate
												+ " to arrive for " + time
												+ " + maxLate=" + maxLateMilli
												+ " Travel Time " + Util.milliToTimeString(secondToArrive * Util.MILLI_SECOND));
										node.active = false;
										continue;
									}
								} else {
									log.warn("Not Enough Troops to Check timing");
									node.nodeTime = null; // try again later
									node.active = true;
									continue;
								}
							}
						} else {
							// send at specified time - already set
							EventLog.log("Send Troops at " + time);
							node.startTime = startDate;
							node.active = true;
						}
					} else {
						EventLog.log("Too late to send at " + time
								+ " + maxLate=" + maxLateMilli);
						node.active = false;
						continue;
					}
				} else {
					// already in list
					log.trace(n + " node Already in list - active="
							+ movement.get(n).active);
				}
			} catch (ParseException e) {
				log.error("Invalid Date Format", e);
				// return TimeWhenRunnable.NEVER;
				return false;
			} catch (ConversationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} // endFor
		// check for earliest
		nextMovementTime = null;
		for (n = 0; n < movement.size(); n++) {
			log.trace(n + " Check Earlier " + movement.get(n).toString());
			// check earliest
			if (movement.get(n).active) {
				// check if earliest action - dodge should take priority
				if ((nextMovementTime == null)
						|| movement.get(n).startTime.before(nextMovementTime)) {
					// EventLog.log("Earlier Node "+n+"="+movement.get(n).toString());
					nextMovementTime = movement.get(n).startTime;
					nextOne = n;
				}
			}
		}
		// check if any action
		if (nextMovementTime != null) {
			// set defaults
			// long nextMilli = nextMovementTime.getTime() - now.getTime();
			suspendMilli = super.config.getLong("/@sleep", MIN_SUSPEND_TIME)* Util.MILLI_SECOND;
			delayMilli = (long) (super.config.getDouble("/@delay", 0)* Util.MILLI_SECOND);
			// check if dodging
			if (nextOne == 0) {
				// get recall if dodging
				recallMilli = super.config.getLong("/dodge/@recall", RECALL_TIME)* Util.MILLI_SECOND;
				waitMilli = super.config.getLong("/dodge/@wait", WAIT_TIME)* Util.MILLI_SECOND;
				// cannot be late
				maxLateMilli = 0;
				// set delay to the recall time so used to send earlier than attack
				// delayMilli = recallMilli;
				// if want to include delay in the recall calculation as well
				delayMilli += recallMilli;
				// set rest of config info
				movement.get(nextOne).target = new Coordinates(super.config, "dodge", 999, 999);
				movement.get(nextOne).villageName = super.config.getString("/dodge/@village", "");
				// log.trace("NextAction Dodge "+movement.get(0).toString());
			} else {
				// no recall and set wait to delay
				recallMilli = 0;
				waitMilli = delayMilli;
				maxLateMilli = movement.get(nextOne).maxLate;
			}
			log.debug(nextOne + " Earliest Movement "
					+ movement.get(nextOne).toString());
			return true;
		}
		return false;
	}

	/**
	 * getTimeWhenRunnable can set new time if required rather than waiting for
	 * execute
	 * 
	 * @return
	 */
	public TimeWhenRunnable getTimeWhenRunnable() {
		// going to webpage in check causes problems for list
		// not a good idea for quick check
		// modify so execute does the update
		// check then only uses result and config info
		
		
		// check if anything to do
		if (checkNextAction(false)) {
			log.debug("Setting next TimeWhenRunnable sharp WAIT before "
					+ nextMovementTime.toString());
			// return new TimeWhenRunnable(nextMovementTime.getTime()
			setTimeWhenRunnable( new TimeWhenRunnable(nextMovementTime.getTime()
					- (WAIT_TIME * Util.MILLI_SECOND), true));
		} else {
			/*
			log.trace("Setting nextTWR minPauseMinutes");
			setTimeWhenRunnable( new TimeWhenRunnable(System.currentTimeMillis()
					+ (minPauseMinutes * Util.MILLI_MINUTE), false));
			*/
			log.trace("not changing TimeWhenRunnable");
		}
		// now call normal method
		return super.getTimeWhenRunnable();
	}

	@Override
	public boolean modifiesResources() {
		return false;
	}
}

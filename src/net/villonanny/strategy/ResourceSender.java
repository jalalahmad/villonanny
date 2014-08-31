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
import net.villonanny.entity.MarketSite;
import net.villonanny.type.BuildingType;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class ResourceSender extends Strategy {

    private static final Logger log = Logger.getLogger(ResourceSender.class);
    private static final String HOURLY_SUFFIX = "hr";
    private ResourceTypeMap cheapestResources = null;

    public TimeWhenRunnable execute() throws ConversationException {
        // <strategy desc="SR01" class="ResourceSender" enabled="true">
        // <options cleverWait="true" delayMinutes="60" randomOrder="true"  fullLoadOnly="false" maxMerchants="2" minSum="0"/>
        // <keepResources wood="" clay="" iron="" crop=""/>
        // <sendMerchant x="" y="" village="" wood="" clay="" iron="" crop="" requestedfrom="requesterUID" allowLess="false"/>
        // <sendMerchant x="" y="" village="" wood="" clay="" iron="" crop="" allowLess="false"/>
        // <sendMerchant x="" y="" village="" wood="" clay="" iron="" crop="" maxMerchants="1"/>
        // <sendMerchant x="" y="" village="" wood="" clay="" iron="" crop="" merchants="true"/>
        // </strategy>
    	// requestedFrom is the uid of the strategy that requested the resources. see RequestResources strategy. fofo
    	// fullLoadOnly="true" means only fully loaded merchants get sent. fofo
    	// maxMerchants limits the max number of merchants to send. sets allowLess=true <sendMerchant setting takes precedence over <options setting. fofo
    	// minSum sets the minimum sum of resources we bother to send. use to reduce number of send's with requestResources
    	// merchants="true" means that amount is number of merchants not resource so sends whole numbers - useful if building trade office
    	log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
        	ResourceTypeMap maxResources = village.getMaxResources();
        	if (maxResources==null) {
        		village.update();
        		log.debug("no village.maxresources, updating village");
        		maxResources = village.getMaxResources();
        	}
            long shortestTimeToWait = Long.MAX_VALUE;
            cheapestResources = null;
			boolean cleverWait = config.getBoolean("/options/@cleverWait", false);
			boolean smartRound = config.getBoolean("/options/@smartRound", false);
            int delayMinutes = config.getInt("/options/@delayMinutes", 60);
            int globalMaxMerchants = config.getInt("/options/@maxMerchants", -1);
            // fofo added global was int maxMerchants = config.getInt("/options/@maxMerchants", -1);
            int minSum = config.getInt("/options/@minSum", 0);
            delayMinutes = config.getInt("/options/@minPauseMinutes", delayMinutes);
            boolean randomOrder = config.getBoolean("/options/@randomOrder", true);
            // get alternatives for minPauseOption
            delayMinutes = config.getInt("/@minPauseMinutes", delayMinutes);
            delayMinutes = config.getInt("/options/@minPauseMinutes", delayMinutes);
            // move check on Market to start - no point doing any other checks of calculations if no market yet
			MarketSite market = (MarketSite) village.getBuildingMap().getOne(BuildingType.MARKETPLACE);
            // use minLevel as option to wait for Market not disable, could use waitFor="Market" on strategy line if not a resource
            int marketLevel = config.getInt("/options/@minLevel", -1);
			if (marketLevel >= 0) {
	            if ((market == null) || (village.getMinLevel(market.getName()) < marketLevel)) {
	            	// does not meet minimum requirements so check again later
	            	EventLog.log("Market Level "+village.getMinLevel(market.getName())+" Needs to be "+marketLevel);
	    			return new TimeWhenRunnable(System.currentTimeMillis() + delayMinutes*Util.MILLI_MINUTE);
	            } 
			} else {
				// check exists at all
				if (market == null) {
					EventLog.log("No market found; disabling");
					return TimeWhenRunnable.NEVER;
				}
			}
            List<SubnodeConfiguration> merchantNodes = config.configurationsAt("/sendMerchant");
            if (randomOrder) {
                // Each time the strategy runs, I start with a random sendMerchant node so that
                // statistically all of the nodes will have an equal chance to run.
                // Otherwise the last nodes might always end up with no merchants left.
                List<SubnodeConfiguration> randomList = new ArrayList<SubnodeConfiguration>();
                for (SubnodeConfiguration merchantNode : merchantNodes) {
                    int pos = (int) (Math.random() * (randomList.size() + 1));
                    randomList.add(pos, merchantNode);
                }
                merchantNodes = randomList;
            }
            boolean noResources;
            for (SubnodeConfiguration merchantNode : merchantNodes) {
                boolean allowLess = merchantNode.getBoolean("@allowLess", false);
				log.trace("allowLess is:"+allowLess);
                noResources=false;
    			boolean fullLoadOnly = config.getBoolean("/options/@fullLoadOnly", false);
    			fullLoadOnly = merchantNode.getBoolean("/@fullLoadOnly", fullLoadOnly);
                String requestedFrom = merchantNode.getString("@requestedFrom", "");
				log.debug("requestedFrom is:"+requestedFrom);
				ResourceTypeMap resourceToSend = getResources(merchantNode, village.getProduction());
				log.debug("Resources to send from config is "+resourceToSend.toStringNoFood());
				// @author gac 
				// move market to start also means can get merchant capacity so can use merchants not totals
				// capacity should only vary in loop if build finishes
				// new fetch was market.fetch(util);
				market.marketFetch(util);
				int merchantCapacity = market.getMerchantCapacity();
                boolean merchantCount = merchantNode.getBoolean("@merchants", false);
				log.trace("merchants is:"+merchantCount);
                // send is number of merchants not total so convert
                if (merchantCount) {
    				log.trace("from config is "+resourceToSend.toStringNoFood());
                	resourceToSend.multiply(merchantCapacity);
    				log.debug("from config *"+merchantCapacity+" is "+resourceToSend.toStringNoFood());
                } else {
    				log.debug("from config is "+resourceToSend.toStringNoFood());
                }
				
                //Please write some comments on what the code is supposed to do, so the next guy can
				    //continue the work with reasonable speed. fofo
				    //below TimeWhenRunnable execute() is the alpha documentation, 
				    //put new functionality in there with the old one please
                /* Travian Style Coordinates in configuration
                 * @author Czar
                 */
                String x;
                String y;
                String coordsTravianStyle = merchantNode.getString("@coords", null);
                if (null != coordsTravianStyle) { // using travian-style configuration, if exists
                    EventLog.log("Coords travian style...");
                    String[] coords = coordsTravianStyle.split("[(|)]");
                    x = coords[1];
                    y = coords[2];
                } else { // if not reverting to old style
                    x = merchantNode.getString("@x", "");
                    y = merchantNode.getString("@y", "");
                }
                // end of travian-style modification
                log.debug("Global maxMerchants="+globalMaxMerchants);
                int maxMerchants = merchantNode.getInt("@maxMerchants", globalMaxMerchants); //maxMerchants in merchantNode overrides the globally set one
                if (maxMerchants>=0) {
                	allowLess=true;
                }
                log.debug("Local maxMerchants="+maxMerchants);
                ResourceTypeMap requested;
                log.debug("checking request from\'"+requestedFrom+"\'");
                if (requestedFrom !="") {
                	String [] coords = village.strategyDone.getRequestingCoords (requestedFrom);
                	if (coords!=null) {
                		x=coords[0];
                		y=coords[1];
                		log.debug ("Requesting strategy "+ requestedFrom+" x="+x+" y="+y);
                	} else {
                		log.debug("no coords found from "+requestedFrom);
                	}
                	requested = village.strategyDone.getRequestedResources(requestedFrom);
                    if (requested != null) {
						for (ResourceType aresource : requested.keySet()) {
							if (aresource == ResourceType.FOOD) {
								continue;
							}
							if ((requested.get(aresource)<resourceToSend.get(aresource))&&
									(resourceToSend.get (aresource)>=0)) {                //always send the smallest amount of config res. and requested res. fofo
								resourceToSend.put (aresource, requested.get(aresource)); 
							}
							else {
								if (resourceToSend.get(aresource)<0) { //negative config res indicates nothing set, so no limits
									resourceToSend.put (aresource, requested.get(aresource));
								}
							}
						}
					} else {
						resourceToSend = new ResourceTypeMap (); //initialized with new, so its all zero
					}
                    log.debug("serving request from "+requestedFrom+" at("+x+"|"+y+") req ress is "+resourceToSend.toStringNoFood());
                }
                log.debug("after request  "+resourceToSend.toStringNoFood());
                
                String targetVillage = merchantNode.getString("@village", null);
                if ((x == "" && y == "" && targetVillage == null) &&requestedFrom=="") {
                    log.error("Strategy had a loading error; disabling");
                    EventLog.log("Strategy had a loading error; disabling");
                    return TimeWhenRunnable.NEVER;
                }

                // keepResources (start)
                ResourceTypeMap resourceToKeep = new ResourceTypeMap();
                try {
                    SubnodeConfiguration keepNode = config.configurationAt("/keepResources");
                    if (keepNode==null) {
                    	log.debug("keepNode is null");
                    }
                    resourceToKeep.put(ResourceType.WOOD, getKeepValue(keepNode, "@wood", maxResources.getWood()));
                    resourceToKeep.put(ResourceType.CLAY, getKeepValue(keepNode, "@clay", maxResources.getClay()));
                    resourceToKeep.put(ResourceType.IRON, getKeepValue(keepNode, "@iron", maxResources.getIron()));
                    resourceToKeep.put(ResourceType.CROP, getKeepValue(keepNode, "@crop", maxResources.getCrop()));
                    log.debug("config resourses to keep "+resourceToKeep.toStringNoFood());
                	
                } catch (IllegalArgumentException e) {
                	// optional so ignore if not set
                }

                // keepResources (end)
                //***** start of code moved here from MarketSite
                if (smartRound==true ) {
                	resourceToSend = smartRound (resourceToSend, 1000);   //try to round off sent resources so it looks a bit more human. fofo
                }
				/* moved above to get and use capacity market.fetch(util);
				MarketSite market = (MarketSite) village.getBuildingMap().getOne(BuildingType.MARKETPLACE);
				if (market == null) {
					EventLog.log("No market found; disabling");
					return TimeWhenRunnable.NEVER;
				}
				market.marketFetch(util);
				*/
		        ResourceTypeMap available = market.getAvailableResources(util);
				ResourceTypeMap checkedResourceToSend = checkAvailableResources (resourceToSend, 
																				resourceToKeep, 
																				available, 
																				allowLess);
				if (checkedResourceToSend==null) { //not enough resources
                        log.debug("No Resources to Send");
                        noResources = true;
                } else {
                	if (checkedResourceToSend.getSumOfValues() < minSum) {
                	log.debug("Sum of res to send is less than minSum, not sending");
                	noResources=true;
                	}
                }
                //***** end code moved from MarketSite

                long timeToWaitSeconds=-1;
				if (noResources==false) {
					//village.gotoMainPage();
					int merchantsFree = market.getMerchantsFree();
					log.debug("merchantsfree="+merchantsFree+" maxMerchants="+maxMerchants);
					if (maxMerchants>=0 && maxMerchants<merchantsFree) {
						log.debug("merchantsfree="+merchantsFree+" reduced to "+maxMerchants);
						merchantsFree = maxMerchants;
					}
					// moved above int merchantCapacity = market.getMerchantCapacity();
					if ((checkedResourceToSend.getSumOfValues() > (merchantsFree*merchantCapacity)) ||
							(fullLoadOnly==true)) {
						resourceToSend = scaleForMerchants (merchantCapacity, merchantsFree, fullLoadOnly, available, 
															resourceToSend, resourceToKeep, checkedResourceToSend);
					} else {
						resourceToSend = checkedResourceToSend;
					}
	                // check against merchants available
					log.debug("Available before: "+market.getAvailableResources(util).toStringNoFood());
					timeToWaitSeconds = market.sendResource(util,
							resourceToSend, x, y, targetVillage, allowLess,
							village, requestedFrom, fullLoadOnly);
					log.debug("Available after: "+market.getAvailableResources(util).toStringNoFood());
				} else {
					if (targetVillage==null || targetVillage.equals("")) {
						targetVillage=requestedFrom;
					}
					EventLog.log("No resources sent to "+targetVillage);
				}
                long timeToWaitMillis = timeToWaitSeconds * Util.MILLI_SECOND;
                int rc = 0;
                if (timeToWaitSeconds <= 0) {
                    rc = (int) timeToWaitSeconds;
                    timeToWaitMillis = delayMinutes * Util.MILLI_MINUTE;
                }
                if (timeToWaitMillis < shortestTimeToWait) {
                    shortestTimeToWait = timeToWaitMillis;
                }
                if (!cleverWait) {
                    shortestTimeToWait = delayMinutes * Util.MILLI_MINUTE;
                } else {
                    if ((noResources || rc== MarketSite.RC_NORESOURCES) && (cheapestResources == null || resourceToSend.getSumOfValues() < cheapestResources.getSumOfValues())) {
                        cheapestResources = resourceToSend;
                    }
                }
            }
            // check that valid time set
            if (shortestTimeToWait == Long.MAX_VALUE) {
                log.error("Strategy has an error; disabling");
                EventLog.log("ResourceSender Strategy has an error; disabling");
                return TimeWhenRunnable.NEVER;
            }
            return new TimeWhenRunnable(System.currentTimeMillis() + shortestTimeToWait);
        } finally {
            NDC.pop();
        }
    }

    ResourceTypeMap checkAvailableResources (ResourceTypeMap resources, 
    		  								ResourceTypeMap resourceToKeep,
    		  								ResourceTypeMap available,
    		  								boolean allowLess) {
        // Check if there are enough resources within the limits we have
        ResourceTypeMap sendableResources = new ResourceTypeMap();
        log.debug("available is "+available.toStringNoFood());
        log.trace("want to send "+resources.toStringNoFood());
        log.trace("to keep      "+resourceToKeep.toStringNoFood());
        for (ResourceType aresource : available.keySet()) {
            if (aresource == ResourceType.FOOD) {
                continue;
            }
            int toSend = resources.get(aresource);
            int got = available.get(aresource);
            // keepResources (start)
            // check each resource is greater than keep threshold
            int keep = resourceToKeep.get(aresource);
            int origToSend = toSend;  //for log only
            if ((got - toSend) < keep) {
                if (allowLess) {
                    toSend = got - keep;
                    if (toSend < 0) {
                        toSend = 0;
                    }
                    //log.debug("checkAvailableResources Resource " + aresource + " limited from " + origToSend + " to " + toSend);
                } else {
                    //log.debug("checkAvailableResources Not enough resources");
                    return null;
                }
            }
            sendableResources.put(aresource, toSend);
        }
        log.debug("after check   "+sendableResources.toStringNoFood());
        if (sendableResources.getSumOfValues()<=0) {
        	return null;
        }
        return sendableResources;
    }
    ResourceTypeMap scaleForMerchants (int merchantCapacity, int merchantsFree, 
    									boolean fullLoadOnly,
    									ResourceTypeMap available,
    									ResourceTypeMap resourceToSend, //the original amount we wanted to send
    									ResourceTypeMap resourceToKeep, //resources we want to have in storage after we send some away
    									ResourceTypeMap checkedResourceToSend) { //the amount possible to send, taking resourceToKeep and resources available into account

    	//this routine tries to scale the amount to send down to available merchants, while keeping the amount of the res. we have little of intact
    	//so if you want to send 2000 clay and 2000 wood, 
    	//you have 1 merchant that takes 1500 res., 
    	//you have 4000 clay and 500 wood in storage
    	//it will try to scale it down do you end up sending 
    	//close to (1500 clay and 500 wood), instead of 1200 clay and 300 wood that a straight scale would do. fofo
    	//using only round might result in less than totalMercCapacity, so the mail iteration is done with Math.ceil
    	ResourceTypeMap merchantScaledRes = new ResourceTypeMap();
    	int totalMercCapacity=merchantCapacity * merchantsFree;
    	int merchantsNeeded=-1;
    	if (fullLoadOnly) {
    		merchantsNeeded = checkedResourceToSend.getSumOfValues()/merchantCapacity;
    		if (checkedResourceToSend.getSumOfValues()<merchantCapacity) {
    			return checkedResourceToSend; //too little resources to fill one merchant, nothing to do here
    		}
    		if (merchantsNeeded<merchantsFree) {
    			totalMercCapacity = merchantCapacity * merchantsNeeded; //replace with a the smaller number
    		}
    	}
    	for (ResourceType aresource : merchantScaledRes.keySet()) {
			merchantScaledRes.put(aresource, resourceToSend.get(aresource));
		}
		log.debug("merchantsFree ="+merchantsFree+" merchantsNeeded= "+merchantsNeeded+" totalMercCapacity="+totalMercCapacity);
		log.debug("scaleForMerchants Before loop wanted to send "+resourceToSend.toStringNoFood()+" sum is "+Integer.toString(resourceToSend.getSumOfValues()));
		log.debug("scaleForMerchants Before loop scaledRes      "+merchantScaledRes.toStringNoFood()+" sum is "+Integer.toString(merchantScaledRes.getSumOfValues()));
		log.debug("scaleForMerchants Before loop checkedResRes  "+checkedResourceToSend.toStringNoFood()+" sum is "+Integer.toString(checkedResourceToSend.getSumOfValues()));
    	if (totalMercCapacity > 0) { 
			for (int i=0 ; i<5 ; i++) { //get close to, but probably a bit above totalMercCapacity
				double scale= (double) totalMercCapacity/(double) checkedResourceToSend.getSumOfValues();
				log.debug("scaleForMerchants ceil Iteration "+Integer.toString(i)+"scale is "+Integer.toString((int) (scale*100.0))+"%");
				for (ResourceType aresource : merchantScaledRes.keySet()) { //scale down the original request
					int scaledRes = (int) Math.ceil(merchantScaledRes.get(aresource) * scale); 
					merchantScaledRes.put(aresource, scaledRes);
				}
				//log.debug("scaleForMerchants ceil Iteration "+Integer.toString(i)+"wanted to send "+resourceToSend.toStringNoFood()+" sum is "+Integer.toString(resourceToSend.getSumOfValues()));
				checkedResourceToSend = checkAvailableResources (merchantScaledRes, resourceToKeep, available, true);
				//log.debug("scaleForMerchants ceil Iteration "+Integer.toString(i)+"scaledRes      "+merchantScaledRes.toStringNoFood()+" sum is "+Integer.toString(merchantScaledRes.getSumOfValues()));
				//log.debug("scaleForMerchants ceil Iteration "+Integer.toString(i)+"checkedResRes  "+checkedResourceToSend.toStringNoFood()+" sum is "+Integer.toString(checkedResourceToSend.getSumOfValues()));
			}
			/*the below sometimes takes the amount below merchant capacity, so better let MarketSite take care of the rest. fofo
			for (int i=0 ; i<2 ; i++) { //try to get to the exact totalMercCapacity
				double scale= (double) totalMercCapacity/(double) checkedResourceToSend.getSumOfValues();
				log.debug("scaleForMerchants round Iteration "+Integer.toString(i)+"scale is "+Integer.toString((int) (scale*100.0))+"%");
				for (ResourceType aresource : merchantScaledRes.keySet()) { //scale down the original request
					int scaledRes = (int) Math.round(merchantScaledRes.get(aresource) * scale); 
					merchantScaledRes.put(aresource, scaledRes);
				}
				log.debug("scaleForMerchants round Iteration "+Integer.toString(i)+"wanted to send "+resourceToSend.toStringNoFood()+" sum is "+Integer.toString(resourceToSend.getSumOfValues()));
				checkedResourceToSend = checkAvailableResources (merchantScaledRes, resourceToKeep, available, true);
				log.debug("scaleForMerchants round Iteration "+Integer.toString(i)+"scaledRes      "+merchantScaledRes.toStringNoFood()+" sum is "+Integer.toString(merchantScaledRes.getSumOfValues()));
				log.debug("scaleForMerchants round Iteration "+Integer.toString(i)+"checkedResRes  "+checkedResourceToSend.toStringNoFood()+" sum is "+Integer.toString(checkedResourceToSend.getSumOfValues()));
			} 
			*/
		}
		return checkedResourceToSend;
    }

    ResourceTypeMap smartRound (ResourceTypeMap resources, int roundBorder) {
    	resources.put (ResourceType.FOOD, 0);
    	for (ResourceType aresource : resources.keySet()) {
    		if (aresource == ResourceType.FOOD) {
                continue;
            }
            int temp = resources.get (aresource);
            if (temp < (roundBorder/2)) {
            	temp=temp/10;  //int division rounds down to nearest integer
            	temp=temp*10;  //multiply up again without the fraction
            } else {
                if (temp < (roundBorder)) {
                	temp=temp/50;  //int division rounds down to nearest integer
                	temp=temp*50;  //multiply up again without the fraction
                } else {
                	temp=temp/100;
                	temp=temp*100;
                }
            }
            resources.put (aresource,temp);
     	}
    	return resources;
    }
    
@Override
    public boolean modifiesResources() {
        return true;
    }

    /**
     * Return the minimum resources needed to run, or null if not applicable
     *
     * @return
     */
    @Override
    public ResourceTypeMap getTriggeringResources() {
        return cheapestResources;
    }

    private int getHour(String entry) {
        // Pattern p = Pattern.compile("^(\\d+)" + HOURLY_SUFFIX + "$");
        Pattern p = util.getPattern("resourceSender.hour", HOURLY_SUFFIX);
            //why use util.getPattern("resourceSender.hour when we have full control on both pattern and matcher data? fofo
        Matcher m = p.matcher(entry);
        if (m.matches() && m.groupCount() == 1) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private int getValue(SubnodeConfiguration merchantNode, int hourlyProduction, String resourceAttribute) {
        int value = getHour(merchantNode.getString(resourceAttribute, "")) * hourlyProduction;
        if (value == 0) {
            value = merchantNode.getInt(resourceAttribute, -1);
        }
        return value;
    }

    private ResourceTypeMap getResources(SubnodeConfiguration merchantNode, ResourceTypeMap hourlyProduction) {
        int wood = getValue(merchantNode, hourlyProduction.getWood(), "@wood");
        int clay = getValue(merchantNode, hourlyProduction.getClay(), "@clay");
        int iron = getValue(merchantNode, hourlyProduction.getIron(), "@iron");
        int crop = getValue(merchantNode, hourlyProduction.getCrop(), "@crop");
        return new ResourceTypeMap(wood, clay, iron, crop, 0);
    }
    
    private int getPercent(String entry, int maxValue) {
        // Pattern p = Pattern.compile("^(\\d+)" + HOURLY_SUFFIX + "$");
        Pattern p = Pattern.compile("(\\d+)\\%");
        Matcher m = p.matcher(entry);
        //log.debug("percent test value "+entry);
        if (m.matches() && m.groupCount() == 1) {
        	//log.debug("found percent ("+m.group(1)+")");
            int value = Integer.parseInt(m.group(1));
        	if (value > -1) {
        		if (value>100) {
        			value=100;
        		}
        		value = (value * maxValue/100);
        		//log.debug("storage is "+maxValue+" to keep is "+value);
        		return value;
        	}
        }
        //log.debug("no percent found. value is("+m.group(1)+")");
        return Integer.parseInt(entry); 
    }

    private int getKeepValue(SubnodeConfiguration resourceNode, String resourceAttribute, int maxValue) {
    	//if keepResources is written wood="75%", return value for wood is 75% of maxResources value
    	//else return value is the regular number 
    	int value = 0;
    	String resource = resourceNode.getString(resourceAttribute, "-1");
    	value = getPercent (resource, maxValue);
    	//log.debug("To keep value is "+value);
        return value;
    }

}

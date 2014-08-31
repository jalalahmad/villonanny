package net.villonanny.strategy;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.entity.MarketSite;
import net.villonanny.type.ResourceType;

/**
 * This strategy offers resources on the marketplace.
 * Example: 
 * <offer offer="Lumber"
 * amount="5" search="Clay" ratio="1.5" round="100" nrOfOffer="2" ownclan="false"
 * distance="2" sleepMinutes="60"/>
 * 
 * @author biminus
 * 
 */
public class Offer extends Strategy {
    private static final Logger log = Logger.getLogger(Offer.class);
    private static final String OFFER_NODE = "/offer";
    private static final String ATTR_OFFER_TYPE = "/@offer";
    private static final String ATTR_SEARCH_TYPE = "/@search";
    private static final String ATTR_OFFER_COUNT = "/@nrOfOffer";
    private static final String ATTR_OFFER_AMOUNT = "/@amount";
    private static final String ATTR_OFFER_RATIO = "/@ratio";
    private static final String ATTR_SEARCH_ROUND = "/@round";
    private static final String ATTR_OWNCLAN = "/@ownclan";
    private static final String ATTR_DISTANCE = "/@distance";
    private static final String ATTR_SLEEPMINUTES = "/@sleepMinutes";

    // if not specified offer only one.
    private static final int DEFAULT_OFFER_COUNT = 1;
    // default ration 1:1
    private static final float DEFAULT_RATIO = 1f;
    // round it up or down (according to the sign) default is hundred.
    private static final int DEFAULT_ROUND = 100;
    // default distance is 2 hours
    private static final int DEFAULT_DISTANCE = 2;

    private static final int DEFAULT_SLEEP_MINUTE = 60;

    private ResourceType offerType = ResourceType.WOOD;
    private ResourceType searchType = ResourceType.CROP;
    private int offerCount;
    private int offerAmount;
    private float ratio = DEFAULT_RATIO;
    private int round = DEFAULT_ROUND;
    private boolean ownClan;
    private int distance = DEFAULT_DISTANCE;
    private int sleepMinutes = 60;

    @Override
    public TimeWhenRunnable execute() throws ConversationException,
            InvalidConfigurationException {
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
            MarketSite ms = village.getMarket();
            if (ms == null) {
                log.error("MarketSite is not present");
                return TimeWhenRunnable.NEVER;
            }
            // ms.fetch(util);
            // fofo renamed
            ms.marketFetch(util);
            readConfig(ms);
            if (offerType == searchType) {
                msg("Offer and search resources must differ, both are: "
                        + offerType.toString());
                return TimeWhenRunnable.minutesFromNow(sleepMinutes);
            }
            if (ms.getMerchantsFree() == 0) {
                msg("There are no free merchants");
                return TimeWhenRunnable.minutesFromNow(sleepMinutes);
            }
            // gac add keep resources - simple at moment use getSpare if want to allowLess
            if (!village.checkSpareResources(config)){
                msg("Not enough resource");            	
                return TimeWhenRunnable.minutesFromNow(sleepMinutes);
            }
            if (ms.getMerchantsFree() < ms.merchantsForResources(offerAmount)
                    * offerCount) {
                msg("There are less free merchants than needed, still continue.");
            }
            int availableOfferCount = getOfferCount(ms, offerAmount);
            if (availableOfferCount > offerCount) {
                availableOfferCount = offerCount;
            }

            if (availableOfferCount == 0) {
                msg("There are not enough free merchants ("+ms.getMerchantsFree()+")");
                return TimeWhenRunnable.minutesFromNow(sleepMinutes);
            }

            // gac change to match one used EventLog.log("Offering " + offerCount + " times the same offer.");
            EventLog.log("Offering " + availableOfferCount + " times the same offer.");
            for (int i = 0; i < availableOfferCount; i++) {
                ms.sellResources(util, new MarketSite.SellConfig(offerType,
                        offerAmount, searchType, calculateSearchAmount(
                                offerAmount, ratio, round), distance, ownClan));
            }
        } finally {
            NDC.pop();
        }
        return TimeWhenRunnable.minutesFromNow(sleepMinutes);
    }

    private int getOfferCount(MarketSite ms, int offerAmount) {
        return ms.getMerchantsFree() / ms.merchantsForResources(offerAmount);
    }

    private int calculateSearchAmount(int amount, float ratio, int round) {
        int result = (int) (amount * ratio);
        // gac - check if exact and only if not then round up
        if ((result % round) != 0) {
            result += round - (result % round);        	
        }
        return result;
    }

    private void msg(String msg) {
        EventLog.log(msg);
        log.info(msg);		// not always an error
    }

    private void readConfig(MarketSite ms) throws InvalidConfigurationException {
        SubnodeConfiguration snc = config.configurationAt(OFFER_NODE);
        try {

            offerType = ResourceType.fromString(translator.getKeyword(snc
                    .getString(ATTR_OFFER_TYPE)));
            searchType = ResourceType.fromString(translator.getKeyword(snc
                    .getString(ATTR_SEARCH_TYPE)));
        } catch (IllegalArgumentException iae) {
            String errmsg = "invalid type" + iae;
            log.error(errmsg);
            throw new InvalidConfigurationException(errmsg);
        }
        offerAmount = snc.getInt(ATTR_OFFER_AMOUNT, ms.getMerchantCapacity());
        offerCount = snc.getInt(ATTR_OFFER_COUNT, DEFAULT_OFFER_COUNT);
        ratio = snc.getFloat(ATTR_OFFER_RATIO, DEFAULT_RATIO);
        round = snc.getInt(ATTR_SEARCH_ROUND, DEFAULT_ROUND);
        distance = snc.getInt(ATTR_DISTANCE, DEFAULT_DISTANCE);
        ownClan = snc.getBoolean(ATTR_OWNCLAN, false);
        sleepMinutes = snc.getInteger(ATTR_SLEEPMINUTES, DEFAULT_SLEEP_MINUTE);
    }
}

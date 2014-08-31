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

//<strategy desc="Test TroupManager" class="TroupManager" enabled="true" minPauseMinutes="5">
//	<target x="-65" y="26" village="TARGET_VILLAGE" type="reinforce" rate="1" item=""/>
//		<troups2 min="10%" allowLess="true">100</troups2>
//		<time type="arrive" refuse="5" format="dd/MM/yyyy HH:mm:ss">06/08/2008 12:06:00</time>
// <troops randomise="false" allowLess="true" min="10" type="Swordsman">20</troops>
// <troops type="Phalanx" allowLess="true" min="1" randomise="false" enabled="true">2</troops>
// <time type="start" maxLateMinutes="5" movement="reinforce" coords="(-37|137)" village="" desc="desc" format="dd/MM/yyyy HH:mm:ss">22/11/2009 21:50:00</time>
// <time type="arrive" movement="reinforce" coords="(-37|137)" village="" desc="desc" format="dd/MM/yyyy HH:mm:ss">22/11/2009 23:15:00</time>

//	@author gac
//		<dodge> enabled="true"  x="-X" y="-Y" or cords="(X|Y)" recall="30" wait="50" sleep="120"/>
//</strategy>
//
// time/@type is "start" or "arrive"
// time/@maxLateMinutes is the maximum time to send if miss start window

/**
 * 
 * @deprecated
 */
public class TroupManager extends Strategy {

    private final static Logger log = Logger.getLogger(TroupManager.class);

    public TimeWhenRunnable execute() throws ConversationException {
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        
        try {
        	EventLog.log("TroupManager not supported - use TroopManager");
			return TimeWhenRunnable.NEVER; // Quit strategy
        } finally {
            NDC.pop();
        }
    }


    @Override
    public boolean modifiesResources() {
        return false;
    }
}

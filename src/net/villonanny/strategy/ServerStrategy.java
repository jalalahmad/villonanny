package net.villonanny.strategy;

import org.apache.commons.configuration.SubnodeConfiguration;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.FatalException;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.entity.Village;

public abstract class ServerStrategy extends Strategy {
	
	abstract public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException;

	public void init(SubnodeConfiguration strategyConfig, Util util, Village village) {
		if (village!=null) {
			throw new StrategyFatalException("Logic error: this is a ServerStrategy and shouldn't be passed a village instance");
		}
		super.init(strategyConfig, util, village);
	}

	public TimeWhenRunnable getTimeWhenRunnable() {
		// waitFor and runWhile are not implemented
		return timeWhenRunnable;
	}
	
}

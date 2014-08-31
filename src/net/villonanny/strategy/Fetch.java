package net.villonanny.strategy;

import net.villonanny.ConversationException;
import net.villonanny.TimeWhenRunnable;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 * Quando una o piu' risorse scendono sotto al livello configurato, vengono prelevate dai villaggi
 * indicati ma solo se le risorse di questi superano un minimo
 * work-in-progress [xtian]
 *
 */
public class Fetch extends Strategy {
	private final static Logger log = Logger.getLogger(Fetch.class);

	public Fetch() {
	}

	public TimeWhenRunnable execute() throws ConversationException {
		//	<strategy class="Fetch" enabled="true">
		//		<source desc="San Salvario">
		//			<url>http://s1.travian3.it/dorf1.php?newdid=25748</url>
		//			<minResources>5000,5000,5000,10000</minResources>
		//		</source>
		//		<source desc="Moncalieri">
		//			<url>http://s1.travian3.it/dorf1.php?newdid=25748</url>
		//			<minResources>10000,10000,10000,20000</minResources>
		//		</source>
		//		<trigger level="80%"/>
		//	</strategy>
		NDC.push(getDesc()); // Strategy
		try {
			
			
		} finally {
			NDC.pop(); // Strategy
		}
		return null;
	}
	
	/**
	 * Return the minimum resources needed to run, or null if not applicable
	 * @return
	 */
//	public int[] getTriggeringResources() {
//		return cheapest!=null?cheapest.getNeededResources():null;
//	}
	
	public boolean modifiesResources() {
		return true;
	}

}

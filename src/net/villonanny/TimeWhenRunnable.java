package net.villonanny;

import java.util.Date;

import org.apache.log4j.Logger;


public class TimeWhenRunnable {
	private Logger log = Logger.getLogger(TimeWhenRunnable.class);
	public static TimeWhenRunnable NEVER = new TimeWhenRunnable(false);
	public static TimeWhenRunnable NOW = new TimeWhenRunnable(true);
	private boolean enabled = true;
	private Date timeWhenRunnable = null;
	private boolean sharp = false;
	boolean opportunist=false;

	public TimeWhenRunnable(boolean enabled) {
		this.enabled = enabled;
		if (enabled) {
			timeWhenRunnable = new Date();
		} else {
			timeWhenRunnable = Util.getIncrediblyLongTime();
		}
	}

	/**
	 * 
	 * @param millisecond the milliseconds since EPOCH (i.e. 1970)
	 */
	public TimeWhenRunnable(long millisecond) {
		this(millisecond, false);
	}

	public TimeWhenRunnable(long millisecond, boolean sharp) {
		this( new Date(millisecond), sharp );
		// Log possible bugs
		if (millisecond < System.currentTimeMillis() / 2) {
			log.warn("This might be a bug: TimeWhenRunnable milliseconds too small: " + millisecond);
			// set it to an hour
			this.setTimeWhenRunnable(new Date(System.currentTimeMillis()+Util.MILLI_HOUR));
		}
	}
	
	/**
	 * @param timeWhenRunnable a valid date, null for "now" or Util.getIncrediblyLongTime() to disable
	 */
	public TimeWhenRunnable(Date timeWhenRunnable) {
		this(timeWhenRunnable, false);
	}
	
	public TimeWhenRunnable(Date timeWhenRunnable, boolean sharp) {
		if (timeWhenRunnable == null) {
			timeWhenRunnable = new Date(); // now
		} else if (timeWhenRunnable.equals(Util.getIncrediblyLongTime())) {
			this.enabled = false;
		}
		// report if a long time but not the official getIncrediblyLongTime or NotInTheNearFuture
		// constants are 5,000,000,000 msec less so should test ok even after a period of running
		// if (log.isTraceEnabled()) {
		if (log.isDebugEnabled()) {
			long now = new Date().getTime();
			if (!this.enabled) { 
				log.trace("TWR IncrediblyLongTime " + timeWhenRunnable );
			} else if (timeWhenRunnable.getTime() > Util.NotInTheNearFuture() ) {
				log.warn("TWR IncrediblyLongTime and not Disabled " + timeWhenRunnable );
			} else if (timeWhenRunnable.getTime() > (now + 7884000000L) ) {
				log.trace("TWR NotInTheNearFuture " + timeWhenRunnable );	// more than 3 mths
			} else if (timeWhenRunnable.getTime() > (now + 16*(Util.MILLI_HOUR)) ) {
				log.trace("TWR > 16 hours " + timeWhenRunnable );
			} else if (timeWhenRunnable.getTime() > (now + 8*(Util.MILLI_HOUR)) ) {
				log.trace("TWR > 8 hours " + timeWhenRunnable );
			} else if (timeWhenRunnable.getTime() > (now + 4*(Util.MILLI_HOUR)) ) {
				log.trace("TWR > 4 hours " + timeWhenRunnable );
			}			
		}
		if (sharp) {
			log.trace("TWR sharp "  + timeWhenRunnable);			
		}
		this.timeWhenRunnable = timeWhenRunnable;
		this.sharp = sharp;
	}
	
	/**
	 * Return a new TimeWhenRunnable set to "minutesFromNow" minutes in the future
	 * @param minutesFromNow
	 * @return
	 */
	public static TimeWhenRunnable minutesFromNow(long minutesFromNow) {
		return new TimeWhenRunnable(System.currentTimeMillis() + minutesFromNow*60*1000);
	}
	
	public boolean isOpportunist() {
		return opportunist;
	}

	public void setOpportunist(boolean enabled) {
		this.opportunist = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Date getTimeWhenRunnable() {
		return timeWhenRunnable;
	}

	public void setTimeWhenRunnable(Date timeWhenRunnable) {
		// check valid
		if (timeWhenRunnable.after(Util.getIncrediblyLongTime())) {
			log.error("timeWhenRunnable > NEVER "+timeWhenRunnable);
			timeWhenRunnable = Util.getIncrediblyLongTime();
		}
		this.timeWhenRunnable = timeWhenRunnable;
	}

	public boolean isSharp() {
		return sharp;
	}

	public void setSharp(boolean precision) {
		this.sharp = precision;
	}

	public boolean before(TimeWhenRunnable whenRunnable) {
		if ((timeWhenRunnable != null) && (whenRunnable != null)) {
			return timeWhenRunnable.before(whenRunnable.getTimeWhenRunnable());
		}
		return false;
	}

	public boolean before(Date date) {
		if ((timeWhenRunnable != null) && (date != null)) {
			return timeWhenRunnable.before(date);
		}
		return false;
	}

	public boolean after(TimeWhenRunnable whenRunnable) {
		if ((timeWhenRunnable != null) && (whenRunnable != null)) {
			return timeWhenRunnable.after(whenRunnable.getTimeWhenRunnable());
		}
		return false;
	}

	public boolean after(Date date) {
		if ((timeWhenRunnable != null) && (date != null)) {
			return timeWhenRunnable.after(date);
		}
		return false;
	}

	public long getTime() {
		if (timeWhenRunnable == null) {
			return Util.getIncrediblyLongTime().getTime();
		}
		// log.trace("TWR="+timeWhenRunnable);
		return timeWhenRunnable.getTime();
	}
	
	public Date getDate() {
		if (timeWhenRunnable == null) {
			return Util.getIncrediblyLongTime();
		}
		return timeWhenRunnable;
	}

	public String toString() {
		// extend to report times
		String result = "NEVER";
		if (timeWhenRunnable != null) {
			String tString = timeWhenRunnable.toString();
			if (!this.enabled) {
				// Never disabled correctly
				result += " (" + tString + ")";
			} else if (timeWhenRunnable.getTime() >= Util.getIncrediblyLongTime().getTime()) {
				// Never but not disabled
				result += " <<" + tString + ">>";
			} else if (timeWhenRunnable.getTime() >= Util.NotInTheNearFuture()) {
				result = "Later (" + tString + ")";
			} else {
				// simple time
				result = tString;
			}
		}
		return result;
	}
	
	
	
}

/**
 * 
 */
package net.villonanny.misc;

import org.apache.log4j.Logger;

public enum TravianVersion {
	// Versions must be in sequential order for getPrevious() to work
	V30("net/villonanny/htmlPatterns.v30.properties"),
	V35("net/villonanny/htmlPatterns.v35.properties"),
	// The name "V35B" is not the official name 
	V35B("net/villonanny/htmlPatterns.v35b.properties"),
	V35C("net/villonanny/htmlPatterns.v35c.properties"),
	V36("net/villonanny/htmlPatterns.v36.properties");
	
	private final static Logger log = Logger.getLogger(TravianVersion.class);
	public String filename;
	public final static TravianVersion DEFAULT = V36;
	
	TravianVersion(String file) {
		this.filename = file;
	}
	
	/**
	 * @return the previous version, or null if already at V30
	 */
	public TravianVersion getPrevious() {
		int currentIndex = ordinal(); // Starts at 0
		if (currentIndex > 0) {
			return values()[currentIndex - 1];
		}
		return null;
	}

	// Autodetection of version is unreliable
	
//	/**
//	 * From the login page, tell the server version
//	 * @param htmlPage
//	 * @return
//	 */
//	public static TravianVersion getVersion(String htmlPage) {
//		// Try each version from the last one
//		// for (TravianVersion version : TravianVersion.values()) {
//		TravianVersion[] versions = values();
//		for (int i = versions.length-1; i>=0; i--) {
//			TravianVersion version = versions[i];
//			// We can't use util.getPattern() here because we don't know the version yet
//			Pattern pattern = Pattern.compile(version.patternString);
//			Matcher m = pattern.matcher(htmlPage);
//			if (m.find()) {
//				return version;
//			}
//		}
//		EventLog.log("msg.versionNotDetected", TravianVersion.class);
//		log.debug("Can't detect travian version. Page dump follows:\n" + htmlPage);
//		return DEFAULT;
//	}
	
}
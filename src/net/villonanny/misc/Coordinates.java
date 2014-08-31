package net.villonanny.misc;

import net.villonanny.Util;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.log4j.Logger;

/**
 * This class holds a pair of x, y coordinates.
 * It is also a factory of coordinates.
 * @author xtian
 *
 */
public class Coordinates {
	private static final Logger log = Logger.getLogger(Coordinates.class);
	private String x;
	private String y;
	
	public Coordinates(String x, String y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * 
	 * @param config the subnode configuration of the strategy tag
	 * @param tagName name of the tag that contains the coordinate attributes. E.g. "target"
	 */
	public Coordinates(SubnodeConfiguration config, String tagName) {
		this(config, tagName, "", ""); // Default is "" for compatibility with Farmizzator
	}

	public Coordinates(SubnodeConfiguration config, String tagName, String defaultX, String defaultY) {
        String coordsTravianStyle = config.getString("/" + tagName + "/@coords", null); // e.g. /target/@coords
        if (null != coordsTravianStyle) { // using travian-style configuration, if exists
            String[] coords = coordsTravianStyle.split("[(|)]");
            if (coords.length>2) {
	            x = coords[1];
	            y = coords[2];
            } else {
            	log.error("Invalid @coords: " + coordsTravianStyle);
            	x = defaultX;
            	y = defaultY;
            }
        } else { // if not reverting to old style
            x = config.getString("/" + tagName + "/@x", defaultX);
            y = config.getString("/" + tagName + "/@y", defaultY);
        }
	}
	
	public Coordinates(SubnodeConfiguration config, String defaultX, String defaultY) {
        String coordsTravianStyle = config.getString("/@coords", null); // e.g. /target/@coords
        if (null != coordsTravianStyle) { // using travian-style configuration, if exists
            String[] coords = coordsTravianStyle.split("[(|)]");
            if (coords.length>2) {
	            x = coords[1];
	            y = coords[2];
            } else {
            	log.error("Invalid @coords: " + coordsTravianStyle);
            	x = defaultX;
            	y = defaultY;
            }
        } else { // if not reverting to old style
            x = config.getString("/@x", defaultX);
            y = config.getString("/@y", defaultY);
        }
	}

	public Coordinates(SubnodeConfiguration config, String tagName, int defaultX, int defaultY) {
		this(config, tagName, String.valueOf(defaultX), String.valueOf(defaultY)); 
	}
	

	public String getX() {
		return x;
	}

	public void setX(String x) {
		this.x = x;
	}

	public String getY() {
		return y;
	}

	public void setY(String y) {
		this.y = y;
	}
	
	public int getIntX() {
		return Integer.valueOf(x);
	}
	
	public int getIntY() {
		return Integer.valueOf(y);
	}
	
	public String[] toStringArray() {
		return new String[]{x, y};
	}
	
	public String toString() {
		return "(" + x + "|" + y + ")";
	}
}

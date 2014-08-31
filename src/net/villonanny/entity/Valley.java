package net.villonanny.entity;

import java.awt.Point;
import java.io.*;
import java.util.Date;
import java.util.List;

import net.villonanny.ConfigManager;
import net.villonanny.EventLog;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.misc.TravianVersion;
import net.villonanny.type.ResourceTypeMap;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;

/**
 * Class to store information about a Valley on the map, type, attacks, owner
 * @author GAC
 *
 */
public class Valley {
	// public static final String DEFAULT_FILENAME = "TravianServer.properties";
	public static final String CONFIGDIR = "config";
	public static final String INFOVERSION = "2";
	private	PropertiesConfiguration config = null;
	// private XMLConfiguration xmlConfig;
	// protected ConfigManager configManager;
	// private SubnodeConfiguration config;

	private final static Logger log = Logger.getLogger(Valley.class);
	private Util	util;
	// private Translator translator;
	// private String villageName;			// village strategy
	// private String villageUrlString;
	String configFile = null;
	// private String mapUrlString;
	private String	baseUrl; 			// http://s1.travian3.it/
	private	int 	mapId = -1;				// unique key d=mapId
	// private String	version = INFOVERSION;	// version no - only stored in file on export
	private Date	lastUpdate = null;			// time of last update

	// info
	private String	serverId = null;
	private String	valleyName = null;
    private int		ownerId = -1;		// uid of attacker or trade sender
	private String	ownerName = null;
    private String	ownerRole = null;		// role if known ally/nap/confed/enemy=attacker/defender/sender 

	// private String valleyType = null;
	// private String valleyUrlString = null;
	private String alliance = null;
	private int		population = -1;
	// private String xPos = null;
	// private String yPos = null;
	private Point 	position = null;
	// private String	coordString = null;
	
	private Integer resTypeNo = -1;			// GAC added for V35 map information
	private Integer valleyTypeNo = -1;
	// private Integer[] resCount = new [4];		// GAC TODO - find a constant for no of resource types
	// need Restypemap
	private	Integer totalBounty = 0;			// initialise to 0 as add to when set
	private Integer	ogAttacks = 0;				// initialise to 0 as increment when set
	private Integer	icAttacks = 0;				// initialise to 0 as increment when set
	private	Integer totalLosses = 0;			// initialise to 0 as add to when set
	private	Integer maxTroops = -1;				// max troops ever seen in valley

	
	
	// GAC - Including header information means that ordinal is also oasis number
	private	String[][]	Oases =	{ 
			{"Type", "Image", "Bonus"},
			{"Lumber oasis", "o1.gif", "+25% lumber per hour"}, 
			{"Lumber oasis", "o2.gif", "+25% lumber per hour"},
			{"Lumber and crop oasis", "o3.gif", "+25% lumber per hour, +25% crop per hour"},
			{"Clay oasis", "o4.gif", "+25% clay per hour"},
			{"Clay oasis", "o5.gif", "+25% clay per hour"},
			{"Clay and crop oasis", "o6.gif", "+25% clay per hour, +25% crop per hour"},
			{"Iron oasis", "o7.gif", "+25% iron per hour"},
			{"Iron oasis", "o8.gif", "+25% iron per hour"},
			{"Iron and crop oasis", "o9.gif", "+25% iron per hour, +25% crop per hour"},
			{"Crop oasis", "o10.gif", "+25% crop per hour"},
			{"Crop oasis", "o11.gif", "+25% crop per hour"},
			{"50% Crop oasis", "o12.gif", "+50% crop per hour"},
			{"", "", ""}
			};
	
	public String nameFromImage(String image) {
		// find oasis name based on gif
		int i = 1;
		while (Oases[i][0] != "") {
			// System.out.printf("%d (%s) (%s)\n", i, image, Oases[i][1]);
			if (image.equals(Oases[i][1])) {
				return Oases[i][0];
			}
			i++;
		}
		return "Village" ;
	}

	// resource counts for field types in Travian 3.5 ToolTip
	// make into a restype map, or extend to include name?
	private Integer[][] resCounts = {
			{0, 0, 0, 0},
			{3,3,3,9},
			{3,4,5,6},
			{4,4,4,6},
			{4,5,3,6},
			{5,3,4,6},
			{1,1,1,15},
			{4,4,3,7},
			{3,4,4,7},
			{4,3,4,7},
			{3,5,4,6},
			{4,3,5,6},
			{5,4,3,6}
	} ;
	
	public	int	typeFromRes(ResourceTypeMap res) {
		for (int i = 0 ; i < resCounts.length ; i++ ){
			if ((resCounts[i][0] == res.getWood()) &&
				(resCounts[i][1] == res.getClay()) &&
				(resCounts[i][2] == res.getIron()) &&
				(resCounts[i][3] == res.getCrop()) ) {
				return i;
			}
		}
		// not found
		return -1;
	}
	
	/*
	public Valley(Util util, String name, String urlString, Translator translator) {
		// super(name, urlString, translator);
		this.util = util;
		this.villageName = name;
		this.villageUrlString = urlString;
		this.translator = translator;
	} */
	
	/**
	 * create Valley and load any previous contents from config file 
	 * @param util
	 * @param xpos, ypos
	 */
	public Valley(Util util, int value) {
		init(util, value);
	}
	/**
	 * create Valley and load any previous contents from config file 
	 * @param util
	 * @param xpos, ypos
	 */
	public Valley(Util util, int x, int y) {
		// construct unique id from position
		init(util, Util.getMapIdFromPosition(x, y));
	}
	public Valley(Util util, String x, String y) {
		init(util, Util.getMapIdFromPosition(x, y));		
	}
	/**
	 * create empty valley - set contents or use fetch to load from config store
	 * @param util
	 */
	public Valley(Util util) {
		this.util = util;
	}
	
	/**
	 * initialisation for Valley
	 * @param util
	 * @param mapid of valley
	 */
	private	void init(Util util, int value) {
		this.util = util;
		baseUrl = util.getBaseUrl();
		log.trace("baseUrl:"+baseUrl);
		// int	endFirst = baseUrl.indexOf('.', 1);
		// int	endFirst = baseUrl.indexOf('.');
		int startLast = baseUrl.lastIndexOf('.');
		int endLast = baseUrl.indexOf('/', startLast);
		// serverId = baseUrl.substring(baseUrl.indexOf('/', 1)+2, baseUrl.indexOf('.', 1));
		serverId = baseUrl.substring(baseUrl.indexOf('/')+2, baseUrl.indexOf('.', 1)) +
					baseUrl.substring(startLast, endLast);
		
		// not unique need to add the last part uk or com
		// substring(0, baseUrl.indexOf("/", "http://".length()) + 1);

		mapId = value;
		// construct position from id
		position = new Point(-1,-1);
		// position = util.getPositionFromMapId(Integer.valueOf(value));
		position = util.getPositionFromMapId(value);
		String coordString = "("+position.x+"|"+position.y+")";
		// xPos = Integer.toString(x);
		// yPos = Integer.toString(y);
		// configFile = CONFIGDIR + File.separatorChar + serverId + ".properties";
		configFile = CONFIGDIR + File.separatorChar + serverId + ".csv";
		// try and open file if not already open
		config = util.getServer().getValleyConfig();
		if (config== null) {
			try {
				config = new PropertiesConfiguration(configFile);
				EventLog.log("Opening Config for Server "+serverId+" Valley:"+coordString);
				// restore any existing info
				load();
				// config.setProperty("000001","verNo,x,y,Name,Owner,Alliance,Population,resTypeNo,valleyTypeNo,totalAttacks,totalBounty,mapId");				
			} catch (ConfigurationException e) {
				// String filename = DEFAULT_FILENAME;
				// create new config file
				config = new PropertiesConfiguration();
				config.setHeader("Valley information for "+serverId+" created "+new Date());
				config.setFileName(configFile);
				// set a version string so can handle any change of format
				config.addProperty("version", INFOVERSION);
				// save the header and basic info
				// config.addProperty("000001","verNo,x,y,Name,Owner,Alliance,Population,resTypeNo,valleyTypeNo,totalAttacks,totalBounty,mapId");				
				config.addProperty("000002","verNo,Date,x,y,Name,Owner,Alliance,Pop,Id,resType,valleyType,role,troops,ogAttacks,Bounty,icAttacks,Losses,mapId");				
				this.save();
				EventLog.log("Created New Config for Server "+serverId+" Valley:"+coordString);
			}
			util.getServer().setValleyConfig(config);			
		} else {
			// restore any existing info
			load();			
		}
		// System.exit(0);
	}
	/**
	 * try and load village from config
	 * @return
	 */
	public boolean load() {
		boolean result = false;
		log.trace("Valley:"+this.toString());
		// check for this valley
		String[] vInfo = config.getStringArray(Integer.toString(mapId));
		// check if found
		if (vInfo.length > 0) {
			try {
				int	version = Integer.parseInt(vInfo[0]);
				// restore info - support versions if required
				// version in 0 to support mix per valley and simpler export to csv
				if (version == 1) {
					// x,y = vInfo[1,2];
					valleyName = vInfo[3];
					ownerName = vInfo[4];
					alliance = vInfo[5];
					population = Integer.parseInt(vInfo[6]);
					resTypeNo = Integer.parseInt(vInfo[7]);
					valleyTypeNo = Integer.parseInt(vInfo[8]);
					icAttacks = Integer.parseInt(vInfo[9]);
					totalBounty = Integer.parseInt(vInfo[10]);
					// make last param something can check
					if (mapId == Integer.parseInt(vInfo[11])) {
						log.trace(configFile+" loaded v"+version+":"+this.toString());				
					} else {
						log.error(configFile+" invalid parameters v"+version+":"+this.toString());				
					}					
				} else {
					// ver = 0, time = 1, x,y = vInfo[2,3];
					valleyName = vInfo[4];
					ownerName = vInfo[5];
					alliance = vInfo[6];
					population = Integer.parseInt(vInfo[7]);				
					ownerId = Integer.parseInt(vInfo[8]);
					resTypeNo = Integer.parseInt(vInfo[9]);
					valleyTypeNo = Integer.parseInt(vInfo[10]);
					ownerRole = vInfo[11];
					maxTroops = Integer.parseInt(vInfo[12]);
					ogAttacks = Integer.parseInt(vInfo[13]);
					totalBounty = Integer.parseInt(vInfo[14]);
					icAttacks = Integer.parseInt(vInfo[15]);
					totalLosses = Integer.parseInt(vInfo[16]);
					// make last param something can check
					if (mapId == Integer.parseInt(vInfo[17])) {
						log.trace(configFile+" loaded v"+version+":"+this.toString());				
					} else {
						log.error(configFile+" invalid parameters v"+version+":"+this.toString());				
					}					
				}
			} catch (NullPointerException e) {
				throw e; // Will catch below				
			} catch (NumberFormatException e){
				// throw e; // Will catch below
				// just report
				log.error(configFile+" invalid parameter "+this.toString());				
			} catch (Exception e) {
				log.error(configFile+" error in parameters "+this.toString());				
				// e.printStackTrace();
			}
			result = true;
		} // endif vInfo
		// read troopcounts
		vInfo = config.getStringArray(Integer.toString(mapId)+"troops");
		if (vInfo.length > 0) {
			
		}
		return result;
	}
	public String	toString() {
		/* String	vInfo = valleyName+","+position.x+","+position.y+","+valleyType+","+ownerName+","+alliance+","+population+","+
		resTypeNo+","+valleyTypeNo+","+noAttacks+","+totalBounty+","+mapId; */
		// extract information from resource type field
		String vn = "";
		if (!util.isTravianVersionBelow(TravianVersion.V35)) {
			if (resTypeNo == 0) {
				// an oasis
				vn = Oases[valleyTypeNo][0];
			} else {
				vn = valleyName == null ? "" : valleyName.replace(',','_');				
			}
		} else {
			vn = valleyName == null ? "" : valleyName.replace(',','_');			
		}
		String on = ownerName == null ? "" : ownerName.replace(',','_');
		String an = alliance == null ? "" : alliance.replace(',','_');
		String role = ownerRole == null ? "" : ownerRole;
		String vInfo = String.format("%d,%d,%s,%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d",
				position.x,position.y,vn,on,an,population,ownerId,resTypeNo,valleyTypeNo,
				role,maxTroops,ogAttacks,totalBounty,icAttacks,totalLosses,
				mapId);
		return vInfo;
	}
	
	public boolean save() {
		// update config version of valley and save file
		// EventLog.log("Updating "+configFile);
		// config.setProperty(Integer.toString(this.mapId), INFOVERSION+","+this.toString());
		store();
		try {
			config.save();
			log.trace(configFile+" saved "+this.toString());
			// EventLog.log("Saved "+configFile);
		} catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	public Boolean	store() {
		// update config version of valley
		// config.setProperty("version", INFOVERSION);
		config.setProperty(Integer.toString(this.mapId), INFOVERSION+","+new Date()+","+this.toString());
		log.trace("updated "+this.toString());
		return true;
	}
	
	/*
	public String getUrlString() {
		// return Url for this valley
		return mapUrlString;
	} */

	// return name  - used in error message
	public int getId() {
		return mapId;
	}
	// return name  - used in error message
	public String getName() {
		return valleyName;
	}
	public void setName(String value) {
		// EventLog.log("ValleyName:"+value);
		valleyName = value;
	}
	public void setOwner(int id, String name) {
		// EventLog.log("PlayerName:"+value);
		if (id > 0) {
			ownerId = id;			
		}
		ownerName = name;
	}
	public	int	getOwnerId() {
		return ownerId;
	}
	public String getOwnerName() {
		return ownerName;
	}
	public void setOwnerRole(String value) {
		// EventLog.log("PlayerName:"+value);
		ownerRole = value;
	}
	public String getOwnerRole() {
		return ownerRole;
	}
	public void setAlliance(String value) {
		alliance = value;
	}
	public void setPopulation(int value) {
		population = value;
	}
	public void setResType(int value) {
		resTypeNo = value;
	}
	public void setValleyType(int value) {
		valleyTypeNo = value;
	}
	/**
	 * store last bounty and increment attack count
	 * @param value
	 */
	public void	addBounty(int value) {
		// EventLog.log("mapId "+mapId+" @ "+coordString+"="+value);
		totalBounty += value;
		ogAttacks++;
	}
	public void	addLoss(int value) {
		// EventLog.log("mapId "+mapId+" @ "+coordString+"="+value);
		totalLosses += value;
		icAttacks++;
	}
    public int		getAverageBounty() {
    	if (icAttacks > 0) {
        	return (totalBounty/icAttacks);    		
    	} else {
    		return -1;
    	}
    }
    public int		getTotalBounty() {
        return totalBounty;    		
    }
    public int		getTotalAttacks() {
        return icAttacks;    		
    }
    public void		setTroops(int value) {
		if (value > maxTroops) {
			maxTroops = value;			
		}    	
    }

	


	
	/**
	 * find and retrieve information for this valley
	 * @return
	 */
	public Valley	fetch() {
		// just reload this
		load();
		return this; 
	}
	/**
	 * find and retrieve information for this valley
	 * @param position
	 * @return
	 */
	public Valley	fetch(Point value) {
		position = value;
		mapId = Util.getMapIdFromPosition(position.x,position.y);
		// coordString = "("+position.x+"|"+position.y+")";
		// xPos = Integer.toString(x);
		// yPos = Integer.toString(y);
		// now reload this
		load();
		return this; 
	}
	
	public Valley	fetch(String value) {
        String[] coords = value.split("[(|)]");
        if (coords.length>2) {
            position.x = Integer.parseInt(coords[1]);
            position.y = Integer.parseInt(coords[2]);
        }
		mapId = Util.getMapIdFromPosition(position.x,position.y);
		// coordString = value;
		// xPos = Integer.toString(x);
		// yPos = Integer.toString(y);
		// now reload this
		load();
		return this; 
	}
	
	public Valley	fetch(String x, String y) {
		fetch(Integer.parseInt(x), Integer.parseInt(y));
		return this; 
	}
	
	public Valley	fetch(int x, int y) {
		position = new Point(x,y);
		mapId = Util.getMapIdFromPosition(x, y);
		// coordString = "("+x+"|"+y+")";
		// now reload this
		load();
		return this; 
	}
	
	// public	boolean	update(String serverId, int x, int y) {
	public	boolean	update() {
		save();
		/*
		String filename = "u"+DEFAULT_FILENAME;
		xmlConfigFilename = filename;
		XMLConfiguration	xmlConfig;
		EventLog.log("about to load "+xmlConfigFilename);
		try {
			xmlConfig = new XMLConfiguration(xmlConfigFilename);
			// xmlPart.load(xmlConfigFilename);
		} catch (ConfigurationException e1) {
			// ignore if file did not exist
			// e1.printStackTrace();
			// return false;
			xmlConfig= new XMLConfiguration();
		}
		// set filename in xml
		xmlConfig.setFileName(xmlConfigFilename);
		String	s = " "+serverId+"/d"+mapId;
		xmlConfig.addProperty(" server@enabled", "true");
		
		// xmlConfig.addProperty("d"+mapId+" name", valleyName);
		// xmlConfig.addProperty("d"+mapId+" coords", coordString);
		// xmlConfig.addProperty("d"+mapId+" position/x", position.x);
		// xmlConfig.addProperty("d"+mapId+" position/y", position.y);
		// xmlConfig.setProperty(s+"/name", valleyName);
		// xmlConfig.setProperty(" "+serverId+"/d"+mapId+"/position/x", position.x);
		// xmlConfig.setProperty(" "+serverId+"/d"+mapId+"/position/y", position.y);
		xmlConfig.setProperty(" "+serverId+"/d"+mapId+"/coords", coordString);
		// Save configuration
		EventLog.log("about to save "+xmlConfigFilename);
		try {
			xmlConfig.save();
			EventLog.log("Valley Info Updated", ConfigManager.class, xmlConfig.getFileName());
		} catch (Exception e) {
			log.error("Failed to save configuration (ignoring)", e);
			return false;
		}
		/*
		String configurationSelector = serverId;
		List<SubnodeConfiguration> configNodes = config.configurationsAt(configurationSelector);
		String newUid = "test";
		for (SubnodeConfiguration configNode : configNodes) {
			String currentUid = configNode.getString("/@uid", null);

			configNode.setProperty(" @uid", newUid );
			// configNode.append("test2");
		}
		// modified = true;
		*/
		return true;
	}
	
	public	boolean	saveXML() {
		String configFile = serverId + ".properties";
		try {
			config = new PropertiesConfiguration(configFile);
			
		} catch (ConfigurationException e2) {
			// TODO Auto-generated catch block
			// e2.printStackTrace();
			// create new config
			config = new PropertiesConfiguration();
			config.setHeader("This is the header");
			config.setFileName(configFile);
					
			try {
				config.save();
			} catch (ConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		config.addProperty("add1", "value1");
		config.setProperty("set1", configFile);
		config.setProperty("int", -1);
		config.setProperty("boolean", true);
		String s = this.toString();
		config.setProperty(Integer.toString(this.mapId), s);
		
		EventLog.log("Saving "+configFile);
		try {
			config.save();
		} catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		EventLog.log("Saved "+configFile);
		
		/*
		String filename = "s"+DEFAULT_FILENAME;
		this.xmlConfigFilename = filename;
		// NodeCombiner combiner = new UnionCombiner();
		// CombinedConfiguration tmpConfig = new CombinedConfiguration(combiner);
		// tmpConfig.setExpressionEngine(new XPathExpressionEngine());
		// tmpConfig.setThrowExceptionOnMissing(true); // Throw NoSuchElementException when config element is missing
		// tmpConfig.setForceReloadCheck(true);
		xmlPart = new XMLConfiguration();
		// xmlPart.setEncoding(Util.getEncodingString());
		xmlPart.setExpressionEngine(new XPathExpressionEngine());
		// xmlPart.setThrowExceptionOnMissing(true); // Throw NoSuchElementException when config element is missing
		// tmpConfig.addConfiguration(xmlPart);

		try {
			xmlPart.load(xmlConfigFilename);
		} catch (ConfigurationException e1) {
			// ignore if file did not exist
			// e1.printStackTrace();
		}
		// set filename in xml
		xmlPart.setFileName(xmlConfigFilename);

		// File originalFile = xmlPart.getFile();

		// tmpConfig.addProperty(" @valley", this);
		xmlPart.addProperty("tables.table(-1).fields.field.name", "1");
		xmlPart.addProperty("tables.table(-1).fields.field.name", "2");
		// xmlPart.setProperty("tables.table.fields.field.name", "1");
		// xmlPart.setProperty("tables.table.fields.field.name", "3");
		// xmlPart.setProperty(" "+serverId+"/d"+mapId+" name", valleyName);
		// xmlPart.setProperty(serverId+" d"+mapId+" name", valleyName);
		// this.config = newConfigManager.getXmlConfiguration();
		// errors this.config = xmlPart.configurationAt(serverId);	 // .getXmlConfiguration();
		// this.config = xmlPart.getRootNode();		
		// xmlPart.configurationAt(key, supportUpdates);
		// config.addProperty(" proxy@enabled", "true");
		// config.addProperty("proxy hostName", "host2");
		// config.addProperty("proxy hostPort", "port2");
		// config.setProperty(" proxy@enabled", "false");
		// config.addProperty("d"+mapId+" name", valleyName);
		// config.addProperty("d"+mapId+" coords", coordString);
		// config.addProperty("d"+mapId+" position/x", position.x);
		// config.addProperty("d"+mapId+" position/y", position.y);

		// xmlPart.getString(" "+serverId+"/d"+mapId+"/name");
		// xmlPart.setProperty(" "+serverId+"/d"+mapId+"/position/x", position.x);
		// xmlPart.setProperty(" "+serverId+"/d"+mapId+"/position/y", position.y);
		// xmlPart.setProperty(" "+serverId+"/d"+mapId+"/coords", coordString);
		// xmlPart.setProperty(serverId+" d"+mapId+" coords", coordString);
		
		// xmlPart.setProperty(" "+serverId+"/d"+mapId+"/field2", "3");
		// xmlPart.addProperty(" @1", "2");
		// xmlPart.addProperty(" @3", "4");
		// xmlPart.setProperty(" @1", "5");
		// xmlPart.setProperty(" @6/7", "8");
		/*
		ValleyHelper temp = new ValleyHelper();
		try {
			temp.write(this, filename);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} */
		// Save configuration
/*		try {
			xmlPart.save();
			EventLog.log("evt.configSaved", ConfigManager.class, xmlPart.getFileName());
		} catch (Exception e) {
			log.error("Failed to save configuration (ignoring)", e);
		}
		// System.exit(0);
		 * 
		 */
		return false;
	}

	/* try serialisers?
	public class ValleyHelper {
	    public void write(Valley v, String filename) throws Exception{
	        /* XMLEncoder encoder =
	           new XMLEncoder(
	              new BufferedOutputStream(
	                new FileOutputStream(filename)));
	        encoder.writeObject(v);
	        encoder.close();
	        */
	/*
	    	EventLog.log("write "+v.valleyName+" "+v.position.x+","+v.position.y);
	    	// convert to a string
	    	ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(  );
	    	java.beans.XMLEncoder e = new java.beans.XMLEncoder( out  );
	    	e.writeObject( v );	
	    	e.close();
	    	log.info(out.toString());
	    	System.out.println( out.toString() );
	    }

	    public Valley read(String filename) throws Exception {
	        XMLDecoder decoder =
	            new XMLDecoder(new BufferedInputStream(
	                new FileInputStream(filename)));
	        Valley o = (Valley)decoder.readObject();
	        decoder.close();
	        return o;
	    }
	}
	*/
}

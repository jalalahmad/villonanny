package net.villonanny.entity;


/*
 * GAC	Create Map entity - not sure if there was a valid type to extend, building or village did not seem appropriate
 * probably Valley should be own Class with EmptyValley, Oasis etc
 * possibly even above Village which is a type of occupied Valley
 * Oasis is probably equivalent to resource field so can hold jpg ids which seem to be only way to identify
 * At the moment cheat by using direct arrays and type as number of crop fields
 */



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.Translator;
import net.villonanny.Util;
import net.villonanny.misc.TravianVersion;

import org.apache.log4j.Logger;

public class SevenBySeven {
	private final static Logger log = Logger.getLogger(SevenBySeven.class);
	private Util util;
	private Translator translator;
	private String villageName;			// village strategy
	private String villageUrlString;
	private String mapUrlString;
//	private String baseUrl; 			// http://s1.travian3.it/
	private PrintWriter out;
	// private int	type = 0;
	// private String valleyUrlString;		// valley strategy
	// should this be its own class per valley - not sure how will be used
	private String[] valleyName = new String[50];		// should be 49 in 7x7
	private String[] valleyType = new String[50];
	private String[] valleyUrlString = new String[50];
	private String[] player = new String[50];
	private String[] population = new String[50];
	private String[] alliance = new String[50];
	private String[] xPos = new String[50];
	private String[] yPos = new String[50];

	private String[] gif = new String[50];
	private String[] dRef = new String[50];
	private String[] cRef = new String[50];
	private String[] id = new String[50];
	private Integer[] resTypeNo = new Integer[50];			// GAC added for V35 map information
	private Integer[] valleyTypeNo = new Integer[50];
	private Integer[] cropCount = new Integer[50];
	private Integer[][] resCount = new Integer[50][4];		// GAC TODO - find a constant for no of resource types
	private Double[] distance = new Double[50];
	
	// private Valley[] valley = new Valley[50];
	private Valley[] valley = new Valley[50];
	
/*	based on javascript
 	private Integer[] valleyResources(Integer valleyType){
 		switch(valleyType) {
		case 1: return new Integer[] {3,3,3,9};
		case 2:	return new Integer[] {3,4,5,6};
		case 3: return new Integer[] {4,4,4,6};
		case 4: return new Integer[] {4,5,3,6};
		case 5: return new Integer[] {5,3,4,6};
		case 6: return new Integer[] {1,1,1,15};
		case 7:return new Integer[] {4,4,3,7};
		case 8:return new Integer[] {3,4,4,7};
		case 9:return new Integer[] {4,3,4,7};
		case 10:return new Integer[] {3,5,4,6};
		case 11:return new Integer[] {4,3,5,6};
		case 12:return new Integer[] {5,4,3,6};
		default:return new Integer[] {0,0,0,0};
		}
	};
*/
	
	/*
	 *	Oases - used to identify from Gif
	 *	TODO should be a Type, but need to decide on valley class  
	 *		Need to add to language files to support
	 *   
	 *  	Appearance 1 	Appearance 2 	Increase
Type 1 	Lumber oasis 	Lumber oasis 	+25% lumber per hour
Type 2 	Lumber and crop oasis 	- 	+25% lumber per hour,
+25% crop per hour
Type 1 	Clay oasis 	Clay oasis 	+25% clay per hour
Type 2 	Clay and crop oasis 	- 	+25% clay per hour,
+25% crop per hour
Type 1 	Iron oasis 	Iron oasis 	+25% iron per hour
Type 2 	Iron and crop oasis 	- 	+25% iron per hour,
+25% crop per hour
Type 1 	Crop oasis 	- 	+50% crop per hour
Type 2 	Crop oasis 	Crop oasis 	+25% crop per hour
	 */
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
	
	private String OasisLookup (String image) {
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
	
	

	public enum OutputType {
		// output file types, default is text
		CSV("csv"),
		XML("xml"),
		TXT("txt")
	    ;

		
		private String fileExt;
		
		OutputType (String fileExt) {
			// use this to set the class value from parameter
			this.fileExt = fileExt;
		}
		
		public String getExtension() {
			return fileExt;
		}
		
		public String toString() {
			return fileExt;			
		}
		
		private final static Map<String, OutputType> fromStringMap;
		static {
			fromStringMap = new HashMap<String, OutputType>();
			OutputType[] values = OutputType.values();
			for (int i = 0; i < values.length; i++) {
				OutputType m = values[i];
				fromStringMap.put(m.fileExt.toLowerCase(), m);
			}
		}
		
		public static OutputType fromKey(String key) {
			if (key==null) {
				return null;
			}
			String lowKey = key.toLowerCase();
			OutputType result = fromStringMap.get(lowKey);
			if (result == null) {
				EventLog.log("Unrecognised File Type - " + key);
			}
			return result;
		}	
	}
	private OutputType outputType = OutputType.TXT;

	public SevenBySeven(Util util, String name, String urlString, Translator translator) {
		// super(name, urlString, translator);
		this.util = util;
		this.villageName = name;
		this.villageUrlString = urlString;
		this.translator = translator;
	}

	public void init() {
		EventLog.log("GAC Valley.init() called");
		log.info("GAC Valley.init() called");
		/*
		for (Integer i = 0 ; i < 50 ; i++) {
			dRef[i] = "";
		}
		*/
	}
	
	public String getUrlString() {
		// return Url for the centre of this 7x7
		return mapUrlString;
	}

	public void setOutputType(OutputType type) {
		// default is to append to outputfile every time strategy run
		setOutputType(type, true);
	}
	
	public void setOutputType(OutputType type, Boolean append) {
		outputType = type;
		// setting the output type forces creation of the file if need to generate header
		try {
			openOutput(append);
			switch(outputType)
			{
			case CSV:
				if (!append) {
					// save as csv file for use in a spreadsheet
					out.println("Time,Url,x,y,distance,rType,vType,Valley Type,Village Name,Player,Alliance,Pop,Wood,Clay,Iron,Crop,Comments");					
				}
				break;
			default:
				// no header
				break;
			}
			closeOutput();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void openOutput() throws FileNotFoundException {
		// default to appending to file - this method is also used for each block
		openOutput(true);
	}
	
	private void openOutput(Boolean append) throws FileNotFoundException {
		out = new PrintWriter(new FileOutputStream(new File("logs" + File.separator + "CropFinder." + outputType.fileExt), append));
	}
	
	private void closeOutput() {
		// close any output file
		out.close();
	}
	
	private void outputValley(Integer vNo) {
		// output valley information to file
		switch(outputType)
		{
		case CSV:
			// out.println("Time,Url,x,y,distance,Type,Village Name,Player,Alliance,Pop,Wood,Clay,Iron,Crop,Comments");
			// output time code or real date?
			// (new Date().getTime()),
			out.print(String.format("%s,%s,%s,%s,%f,%d,%d,%s,\"%s\",\"%s\",\"%s\",%s,%d,%d,%d,%d",
					(new Date()),
					valleyUrlString[vNo], xPos[vNo], yPos[vNo], distance[vNo], resTypeNo[vNo], valleyTypeNo[vNo], valleyType[vNo],
					valleyName[vNo], player[vNo], alliance[vNo], population[vNo],
					resCount[vNo][0], resCount[vNo][1], resCount[vNo][2], resCount[vNo][3] ));
			// make Cropper easily visible in file if looking in text editor
			if (resCount[vNo][3] == 15) {
				out.println(",+++++++++++++++CROPPER+++++++++++++++");
			} else if (resCount[vNo][3] == 9) {
				out.println(",+++++++++CROPPER+++++++++");
			} else if (resCount[vNo][3] > 6) {
				out.println(",Cropper");		// travian 3.5 7 cropper
			} else {
				out.println(",");
			}
			break;
		case XML:		// write villages in format that can be used in farmrotator
			if (valleyType[vNo] == "Village") {
				out.println(String.format("<target movement=\"RAID\" x=\"%s\" y=\"%s\" desc=\"%s Pop:%s\" rate=\"1\"/>",
						xPos[vNo], yPos[vNo], valleyName[vNo], population[vNo] ));
				
			}
			break;
			
		case TXT:		// default
		default:
			// Include date
			out.print(new Date());
			out.print( String.format(" %s distance %f %s ", id[vNo], distance[vNo], valleyType[vNo]) );
			// check for owner
			// if (valleyName[vNo] != "" ) {
			if ((valleyName[vNo] != "") || (player[vNo] != "" )) {
				out.print(String.format("\"%s\" player \"%s\" alliance \"%s\" pop %s",
					valleyName[vNo], player[vNo], alliance[vNo], population[vNo] ));
			}
			// check if any resource information
			if (resTypeNo[vNo] != 0) {
				out.print( String.format(" Fields(%d,%d,%d,%d)",
						resCount[vNo][0], resCount[vNo][1], resCount[vNo][2], resCount[vNo][3]) );
				// make Cropper easily visible in file if looking in text editor
				if (resCount[vNo][3] == 15) {
					out.print(" +++++++++++++++CROPPER+++++++++++++++");
				} else if (resCount[vNo][3] == 9) {
					out.print(" +++++++++CROPPER+++++++++");
				} else if (resCount[vNo][3] > 6) {
					out.print(" Cropper");		// travian 3.5 7 cropper
				} else {
					// nothing to add
				}
			}
			out.println("");
			// add target format for MultiFarmRotator
			if (valleyName[vNo] != "" ) {
				out.println(String.format("<target movement=\"RAID\" x=\"%s\" y=\"%s\" desc=\"%s Pop:%s Dist:%s\" rate=\"1\"/>",
						xPos[vNo], yPos[vNo], valleyName[vNo], population[vNo], distance[vNo] ));				
			}
			break;
		}
		// output to file
	}
	
	/**
	 * Move the map to x,y coordinates and return the page
	 * @param centreX
	 * @param centreY
	 * @return
	 * @throws ConversationException
	 */
	public String moveTo(String centreX, String centreY) throws ConversationException {
//		// store base Url as not calling login
//		baseUrl = this.villageUrlString.substring(0, this.villageUrlString.indexOf("/", "http://".length()) + 1);
		// store centre of the map to go back to if needed
		mapUrlString = Util.getFullUrl(util.getBaseUrl(), "karte.php");
		// EventLog.log("GAC MapURL " + mapUrlString );
		String page = util.httpGetPage(mapUrlString, false);		// assume a real person would look at page for a while
		// now need to call Post to move to Specified Coordinates
		List<String> postNames = new ArrayList<String>();
		List<String> postValues = new ArrayList<String>();
		
		// Util.addHiddenPostFields(page, "<form method=\"POST\" name=\"snd\" action=\"build.php\">", postNames, postValues);
		// Util.addButtonCoordinates("s1", 80, 20, postNames, postValues);
		
		// Map Form does not appear to have hidden fields
		// post it and get the page back
		postNames.add("xp");
		postValues.add(centreX);
		postNames.add("yp");
		postValues.add(centreY);
		Util.addButtonCoordinates("s1", 0, 0, postNames, postValues);
		page = util.httpPostPage(mapUrlString, postNames, postValues, false);
		return page;
	}

	public void search(Util util, Integer centreX, Integer centreY, Integer fromX, Integer fromY) throws ConversationException {
		// super.fetch(util);
		Pattern p;
		Matcher m;
		log.debug("GAC Valley.fetch() called");
		
		// 7x7 created from home map of current Village
		// EventLog.log("GAC Village " + this.villageName " URL " + this.villageUrlString );
		String page = moveTo(Integer.toString(centreX), Integer.toString(centreY));
		
		// titles are complex, may be blank contain spaces or some special characters so need to quote on use
		p = util.getPattern("sevenBySeven.main");
		m = p.matcher(page);
		int vCount = 0;
		while (m.find()) {
			/* GAC debug trace for pattern matching - keep a copy somewhere
			log.debug(String.format("GAC match %d \"%s\"\n", m.groupCount(), p) );
            for (Integer i = 0 ; i++ < m.groupCount() ; ) {
                log.debug("GAC m.group(" + i + "=" + m.group(i) +  ")");
            } 
			EventLog.log(String.format("GAC %d match \"%s\"\n", m.groupCount(), p));
            for (Integer i = 0 ; i++ < m.groupCount() ; ) {
                EventLog.log("GAC m.group(" + i + "=" + m.group(i) +  ")");
            } 
            */
			if (m.groupCount() > 6) {
				// pre increment - so if count of 1 will be in element 1
				vCount++;
				valley[vCount] = new Valley(util, m.group(1), m.group(2));
				xPos[vCount] = m.group(1);
				yPos[vCount] = m.group(2);
				// synthesise id from x,y info
				id[vCount] = "("+xPos[vCount]+"|"+yPos[vCount]+")";
				// clear the counters and default the type
				cropCount[vCount] = 0;
				for (Integer i = 0 ; i < 4 ; i++ ) {
					resCount[vCount][i] = 0;
				}
				valleyType[vCount] = "Unoccupied";		// "Abandoned valley";								
				// Travian 3.5 included a major restructure to the map
				if (!util.isTravianVersionBelow(TravianVersion.V35)) {
					valley[vCount].setResType(Integer.parseInt(m.group(3)));
					valley[vCount].setValleyType(Integer.parseInt(m.group(4)));
					// store new V35 resource type and valley type from map in fields to start with
					resTypeNo[vCount] = Integer.parseInt(m.group(3));
					valleyTypeNo[vCount] = Integer.parseInt(m.group(4));
					// reference strings
					dRef[vCount] = m.group(5);
					cRef[vCount] = m.group(6);
					// next in gif string
					gif[vCount] = m.group(7);
					
					// String dInfo = "";
		            // for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
		            // EventLog.log("sevenBySeven.main:"+m.groupCount()+":"+dInfo);
		            
		            // check for optional fields
		            if ( m.group(8) != null) {
		            // if ((m.groupCount() > 10) && (m.group(8) != null)) {
						valleyName[vCount] = m.group(8);
						player[vCount] = m.group(9);
						alliance[vCount] = m.group(11);
		            	// set permanent store
						valley[vCount].setName(m.group(8));
						valley[vCount].setAlliance(m.group(11));
						valley[vCount].setOwner(0,m.group(9));
						if (!m.group(10).equals("-")) {
							valley[vCount].setPopulation(Integer.parseInt(m.group(10)));
							population[vCount] = m.group(10);		            	
							valleyType[vCount] = "Village";								
						} else {
							valleyType[vCount] = "Occupied Oasis";								
							population[vCount] = "";		            	
						}
			            log.trace("sevenBySeven.main optional ("+xPos[vCount]+"|"+yPos[vCount]+") "+m.groupCount()+ 
			            		String.format(" \"%s\" player \"%s\" alliance \"%s\" pop %s",
			            				valleyName[vCount], player[vCount], alliance[vCount], population[vCount] ));
		            } else {
						// use type information to set rest of valley info below default for now 
						valleyName[vCount] = "";
						player[vCount] = "";
						alliance[vCount] = "";
						population[vCount] = "";		            	
		            }
		            
				} else {
					valley[vCount].setName(m.group(10));
					valley[vCount].setAlliance(m.group(7).replace('"', ' ').trim());
					valley[vCount].setOwner(0,m.group(6).replace('"', ' ').trim());
					valley[vCount].setPopulation(Integer.parseInt(m.group(5).replace('"', ' ').trim()));
					// dont set type fields if not known
					// valley[vCount].setResType(0);
					// valley[vCount].setValleyType(0);
					valleyName[vCount] = m.group(10);
					dRef[vCount] = m.group(8);
					cRef[vCount] = m.group(9);
					// gif[vCount] = m.group(3);				// img\/un\/m\/
					gif[vCount] = m.group(4);
					valleyType[vCount] = OasisLookup(gif[vCount]);
					// EventLog.log(m.group(3) + " 4 " + m.group(4) + " 5 " + m.group(5));
					// pattern includes " - remove them to store
					population[vCount] = m.group(5).replace('"', ' ').trim();
					player[vCount] = m.group(6).replace('"', ' ').trim();
					alliance[vCount] = m.group(7).replace('"', ' ').trim();
					// default new V35 fields
					resTypeNo[vCount] = 0;
					valleyTypeNo[vCount] = 0;				
				}
				log.trace(String.format("Valley %d %s \"%s\" d=%s&c=%s", 
						vCount, id[vCount], valleyName[vCount], dRef[vCount], cRef[vCount] ));
				/* EventLog.log(String.format("Valley %d %s \"%s\" d=%s&c=%s", 
						vCount, id[vCount], valleyName[vCount], dRef[vCount], cRef[vCount] )); */
				// store info to config but not save to file
				valley[vCount].store();
			} else {
				// did not find match
				log.error("Error parsing Valleys around " + centreX + "," + centreY);
				break;
			}
			try {
			} catch (NumberFormatException nfe) {
				throw new ConversationException("Error parsing Valley in " + this.getName());
			}
		}
		
		// check found valid set of data
		if (vCount == 49) {
			// open an output file
			try {
				// the file is opened each time in append mode to allow for a gradual build up of information
				// PrintWriter out = new PrintWriter(new FileOutputStream(new File("logs\\CropFinder.csv"), true));
				openOutput();
				// Centre Map on Village at centre of 7x7
				// recreate the sequence I use manually - seems to be only way to use back for quicker checking
				// rather than use z= reference direct
				log.info("Checking 7x7 around " + valleyName[25] + " " +id[25]);
				mapUrlString = "karte.php?d=" + dRef[25] + "&c=" + cRef[25]; 
				mapUrlString = Util.getFullUrl(this.villageUrlString, mapUrlString);
				// Travian 3.5 included a major restructure to the map
				// if (util.isVersion35orAbove()) {
				if (!util.isTravianVersionBelow(TravianVersion.V35)) {
					// all information is on the map
				} else {
					// log.debug("GAC Centre URL " + mapUrlString );
					page = util.httpGetPage(mapUrlString,true);	// navigate quickly
					// now go back to 7x7 - this is the one we keep to reuse
					mapUrlString = "karte.php?z=" + dRef[25]; 
					mapUrlString = Util.getFullUrl(this.villageUrlString, mapUrlString);
					// log.debug("GAC Back To Centre URL " + mapUrlString );
					page = util.httpGetPage(mapUrlString,true);
					// write header - note writing both empty and settled valleys which have different info					
				}

				// now check all the valleys
				for (Integer vNo = 1; vNo <= vCount ; vNo++) {
					// store the url for direct use - centre of map using z or actual url
					// valleyUrlString[i] = util.getBaseUrl() + "karte.php?z=" + dRef[i]; 						
					valleyUrlString[vNo] = util.getBaseUrl() + "karte.php?d=" + dRef[vNo] + "&c=" + cRef[vNo];
					// calculate the distance from origin of search
					Double xDiff = Double.parseDouble(xPos[vNo]) - fromX;
					Double yDiff = Double.parseDouble(yPos[vNo]) - fromY;
					distance[vNo] = Math.sqrt(Math.pow(xDiff,2)+Math.pow(yDiff,2));
					
					// more information was always present before V35 so first select mode of operation
					// if (util.isVersion35orAbove()) {
					if (!util.isTravianVersionBelow(TravianVersion.V35)) {
						// extract information from resource type field
						if (resTypeNo[vNo] == 0 ) {
							// an oasis
							valleyType[vNo] = Oases[valleyTypeNo[vNo]][0];
						} else {
							// Village site - can always set resources even if occupied
							for (Integer rCount = 0 ; rCount < 4 ; rCount++ ) {
								resCount[vNo][rCount] = resCounts[resTypeNo[vNo]][rCount];
								// log.debug(String.format("%d.%d %s %s %d", vNo, rCount, id[vNo],
								//		valleyType[vNo], resCount[vNo][rCount]) );
							}
							cropCount[vNo] = resCount[vNo][3];
							// now check for croppers
							if ((cropCount[vNo] == 15) || (cropCount[vNo] == 9) || (cropCount[vNo] == 7)) {
								EventLog.log(String.format("%d Cropper Found at %s", cropCount[vNo], id[vNo]));
								// wait a bit longer - simulate writing result down if it is a cropper
								// GAC is this still valid in 3.5 mode?
								util.shortestPause(false);
							}
						}
						// EventLog.log(valleyType[vNo]+" Optional Info: "+gif[vNo]);
						// check if occupied by looking for , in the optional information
						if (gif[vNo].indexOf(',') != -1) {
							// [-223,-89,3,0,"d=391867&c=c8","b20","V5","gax","351","TKT~BOOM"]
				            // EventLog.log("sevenBySeven.village gif"+": "+gif[vNo]);
							p = util.getPattern("sevenBySeven.village");
							m = p.matcher(gif[vNo]);
							if (m.find()) {
								// String dInfo = "";
					            // for (int i = 0 ; i++ < m.groupCount() ; ) { dInfo = dInfo.concat(","+m.group(i)); }
					            // EventLog.log("sevenBySeven.village"+": "+dInfo);
								valleyName[vNo] = m.group(2);
								player[vNo] = m.group(3);
								alliance[vNo] = m.group(5);	
								
								valley[vNo].setName(m.group(2));
								valley[vNo].setAlliance(m.group(5));
								valley[vNo].setOwner(0,m.group(3));
								if (m.group(4).equals("-")) {
									// oases - type should already be set
								} else {
									valleyType[vNo] = "Village";								
									population[vNo] = m.group(4);
									valley[vNo].setPopulation(Integer.parseInt(m.group(4)));
								}
								// store info to config but not save to file
								valley[vNo].store();
							} else {
								// did not find match
								log.error("Error parsing Valley (" + xPos[vNo] + "," + yPos[vNo] + ")");
							}
						} else {
							// leave as oasis or default								
						}
					} else if (valleyName[vNo].length()==0) {
						// unoccupied preV35 as name is empty not null - overwrite valley type
						// TODO - should come from language file
						valleyType[vNo] = "Unoccupied";		// "Abandoned valley";
						log.trace("Checking " + id[vNo] );
						// select it and count crop fields
						String nextUrlString = "karte.php?d=" + dRef[vNo] + "&c=" + cRef[vNo];
						nextUrlString = Util.getFullUrl(this.villageUrlString, nextUrlString);
						// EventLog.log("GAC MapURL " + nextUrlString );
						page = util.httpGetPage(nextUrlString, true);
						
//						<td class="s7 b">4</td><td> Woodcutters</td>		
						// p = Pattern.compile("(?s)(?i)(?u)"+"<td class=\"([^\"]*)\">(\\d+)</td><td>([^<]*)");
						p = util.getPattern("sevenBySeven.resCount");
						// x y gif pop player ... ally dref cref name
						// "\"[^>]*title=\"([^\"]*)\"\\s*href=\"karte\\.php\\?d=(\\d+)&c=(\\w+)\"\",\"src\":\"img\\/un\\/m\\/(\\w*)");
						// title="" href="karte.php?d=391912&c=a2"
						m = p.matcher(page);
						int rCount = 0;
						while (m.find()) {
							if (m.groupCount() > 2) {
								resCount[vNo][rCount] = Integer.parseInt(m.group(2));
								rCount++;
								log.trace(String.format("%d.%d %s %s %s", vNo, rCount, id[vNo],
										m.group(2).trim(), m.group(3)) );
								// GAC cheat - crop is always last so dont need to check for crop or wheat title
								// if do check need to include trim as some have leading space
								// could also use to set correct resCount based on Villonanny Info although enum order seems wrong to me
								if (rCount == 4) {
									cropCount[vNo] = Integer.parseInt(m.group(2));
									// now check for croppers
									if ((cropCount[vNo] == 15) || (cropCount[vNo] == 9) || (cropCount[vNo] == 7)) {
										EventLog.log(String.format("%d Cropper Found at %s", cropCount[vNo], id[vNo]));
										// wait a bit longer - simulate writing result down if it is a cropper 
										util.shortestPause(false);
									}
								}
								// do we want to flag anything else like extra clay
								// the jpg file name is on each page as f1=9/1/1/1 f3 =4/4/4/6 but seems no easy way to identify it
								// rather than rely on log better if append all outputs to a file - one line per reference below
									
							} else {
								// did not find match
								log.error("Error parsing Valley (" + xPos[vNo] + "," + yPos[vNo] + ")");
								break;
							}
						}
						// back to map
						page = util.httpGetPage(mapUrlString, true);
					} else {
						// pre V35 Oasis, type already set from gif lookup
						
						// TODO - Count Oasis to work out Crop Bonus
						
						// TODO - Could store occupied site player info including links for a plus type feature
						// player & id, alliance & id, pop all easy
						// type of village to see if cropper seems to be image based
						// &nbsp;(89|-98)&nbsp;</div></h1></div><div class="dname c"><br><br>(Capital)</div><div id="f3"></div>
						// (-176|-92)&nbsp;</div></h1></div><div id="f6"></div>		15 cropper
					}
					// output the information
					outputValley(vNo);
				} // end of for each valley
				// close any output file
				closeOutput();
			} catch (IOException e) {
				log.error("Can't output ValleyInformation to File ", e);
			}
		} else {
			log.error("Error only found " + Integer.toString(vCount) + " Valleys around " + centreX + "," + centreY );
			if (vCount == 0) {
				return;
			}
		}
		// store all updated info to config file
		valley[vCount].save();
	}

	// return owning village name for this strategy - used in error message
	public String getName() {
		return villageName;
	}


}

package net.villonanny;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.villonanny.Util.Pair.IntPair;
import net.villonanny.entity.Server;
import net.villonanny.entity.ServerFatalException;
import net.villonanny.misc.TravianVersion;
import net.villonanny.misc.captcha.BaseCaptchaHandler;
import net.villonanny.type.ResourceType;
import net.villonanny.type.ResourceTypeMap;
import net.villonanny.type.TribeType;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.cookie.CookieSpec;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;
import org.slf4j.helpers.MessageFormatter;

/**
 * In VilloNanny, there is one instance of Util per Server.
 *
 */
public class Util {
	private static final Logger log = Logger.getLogger(Util.class);
	public static final String USERAGENT_DEFAULT = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1; InfoPath.1; .NET CLR 1.1.4322; .NET CLR 2.0.50727)";
	private static final String ERROR_MESSAGE_BUNDLE_NAME = "Messages";
	private static boolean utf8;
	public static final long MILLI_SECOND = 1000;
	public static final long MILLI_MINUTE = 60 * MILLI_SECOND;
	public static final long MILLI_HOUR = 60 * MILLI_MINUTE;
	private Translator translator;
	private String serverHost;
	private int serverPort;
	private List<String> loginPostNames;
	private List<String> loginPostValues;
	private String loginPassword = null; // Remember password when typed by user
	private long serverTimeMillisDelta=0; // Difference between PC time and server time in milliseconds (>0 if server lower)
	private String baseUrl; // http://s1.travian3.it/
	private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");;
	private HierarchicalConfiguration serverConfiguration;
	private String serverId;		// user@loginUrl
	private String userName;		// user name on this server
	private Server server;
	private static long futureTimeMillis=System.currentTimeMillis() + 15768000000L; // base system time in milliseconds to measure long time
	
	private HttpClient client;
//	private SimpleDateFormat completionTimeFormat = new SimpleDateFormat("HH:mm");
//	private int redirectCounter = 0;
	private String lastVisitPage = "";
	public static final String P_FLAGS = "(?s)(?i)(?u)"; // dotall, case insensitive, unicode case match
	private static int screenx = 1280;
	private static int screeny = 1024;
	private static int uidCounter = 0;
	private ConfigManager configManager;
	private Console console;
	private TravianVersion travianVersion = TravianVersion.DEFAULT;
	private TribeType tribeType;
	private PropertiesConfiguration patternOverride = null;

	private static int testMode = 0;		// GAC add mode to run from saved pages on disk
	private static String	TEST_DIR = "logs" + File.separator + "TestPages-";		// and the root of a location for them
	/*
	 *  GAC added for test mode  
	 * 	Fetch entire contents of a text file and return as a string, use to load pages from disk if doing repeated testing
	 * 		controlled by global config variable	<testMode	httpGet="1" />
	 *	the files are stored in a sub directory specified by TEST_DIR with the language appended to seperate them 
	 *
	 *	Based on PatternDebugger routine but without header so can save source pages as files from browser using view source
	 *		mode 1 - read from disk, currently does a hard exit if cannot find a page to support simple debug strategy  
	 *		mode 2 - write all pages to disk, coverts . and ? characters to _ and appends .txt for easy access from text editors
	 *	TODO mode 3, read from disk until cannot find a page then fetch it and write it 
	 *			- does not always work because of cookies error
	 */
	private String readFromFile(String urlString) throws IOException {
		// build directory from base + language specific so store identical named pages in different location
		String inputDir = TEST_DIR + getTranslator().getLanguage();
	  	String filePath = null;
		// extend to include server as a sub directory - control via config variable?
	  	filePath = urlString.substring(urlString.indexOf('/', 1)+2, urlString.indexOf('.', 1) );
	  	inputDir = inputDir + File.separator + filePath;
	  	// EventLog.log("Getting URL from Disk " + urlString + " Translator " + getTranslator().getLanguage() );
	  	// log.debug("GAC Getting URL from Disk " + urlString);
	  	if ( urlString.indexOf('/', 8) > 0) {
	  		filePath = inputDir + File.separator + urlString.substring(1+urlString.indexOf('/', 8));		// GAC get name at end of string after first http://
	  	} else {
	  		// no substring
	  		filePath = inputDir + File.separator + "dorf1.php";
	  	}
		// store base Url as not calling login
		baseUrl = urlString.substring(0, urlString.indexOf("/", "http://".length()) + 1);
	  	// check for characters that are not valid in filenames and then add .txt to make into safe windows file
		filePath = filePath.replace('?', '_');
		filePath = filePath.replace('.', '_');
		filePath = filePath.concat(".txt");
	  	// default the map look ups so dont have to do all of them
	  	if (filePath.indexOf("&c=") !=-1) {
	  		// check if have this one
	  		File f = new File(filePath);
	  		if (f.exists()) {
	  			// all ok use it
	  		} else {
	  			// default to an unoccupied valley - needs to be saved manually
	  			filePath = TEST_DIR + getTranslator().getLanguage() + File.separator + "karte_php_unoccupied.txt";			
	  		}	
	  	}
	  	EventLog.log("Getting " + urlString + " from DiskFile \"" + filePath + "\"");
		// BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), Util.getEncodingString()));
		// BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "ASCII"));
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF8"));
		/*
		String line = in.readLine(); // date
		if (line==null) {
			throw new IOException("Empty file");
		}
		this.desc = in.readLine();
		if (this.desc == null) {
			throw new IOException("File too short: missing description on second line");
		}
		log.info("Description: " + desc);
		String patternString = in.readLine();
		if (patternString==null) {
			throw new IOException("File too short: missing pattern on third line");
		}
		log.info("patternString: " + patternString);
		this.pattern = Pattern.compile(patternString);
		*/
		StringBuffer pageBuffer = new StringBuffer();
		String line = in.readLine();
		while (line!=null) {
			pageBuffer.append(line + "\n");
			line = in.readLine();
		}
		in.close();
		return pageBuffer.toString();
	}
	
	public void writeToFile(String urlString, String page) {
		String outputDir = TEST_DIR + getTranslator().getLanguage();
		String outputFilename = null;
		// GAC add server as subdirectory
		outputFilename = urlString.substring(urlString.indexOf('/', 1)+2, urlString.indexOf('.', 1) );
	  	outputDir = outputDir + File.separator + outputFilename;
	  	// EventLog.log("Getting URL from Disk " + urlString);
	  	log.debug("Write URL to Disk " + urlString);
	  	if ( urlString.indexOf('/', 8) > 0) {
	  		outputFilename = urlString.substring(urlString.indexOf('/', 8) + 1);		// GAC get name at end of string after first http://
	  	} else {
	  		// no substring
	  		outputFilename = "dorf1.php";
	  	}
		// store base Url as not calling login
		baseUrl = urlString.substring(0, urlString.indexOf("/", "http://".length()) + 1);
	  	// check for characters that are not valid in filenames and then add .txt to make into safe windows file
		outputFilename = outputFilename.replace('?', '_');
		outputFilename = outputFilename.replace('.', '_');
		outputFilename = outputFilename.concat(".txt");
	  	// GAC dont default the map look ups so one day may have all of them
		
		// String outputFilename = "match" +  pattern.toString().hashCode() + ".txt";
		// Globally synchronized to avoid file corruption when writing concurrently from different "servers"
		// GAC Performance may be an issue if used a lot
		// TODO - add server name into directory structure
		synchronized (Util.class) {
			try {
				File outDirFile = new File(outputDir);
				if (!outDirFile.exists()) {
					log.debug("Creating directory " + outDirFile.getAbsolutePath());
					outDirFile.mkdirs();
				}
				String fullPath = outputDir + File.separator + outputFilename;
				// seem to have problem using util default - force pages to be utf-8
				// PrintWriter out = new PrintWriter(new File(fullPath), Util.getEncodingString());
				PrintWriter out = new PrintWriter(new File(fullPath), "UTF8");
				// out.println(new Date());
				// out.println(desc);
				// out.println(pattern.toString());
				out.println(page);
				out.close();
				log.debug("Page saved to file " + fullPath);
			} catch (IOException e) {
				log.error("Cannot output page to file", e);
			}
		}
	}
	
	static {
		// Screen size
		// This gives some problems on linux when done between resolution changes, so we do it just once for all and forget about it
		try {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			screenx = screenSize.width;
			screeny = screenSize.height;
		} catch (Throwable e) {
			log.debug("Can't get screen size, using default");
		}
	}

	// Used by AutoConfigurator only to create a minimal Util
	public Util(HierarchicalConfiguration serverConfig, ConfigManager newConfigManager) {
		this.configManager = newConfigManager;
		this.console = new Console(configManager);
		this.client = getHttpClient();
		setServerConfig((SubnodeConfiguration) serverConfig);
		this.translator = new Translator(configManager);
	}
	
	public Util(HierarchicalConfiguration serverConfig, ConfigManager newConfigManager, Console console) {
		this(serverConfig, newConfigManager, console, true);
	}
	
	public Util(HierarchicalConfiguration serverConfig, ConfigManager newConfigManager, Console newConsole, boolean setLanguage) {
		this.configManager = newConfigManager;
		this.console = newConsole;
		this.client = getHttpClient();
		setServerConfig((SubnodeConfiguration) serverConfig);
		if (setLanguage) {
			String languageCode = serverConfig.getString("/@language");
			this.translator = new Translator(languageCode, configManager);
			if (!configManager.hasLanguage(languageCode)) {
				String message = Util.getLocalMessage("msg.noLanguageConfig", this.getClass());
				throw new FatalException(MessageFormatter.format(message, languageCode, ConfigManager.CONFIGDIR));
			}
		} else {
			this.translator = new Translator(configManager);
		}
		String version = serverConfig.getString("/@version", null);
		if (version == null) {
			log.info(String.format("Server \"%s\" is missing the \"version\" attribute", serverConfig.getString("/@desc", "")));
			version = TravianVersion.DEFAULT.name();
		}
		try {
			travianVersion = TravianVersion.valueOf(version.toUpperCase());
		} catch (IllegalArgumentException e) {
			String eventMessage = Util.getLocalMessage("msg.invalidTravianVersion", this.getClass());
			String msg = MessageFormatter.format(eventMessage, version);
			throw new FatalException(msg, e);
		}
		String overrideFile = serverConfig.getString("/@patternOverride", null);
		if (overrideFile != null) {
			try {
				patternOverride = new PropertiesConfiguration(overrideFile);
				log.info("Using pattern override for server \"" + serverConfig.getString("/@desc", "") + "\": \"" + overrideFile + "\"");
			} catch (ConfigurationException e) {
				log.error("Can't load pattern override file (ignored) " + overrideFile, e);
			}
			
		}
		screenx = configManager.getInt("/screenSize/@x", screenx);
		screeny = configManager.getInt("/screenSize/@y", screeny);
		log.debug("Screen size set to " + screenx + "x" + screeny);
		log.debug("Travian version = " + travianVersion.name().toLowerCase());
	}
	
	/**
	 * Return a unique identifier 
	 * @param prefix 
	 * @return
	 */
	public static synchronized String getNewUid(String prefix) {
		uidCounter++;
		// This is reasonably unique and short
		return prefix + String.valueOf(System.currentTimeMillis()/1000 % 10000000) + uidCounter;
	}

	public static void log(String message, Exception e) {
		log.error(message + ": " + e.getMessage());
		if (log.isDebugEnabled()) {
			log.error("Stack Trace", e);
		}
	}
	
	public static String format(Date date) {
		return VilloNanny.formatter.format(date);
	}
	
	/*
	 *  change to return fixed dates so can test
	 */
	public static long NotInTheNearFuture() {
		// return (long) (System.currentTimeMillis() + 21536000000L); // in a long time, but not IncrediblyLongTime since that has another meaning
		return (long) (futureTimeMillis); // in a long time, but not IncrediblyLongTime since that has another meaning
	}
	
	public static Date getIncrediblyLongTime() {
		// Date	nextYear = new Date();
		// Calendar	nextYear =  Calendar.getInstance();
		// 3600hr, 86400dy, 604800week, 2419200month4, 2592000month30, 7884000qtr, 15768000mth6, 31536000yr
		// if (System.currentTimeMillis() > (serverStartTimeMillis + 604800 000L)) {
		// if (System.currentTimeMillis() > (serverStartTimeMillis + 2419200000L)) {
		if ((System.currentTimeMillis()  + 7884000000L) > (futureTimeMillis)) {
			// been running for 3 months bump the long time out 6 months
			futureTimeMillis = System.currentTimeMillis() + 15768000000L;
			log.debug("Setting NotInTheNearFuture Time to " + new Date(futureTimeMillis) );
		}
		log.trace("NNF "+new Date(futureTimeMillis)+" NEVER "+new Date(futureTimeMillis + 31536000000L));
		return new Date(futureTimeMillis + 31536000000L);	// next year
		// was return new Date(System.currentTimeMillis() + 31536000000L); // next year
	}
	
	public String httpGetPage(String urlString) throws ConversationException {
		return httpGetPage(urlString, false);
	}
	
	public String httpGetPage(String urlString, boolean quick) throws ConversationException {
		log.trace("Getting " + urlString + " (with login check) ...");
		if (urlString==null) {
			log.warn("Nothing to get");
			return "";
		}
		accessOrderFix (urlString);  //try to fix url 'clicking' so it emulates a person clicking
		String page = httpGetPageNoFix(urlString, quick);
		return page;
	}
		
	public String httpGetPageNoFix(String urlString, boolean quick) throws ConversationException {
		log.trace("Getting " + urlString + " (with login check) ...");
		if (urlString==null) {
			log.warn("Nothing to get");
			return "";
		}
		accessOrderCheck (urlString); //a test to warn of illegal 'click' order
		String page = httpGetPageNoLogin(urlString, quick);
		// Check if login page returned, and perform login
		if (isLoginPage(page)) {
			String s = "Login page returned; performing login for " + urlString;
//			log.debug(s);
			EventLog.log(s);
			page = loginWithPage(page, urlString, quick);
			if (isLoginPage(page)) {
				throw new FatalException("Can't login");
			}
			// Now fetch original page again because we might get the wrong village otherwise
			page = httpGetPageNoLogin(urlString, quick);
		}		
		log.trace("Got (with login check) " + urlString);
		return page;
	}
	
	public String httpGetPageNoLogin(String urlString, boolean quick) throws ConversationException {
		console.checkFlags();
		// check for codes - are being used in some return urls
		if (urlString.contains("&amp;")) {
			log.trace("Replacing &amp; in "+urlString);
			urlString = urlString.replaceAll("&amp;", "&");			
		}
		// GAC modify to run from local files for decoupled debug
		testMode = configManager.getInt("testMode/@httpGet", 0);	

		  if 	((testMode & 1) == 1) {
			  	// log.debug("GAC Getting " + urlString + " from DiskFile " + fname);
			  	String page = null;
				try {
					page = readFromFile(urlString);
					// return the page
					return page;
				} catch (IOException e) {
					// GAC - check if in read/write mode, in which case ignore the error and carry on to access page
					if 	((testMode & 2) == 2) {
						// Warn what we are doing
						EventLog.log("Cannot Find Page on Disk " + urlString + " ***** Getting from Server");
					} else {
						// TODO Auto-generated catch block
						e.printStackTrace();
						// GAC just exit for now - create missing file manually and restart!
						System.exit(-1);
					}
				}
		  } 	// else {		// GAC added for test mode, returns above so just continue rather than make second change
		GetMethod get = new GetMethod(urlString);
		addHeaders(get);
		try {
			log.trace("Getting " + urlString + " ...");
			//EventLog.log("Getting " + urlString);
			client.executeMethod(get);
			
			// Save the url for reference header
			lastVisitPage = urlString;
			
			String page = get.getResponseBodyAsString();
			
			// Find src fields and load them to simulate real browser
			// Pattern p = Pattern.compile(" src=\"([^\"\\?]*)[^\"]*\"");
			Pattern p = getPattern("util.src");
			Matcher m = p.matcher(page);
			try {
				while (m.find()) {
					String src = m.group(1);
					getIfNotCachedAndDrop(urlString, src);
				}
			} catch (IndexOutOfBoundsException e) {
				saveTestPattern("util.src", p, page);
				throw new FatalException(e);
			}
			// GAC add mode to write file to disk
			if 	((testMode & 2) == 2) {
				writeToFile(urlString, page);
			}
			if ((testMode & 4) == 4) {
				// log all urls visited
				// throw an error if want a e.printStackTrace();
				EventLog.log("httpGet:"+urlString);
			}
			failOnCaptcha(page);
			return page;
		} catch (java.net.ConnectException e) {
			throw new ConversationException("Connection to \"" + urlString + "\" failed (check network/proxy setup).", e);
		} catch (IOException e) {
			throw new ConversationException("Can't read page " + urlString, e);
		} finally {
			get.releaseConnection();
			shortestPause(quick);
			log.trace("Got " + urlString);
		}		
	}

	/**
	 * Force login
	 * @throws ConversationException
	 */
	public void login(boolean sharp) throws ConversationException {
		int counter = 2; // retries
		while (true) {
			try {
				String loginUrlString = serverConfiguration.getString("/loginUrl");
				// Get login form. The method will detect the login page and perform login for us
				httpGetPage(loginUrlString);
				break;
			} catch (ConversationException e) {
				EventLog.log("Login failed: " + e.getMessage());
				log.error(e);
				if (counter-- > 0) {
					EventLog.log("Retrying...");
					shortestPause(sharp);
				} else {
					log.error("Exiting " + serverConfiguration.getString("/@desc"));
					throw e;
				}
			}
		}
	}
	
	/**
	 * 
	 * @loginForm the html page containing the login form
	 * @urlString the url that returned the login form
	 */
	private String loginWithPage(String loginForm, String urlString, boolean quick) throws ConversationException {
		Pattern p;
		Matcher m;
		fillLoginParameters(loginForm, urlString);

		//ugly villoFetch hack by riX
		if(urlString.indexOf("vfthis.net")!=-1){
			baseUrl = urlString.substring(0, urlString.indexOf("/", "http://www.vfthis.net/".length()) + 1);
		  	//EventLog.log("riX baseUrl "+baseUrl);
		}
		String loginPostString = baseUrl + "dorf1.php";

		Calendar localTime = new GregorianCalendar();
		localTime.set(Calendar.YEAR, 1970);
		localTime.set(Calendar.MONTH, Calendar.JANUARY);
		localTime.set(Calendar.DAY_OF_MONTH, 1);
		String pageAfterLogin = httpPostPage(loginPostString, loginPostNames, loginPostValues, quick);
		// See if we got any cookies
        CookieSpec cookiespec = CookiePolicy.getDefaultSpec();
        //ugly villoFetch hack by riX
        //EventLog.log("riX basePath "+baseUrl.substring(baseUrl.indexOf("/", "http://".length()), baseUrl.length()));
        //Cookie[] logoncookies = cookiespec.match(serverHost, serverPort, "/", false, client.getState().getCookies());
        Cookie[] logoncookies = cookiespec.match(serverHost, serverPort, baseUrl.substring(baseUrl.indexOf("/", "http://".length()), baseUrl.length()), false, client.getState().getCookies());
        if (logoncookies.length==0) {
        	this.loginPassword=null;
        	throw new ConversationException("Authentication failed");
        }
        log.info("Authentication ok");
		// Find server time
		// p = Pattern.compile("(?s)id=\"tp1\"[^>]*>(.*?)</span>");
        p = getPattern("util.serverTime");
		m = p.matcher(pageAfterLogin);
		if (m.find()) {
			String serverTimeString = m.group(1);
			Date serverDate;
			try {
				serverDate = timeFormat.parse(serverTimeString); // TODO timezone del server
			} catch (ParseException e) {
				throw new ConversationException("Can't parse server time: " + serverTimeString);
			}
			this.serverTimeMillisDelta = localTime.getTimeInMillis() - serverDate.getTime();
		} else {
		    if (isSurveyPage(pageAfterLogin)) {
		        EventLog.log("Survey page or broadcast msg detected, see logs for more details!");
		        // log.debug(pageAfterLogin);
		        // saveTestPattern will try and process
		        saveTestPattern("SurveyPage", p, pageAfterLogin);
                // survey or broadcast message is present
		        // need to get url from the page????
                // pageAfterLogin = httpGetPage(Util.getFullUrl(urlString, "dorf1.php?ok=1"));
            }
		    throw new ConversationException("Can't find server time");
		}
		return pageAfterLogin;
	}
	
	public boolean isSurveyPage(String page) {
	    // Pattern p = Pattern.compile("dorf1.php\\?ok=1");
		Pattern p = getPattern("util.isSurveyPage");
	    Matcher m = p.matcher(page);
	    return m.find();
	}
	
	private void fillLoginParameters(String loginForm, String urlString) throws ConversationException {
		URL loginUrl;
		if (!urlString.endsWith("/")) {
			urlString += "/";
		}
		try {
			loginUrl = new URL(urlString);
		} catch (MalformedURLException e) {
			throw new FatalException(String.format("loginUrl for server \"%s\" is invalid", serverConfiguration.getString("/@desc")), e);
		}
		baseUrl = urlString.substring(0, urlString.indexOf("/", "http://".length()) + 1);
		//ugly villoFetch hack by riX
		if(urlString.indexOf("vfthis.net")!=-1){
			baseUrl = urlString.substring(0, urlString.indexOf("/", "http://www.vfthis.net".length()) + 1);
		}
		serverHost = loginUrl.getHost();
		serverPort = loginUrl.getPort();
		if (serverPort==-1) {
			serverPort = loginUrl.getDefaultPort();
			if (serverPort==-1) {
				serverPort = 80;
			}
		}
		loginPostNames = new ArrayList<String>();
		loginPostValues = new ArrayList<String>();
		// Find username field
		String userNameField;
		// Pattern p = Pattern.compile("<input .* type=\"text\".*name=\"(.*?)\"");
		Pattern p = getPattern("util.userNameField");
		Matcher m = p.matcher(loginForm);
		if (m.find()) {
			userNameField = m.group(1);
		} else {
			saveTestPattern("util.userNameField", p, loginForm);
			throw new ConversationException("Can't find username input field");
		}
		if (m.find()) {
			log.warn("Too many username input fields; ignoring...");
		}
		loginPostNames.add(userNameField);
		String user = serverConfiguration.getString("/user");
		loginPostValues.add(user);
		// Find password field
		String pwdField;
		// p = Pattern.compile("<input .* type=\"password\".*name=\"(.*?)\"");
		p = getPattern("util.pwdField");
		m = p.matcher(loginForm);
		if (m.find()) {
			pwdField = m.group(1);
		} else {
			saveTestPattern("util.pwdField", p, loginForm);
			throw new ConversationException("Can't find password input field");
		}
		if (m.find()) {
			log.warn("Too many password input fields; ignoring...");
		}
		loginPostNames.add(pwdField);
		String pwd = serverConfiguration.getString("/password");
		if (pwd==null) {
			pwd = this.loginPassword;
			if (pwd==null) {
				EventLog.log("Waiting for password input");
				pwd = inputLine("Type the password for " + user + " on " + serverConfiguration.getString("/@desc") + ": ");
				this.loginPassword = pwd;
			}
		}
		loginPostValues.add(pwd);
		// Find all hidden fields
		// addHiddenPostFields(loginForm, "<form method=\"post\" name=\"snd\" action=\"dorf1.php\">", loginPostNames, loginPostValues);
		addHiddenPostFields(loginForm, "util.hiddenPostFields", loginPostNames, loginPostValues);
		// Add button params
		addButtonCoordinates("s1", 80, 20, loginPostNames, loginPostValues);
		try {
			int pos = loginPostNames.indexOf("w");
			loginPostValues.set(pos, screenx + ":" + screeny); // %3A = ":"
		} catch (Exception e) {
			log.warn("Can't find login parameter 'w' (ignoring)");
		}
		// Other params
//		loginPostNames.add("autologin");
//		loginPostValues.add("ja");
	}
	
	public static void addButtonCoordinates(String prefix, int x, int y, List<String> names, List<String> values) {
		int vx = (int) (Math.random() * x);
		int vy = (int) (Math.random() * y);
		// gac make sure at least 1, 1
		if (vx == 0) {
			vx = 1;
		}
		if (vy == 0) {
			vy = 1;
		}
		names.add(prefix + ".x");
		values.add(Integer.toString(vx));
		names.add(prefix + ".y");
		values.add(Integer.toString(vy));
	}

	/**
	 * @return the start position of the form
	 */
	public int addHiddenPostFields(String page, String patternKey, List<String> names, List<String> values) throws ConversationException {
		Pattern p;
		Matcher m;
		// Find start of form
		// p = Pattern.compile(startFromPattern);
		p = getPattern(patternKey);
		m = p.matcher(page);
		if (!m.find()) {
			saveTestPattern(patternKey, p, page);
			throw new ConversationException("Can't find start of form with pattern \"" + p.pattern() + "\"");
		}
		int startPos = m.start();
		// Find end of form
		// p = Pattern.compile("</form>");
		p = getPattern("util.formEnd");
		m = p.matcher(page);
		m.region(startPos, page.length());
		// Confirm send resource does not have "</form>" 
		int endPos = page.length();
		if (m.find()) {		
			endPos = m.end();
		}
		
		// Test for JamVM problem
		if (endPos < startPos) {
			log.debug(String.format("endPos=%s is before startPos=%s. Setting endPos to end of file", endPos, startPos));
			endPos = page.length();
			// log.debug("endPos pattern = " + getPattern("util.formEnd"));
			// log.debug("Page dump\n"+page);
		}
		
		// Find hidden fields
		// p = Pattern.compile("<input +type=\"hidden\" +name=\"(.*?)\" +value=\"(.*?)\"");
		p = getPattern("util.hiddenField");
		m = p.matcher(page);
		m.region(startPos, endPos);
		while (m.find()) {
			String name = m.group(1);
			String value = m.group(2);
			log.trace("Adding name ("+name+") value ("+value+")");
			names.add(name);
			values.add(value);
		}	
		return startPos;
	}

	public boolean isLoginPage(String page) {
		Pattern p;
		Matcher m;
		// p = Pattern.compile("(?s)(?i)<input\\s*type\\s*=\\s*\"image\"\\s*value\\s*=\\s*\"login\"");
		p = getPattern("util.isLoginPage");
		m = p.matcher(page);
		return m.find();
	}

	private void addHeaders(HttpMethod m) {
	    m.setRequestHeader(
	                "Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
		m.addRequestHeader("Accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
		m.addRequestHeader("Accept-Language", "en,it;q=0.5");
		m.addRequestHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");


		// Use the lastVisitPage
		if (!lastVisitPage.equals("")) {
			m.addRequestHeader("Referer", lastVisitPage);
		}
	}

	public String httpPostPage(String url, List<String> postNames, List<String> postValues, boolean quick) throws ConversationException {
		log.debug("Posting " + url + " ...");
		console.checkFlags();
		
		  // GAC modify to run from local files for decoupled debug
		  if 	((testMode & 1) == 1) {
			  	String page = null; 
				try {
					page = readFromFile(url+postNames);
					return page;
				} catch (IOException e) {
					// GAC - check if in read/write mode, in which case ignore the error and carry on to access page
					if 	((testMode & 2) == 2) {
						// Warn what we are doing
						EventLog.log("Cannot Find Page on Disk " + url + " ***** Getting from Server");
					} else {
						// TODO Auto-generated catch block
						e.printStackTrace();
						// GAC just exit for now - create missing file manually and restart!
						System.exit(-1);
					}
				}
		  } // else {		// GAC added for test mode
			// continue with original code
		
		PostMethod httpPost = new PostMethod(url);
		addHeaders(httpPost);
		NameValuePair[] postData = new NameValuePair[postNames.size()];
		String dInfo = "";
		for (int i = 0; i < postData.length; i++) {
			postData[i] = new NameValuePair(postNames.get(i), postValues.get(i));
			dInfo = dInfo.concat(postData[i].getName()+"="+postData[i].getValue()+"&");
		}
		httpPost.setRequestBody(postData);
		// trace params except for login
		if (!dInfo.contains("login")) {
			log.debug("httpPost:"+httpPost.getPath()+","+dInfo);
	        // System.exit(0);
		}
		
		String page;
		try {
			client.executeMethod(httpPost);

			// Save the url for reference header
			lastVisitPage = url;

			page = httpPost.getResponseBodyAsString();
		} catch (IOException e) {
			throw new ConversationException("Can't read page " + url, e);
		} finally {
			httpPost.releaseConnection();
		}
			
		// Follow any redirects
        int statuscode = httpPost.getStatusCode();
        if ((statuscode == HttpStatus.SC_MOVED_TEMPORARILY) ||
            (statuscode == HttpStatus.SC_MOVED_PERMANENTLY) ||
            (statuscode == HttpStatus.SC_SEE_OTHER) ||
            (statuscode == HttpStatus.SC_TEMPORARY_REDIRECT)) {
            Header header = httpPost.getResponseHeader("location");
            if (header != null) {
                String newuri = header.getValue();
                if ((newuri == null) || (newuri.equals(""))) {
                    newuri = "/";
                }
                log.debug("Redirect target: " + newuri); 
                page = httpGetPageNoLogin(newuri, quick);
            } else {
            	throw new ConversationException("Invalid redirect (location=null)");
            }
        }
		log.debug("Posted " + url);
		
		// GAC add mode to write file to disk
		if 	((testMode & 2) == 2) {
			writeToFile(url+postNames, page);
		}	
		if ((testMode & 4) == 4) {
			// log all urls visited
			// throw an error if want a e.printStackTrace();
			EventLog.log("httpPost:"+url);
		}
		failOnCaptcha(page);
		return page;
	}
	
	private void getIfNotCachedAndDrop(String pageUrl, String src) {
		
		  // GAC modify to run from local files for decoupled debug
		  if (testMode == 1) {
			  	// GAC TODO - consider how handle in mode 3, at the moment only ignoring for mode 1
			  	// hoping it was cached previously!
			  	EventLog.log("Ignoring Cache Request: " + pageUrl);
			  	return ;
		  } // else {		// GAC added for test mode


		try {
			// src can be relative or absolute
			// - when relative, ok
			// - when absolute, remove start
			String relative = src;
			if (src.startsWith("http://")) { // Not relative
				int pos = relative.indexOf("/", "http://".length());
				relative = relative.substring(pos);
			} else {
				// Make full url
				int pos = pageUrl.indexOf("/", "http://".length());
				if (pos==-1) {
					src = pageUrl + "/" + src;
				} else {
					src = pageUrl.substring(0, pos + 1) + src;
				}
			}
			String cachePath = configManager.getString("/imageCache/@path", "imageCache");
			File file = new File(cachePath, relative);
			if (!file.canRead()) {
				log.debug("Caching resource : " + src);
				GetMethod getObj = new GetMethod(src);
				try {
					addHeaders(getObj);
					client.executeMethod(getObj);
					// Non need to save it, just create a placeholder
					file.getParentFile().mkdirs();
					file.createNewFile();
					// Save in cache
//				FileOutputStream output = new FileOutputStream(file);
//				InputStream input = getObj.getResponseBodyAsStream();
//				int data;
//				while ((data=input.read()) > -1) {
//					output.write(data);
//				}
//				output.close();
				} catch (Exception e) {
					log.warn("Error while caching resource " + src + " (ignored)", e);
					// ignored
				} finally {
					getObj.releaseConnection();
				}
			}
//		else {
//			log.debug("Skipping cached resource: " + src);
//		}
		} catch (Exception e) {
			log.warn("Error while checking cache for resource " + src + " (ignored):" + e.getMessage());
			// ignored
		}
	}
	
	public static void sleep(long milli) {
		if (milli<0) {
			log.debug("Not sleeping: negative value " + milli);
			return;
		}
		Date awake = new Date(System.currentTimeMillis() + milli);
		String s = String.format("Sleeping %s minutes until %s ...", milli / MILLI_MINUTE, format(awake));
//		log.info(s);
		EventLog.log(s);
		try {
			Thread.sleep(milli);
		} catch (InterruptedException e) {
			log.debug("Sleep interrputed");
			// Nothing
		}
//		log.debug("Resuming after pause");
	}
	
	public void dayPause(long minimumPauseMillis, boolean sharp, long sharpPauseMillis) {
		log.debug("dayPause("+minimumPauseMillis+","+sharp+","+sharpPauseMillis+")");
		long minMillis = configManager.getInt("dayExtraPauseMinutes/@min", 5) * MILLI_MINUTE;
		long maxMillis = configManager.getInt("dayExtraPauseMinutes/@max", 10) * MILLI_MINUTE;			
		// long minPause = configManager.getInt("dayPauseMinutes/@max", -1) * MILLI_MINUTE;
		long minPause = configManager.getInt("dayExtraPauseMinutes/@minPauseMinutes", -1) * MILLI_MINUTE;
		long maxHours = configManager.getInt("dayExtraPauseMinutes/@maxHours", -1);
		if ((sharp == false) && (minPause >= 0)) {
			if (minimumPauseMillis < minPause) {
				log.debug("Day min:"+minMillis+" max:"+maxMillis+" minPause:"+minPause);
				minimumPauseMillis = minPause ;
			}
		}
		doPause(minimumPauseMillis, minMillis, maxMillis, sharp, sharpPauseMillis, maxHours);
	}

	public void nightPause(long minimumPauseMillis, boolean sharp, long sharpPauseMillis) {
		log.debug("nightPause("+minimumPauseMillis+","+sharp+","+sharpPauseMillis+")");
		long minMillis = configManager.getInt("nightExtraPauseMinutes/@min", 60) * MILLI_MINUTE;
		long maxMillis = configManager.getInt("nightExtraPauseMinutes/@max", 90) * MILLI_MINUTE;
		long minPause = configManager.getInt("nightExtraPauseMinutes/@minPauseMinutes", -1) * MILLI_MINUTE;
		long maxHours = configManager.getInt("nightExtraPauseMinutes/@maxHours", -1);
		if ((sharp == false) && (minPause >= 0)) {
			if (minimumPauseMillis < minPause) {
				log.debug("Night min:"+minMillis+" max:"+maxMillis+" minPause:"+minPause);
				minimumPauseMillis = minPause ;
			}
		}
		doPause(minimumPauseMillis, minMillis, maxMillis, sharp, sharpPauseMillis, maxHours);
	}
	
	private void doPause(long neededMillis, long minAddMillis, long maxAddMillis, boolean sharp, long sharpPauseMillis, long maxHours) {
		if (console.isQuitting()) {
			return;
		}
		// support optional check not too long
		if ((maxHours > 0) && (neededMillis > (maxHours * MILLI_HOUR))) {
			log.info("Limiting Maximum Pause to "+maxHours+" Hours from "+milliToTimeString(neededMillis));
			neededMillis = maxHours * MILLI_HOUR;
		}
		long millis = MILLI_MINUTE;
		if (sharp) {
			millis = neededMillis;			
			log.debug("Sharp Pause "+milliToTimeString(neededMillis));
			// sleep(neededMillis);
		} else {			
			millis = neededMillis + (long) (Math.random() * (maxAddMillis-minAddMillis) + minAddMillis);
			// check if sharp is first
			if ((sharpPauseMillis >= 0)&& (sharpPauseMillis < millis)){
				millis = sharpPauseMillis ;
				log.debug("Using Earlier Sharp minimum for Pause "+milliToTimeString(millis));
			}
		}
		sleep(millis);
	}
	
	public void shortestPause(boolean quick) {
		// This pause simulates a user clicking reasonably fast but not too much
		if ("true".equalsIgnoreCase(System.getProperty("QUICK"))) {
			// Override when developing
			quick=true; 
		}
		
		double millis = Math.random();
		if (console.isQuitting()) {
			// Just to be safe do not return, but be quick
			// return
			quick=true; 
		}
		if (quick) {
			// Between 0.1 and 0.5 seconds
			millis = (millis * 500.0) + 100;
		} else {
			// Between 2 and 5 seconds
			millis = (millis * 3000.0) + 2000;
		}

		try {
			log.trace("Sleeping " + (long)millis + (quick?" (quick)":""));
			Thread.sleep((long) millis);
		} catch (InterruptedException e) {
			log.debug("Sleep interrputed");
			// Nothing
		}
		log.trace("Resuming after pausing " + (long)millis + (quick?" (quick)":""));
	}
	
	public static String getFullUrl(String currentPageUrlString, String newUrlEnd) {
		// submitUrlString = submitUrlString.replaceAll("&amp;", "&");
		return currentPageUrlString.substring(0, currentPageUrlString.lastIndexOf("/") + 1) + newUrlEnd;
	}
	
	public String cleanSubmitString(String submitUrlString, String patternKey) {
		Pattern p;
		Matcher m;
		Boolean done=false;
		
		if ((patternKey==null) || (patternKey.isEmpty())) {
			return submitUrlString;
		}
		String removeThis=getPatternString(patternKey);
		if ((removeThis==null) || (removeThis.isEmpty())) {
			return submitUrlString;
		}
		p = Pattern.compile(P_FLAGS + "(\\S+?)"+removeThis+"(\\S+)");
		//log.debug("matcher before loop:"+p.toString());
		while (!done){
			m = p.matcher(submitUrlString);
			if (m.find()) {
				String tt = m.group(1)+m.group(2);
				log.debug("changed emptysite submiturlstring from "+submitUrlString+" to "+tt);
				//log.debug("group1:"+m.group(1)+" group2:"+m.group(2));
				//log.debug("matcher is:"+m.toString()+" pattern is:"+p.toString());
				submitUrlString=tt;
			}
			else {
				done=true;
			}
		}

		return submitUrlString;
	}
	
	/**
	 * Convert "HH:mm:ss" into seconds
	 * @param timeString
	 * @return
	 */
	public static int timeToSeconds(String timeString) {
		int value = 0;
		String[] parts = timeString.trim().split(":");
		for (int i = 0; i < parts.length; i++) {
			String elem = parts[i];
			int elemVal = Integer.parseInt(elem);
			value = value*60 + elemVal;
		}
		return value;
	}
	
	/**
	 * Transforms "HH:mm:ss" into date
	 * @param timeNeeded
	 * @return
	 */
	public Date getCompletionTime(String timeNeeded) {
		int seconds = timeToSeconds(timeNeeded);
		Calendar time = new GregorianCalendar();
		time.add(Calendar.SECOND, seconds);
		return time.getTime();
	}

	public Date calcWhenAvailable(ResourceTypeMap production, ResourceTypeMap availableResources, ResourceTypeMap neededResources) {
		float hoursNeeded = 0;
		for (ResourceType res : ResourceType.values()) {
			if (res==ResourceType.FOOD) {
				continue;
			}
			int missing = neededResources.get(res) - availableResources.get(res);
			float time = missing / (float)production.get(res);
			if (time>hoursNeeded) {
				hoursNeeded = time;
			}
		}
		int seconds = (int) (hoursNeeded * 3600);
		Calendar cal = new GregorianCalendar();
		cal.add(Calendar.SECOND, seconds);
		return cal.getTime();
	}
	
	public boolean isNightTime() {
		try {
			final String FORMAT1 = "yyyy MMM dd";
			String timeFrom = configManager.getString("nightTime/@from", "01:00"); // HH:mm e.g. 23:45
			String timeTo = configManager.getString("nightTime/@to", "06:00"); // e.g. 06:30
			SimpleDateFormat format1 = new SimpleDateFormat(FORMAT1);
			String nowDateString = format1.format(new Date()); // e.g. 2007 May 05
			SimpleDateFormat format2 = new SimpleDateFormat(FORMAT1 + " HH:mm"); // e.g. 2007 May 05 12:33
			Date dateFrom = format2.parse(nowDateString + " " + timeFrom); // e.g. 2007 May 05 23:45
			Date dateTo = format2.parse(nowDateString + " " + timeTo); // e.g. 2007 May 05 06:30
			if (dateFrom.after(dateTo)) {
				dateFrom = new Date(dateFrom.getTime() - 1000L*3600*24);
			}
			Date now = new Date();
			return (now.after(dateFrom) && now.before(dateTo));
		} catch (ParseException e) {
			throw new FatalException("Invalid time format", e);
		}
	}
	
	/**
	 * Simulate a user that is away doing something else
	 * @param minimumPauseMillis 
	 */
	public void userPause(long minimumPauseMillis, boolean sharp, long sharpPauseMillis) {
		try {
			if (isNightTime()) {
				nightPause(minimumPauseMillis, sharp, sharpPauseMillis);
			} else {
				dayPause(minimumPauseMillis, sharp, sharpPauseMillis);
			}
		} catch (Exception e) {
			log.error("Unexpected exception caught (ignored)", e);
			Util.sleep(5000);
		}
	}

	/**
	 * Convert milliseconds into "HH:mm:ss" 
	 * @param milliPause
	 * @return
	 */
	public static String milliToTimeString(long milliPause) {
		long hours = milliPause / 3600000;
		long min = (milliPause - hours*3600000) / 60000;
		long sec = (milliPause % 60000) / 1000;
		return hours + ":" + min + ":" + sec; // TODO aggiungere lo zero iniziale se una cifra
	}

	public static String inputLine(String prompt) {
		return inputLine(prompt, null);
	}
	
	public static String inputLine(String prompt, String defaultValue) {
		// TODO hide typing or at least clear password at the end
		System.out.println(prompt);
		BufferedReader readIn = new BufferedReader(new InputStreamReader(System.in));
		try {
			String result = readIn.readLine();
			// Failed attempt to clear password (doesn't work): System.out.println((char)27 + "A                                                                            ");
			if (defaultValue!=null && (result==null || result.trim().length()==0)) {
				System.out.println(defaultValue);
				return defaultValue;
			}
			return result;
		} catch (IOException e) {
			throw new FatalException("Can't read user input", e);
		}
	}

	public Date serverTimeToLocalTime(Date serverTime) {
		return new Date(serverTime.getTime() + serverTimeMillisDelta);
	}

	public Translator getTranslator() {
		return translator;
	}
	
	public void saveTestPattern(String desc, Pattern pattern, String page) {
		String outputDir = configManager.getString("patternDebug/@path", "logs/patterns"); 
		PatternDebugger patternDebugger = new PatternDebugger(desc, pattern, page);
		patternDebugger.toFile(outputDir);
		// check for survey page after saving pattern
	    if (isSurveyPage(page)) {
	        EventLog.log("Survey page or Broadcast message detected in saveTestPattern");
	        // log.debug(pageAfterLogin);
            // survey or broadcast message is present
	        // need to get url from the page????
            try {
				httpGetPage(Util.getFullUrl(getBaseUrl(), "dorf1.php?ok=1"));
			} catch (ConversationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
	    // check for internet access problem
	    if (isAccessFault(page)) {
	        EventLog.log("Internet Access Problem message detected in saveTestPattern");
        }
	}
	
	/**
	 * check page for internet access problems reported from hub or service provider
	 * @param page
	 * @return
	 */
	public boolean isAccessFault(String page) {
		// should this be a pattern or config variable - it is really user SP specific
		// Pattern p = getPattern("util.accessProblemPage");
		String patternString = configManager.getString("/access/@fault", "can't connect to broadband");
		Pattern p = Pattern.compile(patternString);
	    Matcher m = p.matcher(page);
	    return m.find();
	}
	

	/**
	 * Returns a localised message, loaded from a message bundle.
	 * @param key the key to the message
	 * @param caller the class of the caller, needed to retrieve the bundle file from the same package of the caller
	 * @return
	 */
	public static String getLocalMessage(String key, Class caller) {
		try {
			ResourceBundle bundle = getResourceBundle(ERROR_MESSAGE_BUNDLE_NAME, caller);
			return bundle.getString(key);
		} catch (MissingResourceException e) {
			return key;
		}
	}

	/**
	 * Ottiene un bundle caricando il file che si trova nello stesso package della classe indicata
	 * @param bundleName nomeBundle
	 * @param callerClass chiamante
	 * @return ResourceBundle
	 * @throws MissingResourceException bundle non esiste
	 */
	public static ResourceBundle getResourceBundle(String bundleName, Class caller) throws MissingResourceException {
		StringBuffer fullBundleName = new StringBuffer(caller.getPackage().getName()).append(".").append(bundleName);
		return ResourceBundle.getBundle(fullBundleName.toString());
	}
	
	private HttpClient getHttpClient() {
		HttpClient client = new HttpClient();
		// Timeout
		client.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, (int) (MILLI_SECOND * 30)); // milliseconds 
		client.getParams().setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, (int) (MILLI_SECOND * 30));
		client.getParams().setParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, true);
		// User agent
		String userAgent = configManager.getString("userAgent", USERAGENT_DEFAULT);
		client.getParams().setParameter(HttpMethodParams.USER_AGENT, userAgent);
		// log.trace("userAgent:"+userAgent+" Client:"+client.toString());
		// Proxy
		if (configManager.getBoolean("proxy/@enabled", false)) {
			String host = configManager.getString("proxy/hostName", null);
			int port = configManager.getInt("proxy/hostPort", 0);
			client.getHostConfiguration().setProxy(host, port);
			String user = configManager.getString("proxy/proxyUser", null);
			String pwd = configManager.getString("proxy/proxyPassword", null);
			if (user != null) {
				Credentials credentials = null;
				String ntHost = configManager.getString("proxy/NTHost", null);
				String ntDomain = configManager.getString("proxy/NTDomain", null);
				if ((ntHost != null) && (ntDomain != null)) {
					credentials = new NTCredentials(user, pwd, ntHost, ntDomain);
				} else {
					credentials = new UsernamePasswordCredentials(user, pwd);
				}
				AuthScope authScope = new AuthScope(host, port);
				client.getState().setProxyCredentials(authScope, credentials);
			}
		}
		return client;
	}

	/**
	 * Check if there is a "-utf8" command line argument
	 * @param args
	 * @return
	 */
	public static void setUtf8(String[] args) {
		utf8=false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].trim().toLowerCase().equals("-utf8")) {
				utf8=true;
				break;
			}
		}
		log.debug("utf8 is " + utf8);
	}
	
	public static String startTimeString(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String[] split = args[i].trim().split("=", 2);
			if (split[0].toLowerCase().equals("-starttime") && split.length>1) {
				return split[1];
			}
		}
		return null;
	}
	
	public static String getEncodingString() {
		return utf8?"UTF-8":"ISO-8859-1";
	}
	
	/**
	 * Copy files. Adapted from http://www.rgagnon.com/javadetails/java-0064.html
	 * @param in
	 * @param out
	 * @throws IOException
	 */
    public static void copyFile(File in, File out) throws IOException {
    	FileChannel inChannel = new FileInputStream(in).getChannel();
    	FileChannel outChannel = new FileOutputStream(out).getChannel();
    	try {
    		inChannel.transferTo(0, inChannel.size(), outChannel);
    	} finally {
    		if (inChannel != null) inChannel.close();
    		if (outChannel != null) outChannel.close();
    	}
    }

	public Console getConsole() {
		return console;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public void setServerConfig(SubnodeConfiguration serverConfig) {
		this.serverConfiguration = serverConfig;
	}
	
	/**
	 * store the server class for use from util 
	 * @param server
	 */
	public void setServer(Server server) {
		this.server = server;
	}
	public Server getServer() {
		return server;
	}
	/**
	 * store the server id user@loginurl for use from util 
	 * @param userName
	 * @param serverId
	 */
	public void setServerId(String userName, String serverId) {
		this.userName = userName;
		this.serverId = serverId;
	}
	
	/**
	 * return server id
	 * @return server id user@loginurl
	 */
	public	String	getServerId() {
		return serverId;
	}
	
	/**
	 * return user name
	 * @return String
	 */
	public	String	getUserName() {
		return userName;
	}
	
	/**
	 * return base url
	 * @return String
	 */
	public	String	getBaseUrl() {
		return baseUrl;
	}

	/**
	 * Convert map id to a coordinate
	 * @param id the map id
	 * @return coorinate
	 */
	public static IntPair id2coord(int id) {
        int x = id % 801;
        x -= 401;
        int y = id / 801;
        y = 400 - y;
	    return new IntPair(x, y);
	}
	/**
	 * Convert a coordinate into map id
	 * @param coord the coordinate
	 * @return the map id.
	 */
    public static int coord2id(IntPair coord) {
        int id = (400 - coord.second) * 801 + coord.first + 401;
        return id;
    }
	/**
	 * 
	 * @author biminus
	 * A class for holding 2 elements.
	 * @param <T1> first
	 * @param <T2> second
	 */
    // TODO I would put this in the misc package [xtian]
	public static class Pair<T1, T2> {
	    public T1 first;
	    public T2 second;

	    public Pair(T1 t1, T2 t2) {
	        first = t1;
	        second = t2;
	    }

	    public String toString() {
	        return "(" + first + "," + second + ")";
	    }
	    
	    /**
	     * Pair<Integer, Integer> class for convenience. 
	     * @author biminus
	     *
	     */ 
	    public static class IntPair extends Pair<Integer, Integer> {
	        public IntPair(Integer first, Integer second) {
	            super(first, second);
	        }
	    }
	}
	
	public String getPatternString(String patternKey) {
		String patternString = null;
		// Pattern override, if any
		if (patternOverride!=null) {
			patternString = patternOverride.getString(patternKey, null);
			if (patternString!=null && patternString.trim().length()==0) {
				patternString = null; // Empty properties are the same as missing ones
			}
		}
		if (patternString==null) {
			// No override
			// log.debug("GAC before string.format:" + configManager.getHtmlPattern(patternKey, travianVersion));
			patternString = configManager.getHtmlPattern(patternKey, travianVersion);
			// log.debug("GAC patternString:" + patternString);
		}
		return patternString;
	}
	
	public Pattern getPattern(String patternKey, Object ... params) {
		String patternString = null;
		patternString=getPatternString(patternKey);
		patternString = String.format(patternString, params);
		return Pattern.compile(P_FLAGS + patternString);
	}
	
	/**
	 * version utility to use in action selection
	 * @author gac
	 * @deprecated
	 * @return true if version V35
	 */
	public Boolean isVersion35() {
		return travianVersion == TravianVersion.V35;
	}
	
	/**
	 * version utility to use in action selection
	 * @author gac
	 * @deprecated
	 * @return true if version V35 or above
	 */
	public Boolean isVersion35orAbove() {
		// GAC need to work out how to do this by time next version released!
		// return travianVersion != TravianVersion.V30;
		// try this
		return !isTravianVersionBelow(TravianVersion.V35);
	}
	
	/**
	 * version utility to use in action selection
	 * @author gac
	 * @param  TravianVersion travian version to test
	 * @return true if server version is below the parameter
	 */
	public Boolean isTravianVersionBelow(TravianVersion tVersion) {
		// GAC check if an older version to allow selection of new functionality
		// according to JavaDoc relies on versions being declared in order
		// log.debug("isTravianVersionBelow "+tVersion.compareTo(travianVersion));
		return (tVersion.compareTo(travianVersion) > 0) ;
	}

	public Boolean isTravianVersionAbove(TravianVersion tVersion) {
		// fofo check to change unit number in RallyPoint in V 3.6
		// I copied GAC's Below and changed from > to <
		// log.debug("isTravianVersionBelow "+tVersion.compareTo(travianVersion));
		return (tVersion.compareTo(travianVersion) < 0) ;
	}

	public void setTravianVersion(TravianVersion travianVersion) {
		this.travianVersion = travianVersion;
	}

	public TribeType getTribeType() {
		return tribeType;
	}

	public void setTribeType(TribeType tribeType) {
		this.tribeType = tribeType;
	}
	
	/**
	 * remove any xml or html formatting tags from string
	 * @param input
	 * @return modified string
	 */
	public String	stripTags(String input) {
		String	output="";
		// input.indexOf("<");
		output = input.replaceAll("\\<.*?>","");
		return output;
	}
	
	/**
	 * convert map http reference used by d= z= to x,y position
	 * @param id	map ref id
	 * @return		Point
	 */
	public Point getPositionFromMapId(int id) {
		int x, y;
		y = 400 - (id / 801);
		x = (id % 801) - 401;
		return new Point(x, y);
	}

	/**
	 * convert x,y position to map http reference id
	 * @param x position
	 * @param y	position
	 * @return	int id
	 */
	public static int getMapIdFromPosition(int x, int y) {
	// public static String getMapIdFromPosition(int x, int y) {
	// public String getMapIdFromPosition(int x, int y) {
		int id;
		// 92,95 returned answer for 92-95
		id = (x + 401) + ((400 - y) * 801);
    	log.debug("getMapId from " + x + "," + y + " returning " + id);
		return id;
		// return String.valueOf(id);
	}
	
	// public String getMapIdFromPosition(String x, String y) {
	public static int getMapIdFromPosition(String x, String y) {
		return getMapIdFromPosition(Integer.parseInt(x),Integer.parseInt(y));
	}
	
	private void failOnCaptcha(String page) throws ConversationException {
		if (isTravianVersionBelow(TravianVersion.V36)) {
			return;
		}
		try {
			Pattern	p = getPattern("util.isCaptcha");
			Matcher m = p.matcher(page);
			if (m.find()) {
				EventLog.log("evt.captchaFound", Util.class);
				this.saveTestPattern("util.isCaptcha", p, page);
				BaseCaptchaHandler.handleCaptcha(page, serverConfiguration);
			}
			p = getPattern("util.isCaptcha2");
			m = p.matcher(page);
			if (m.find()) {
				EventLog.log("evt.captchaFound", Util.class);
				this.saveTestPattern("util.isCaptcha2", p, page);
				BaseCaptchaHandler.handleCaptcha(page, serverConfiguration);
			}
			p = getPattern("util.isCaptcha3");
			m = p.matcher(page);
			if (m.find()) {
				EventLog.log("evt.captchaFound", Util.class);
				this.saveTestPattern("util.isCaptcha3", p, page);
				BaseCaptchaHandler.handleCaptcha(page, serverConfiguration);
			}
		} catch (ConversationException e) {
			e.printStackTrace();
			throw new ServerFatalException("Captcha found");
		}
	}
	
    private void utilGotoSecondPage(String currentUrl) {
    	String tempUrlString=getFullUrl(currentUrl, "dorf2.php");
		log.debug("using:"+tempUrlString+" not:"+currentUrl);
    	try {
    		httpGetPageNoFix(tempUrlString, true);
		} catch (ConversationException e) {
			log.debug("error getting page "+tempUrlString+", retrying");
	    	try {
	    		httpGetPageNoFix(tempUrlString, true);
			} catch (ConversationException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
    }
    private void utilGotoMainPage(String currentUrl) {
    	String tempUrlString=getFullUrl(currentUrl, "dorf1.php");
		log.debug("using:"+tempUrlString+" not:"+currentUrl);
    	try {
			httpGetPageNoFix(tempUrlString, true);
		} catch (ConversationException e) {
			log.debug("error getting page "+tempUrlString+", retrying");
	    	try {
				httpGetPageNoFix(tempUrlString, true);
			} catch (ConversationException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
    }
    
    private void accessOrderFix (String urlString) {
		Pattern p;
		Matcher m;
		
		log.trace("fix moving from:"+previousUrl+" to:"+urlString);
		// check for dorf1/dorf2 with newdid
		if (previousUrl != null) {
			// extract the dorf id and newdid if any
			p = Pattern.compile(P_FLAGS+"dorf(\\d+)");
			m = p.matcher(previousUrl);
			if (m.find()) {
				int pId = Integer.parseInt(m.group(1));
				m = p.matcher(urlString);
				if (m.find()) {
					int uId = Integer.parseInt(m.group(1));
					// both dorf - check if new page has newdid
					if (uId != pId) {
						log.trace(pId+" not same page as "+uId);
						p = Pattern.compile(P_FLAGS+"newdid=(\\d+)");
						m = p.matcher(urlString);
						if (m.find()) {
							// go via appropriate page
							if (uId == 2) {
								log.debug("Go via Centre Page");
								this.utilGotoSecondPage(urlString); //trying to go to building site from somewhere other than 
							} else {
								log.debug("Go via Overview Page");
								this.utilGotoMainPage(urlString); //trying to go to field site from somewhere other than 
							}
						} else {
							return; //ok to go to same page from new page							
						}
					}
				}
			}
		}
		
		
		p = Pattern.compile(P_FLAGS+"dorf1");
		m = p.matcher(urlString);
		if (m.find()) {
			return; //ok to go to FIRST_PAGE FROM ANYWHERE
		}
		p = Pattern.compile(P_FLAGS+"dorf2");
		m = p.matcher(urlString);
		if (m.find()) {
			return; //ok to go to SECOND_PAGE FROM ANYWHERE
		}
		p = Pattern.compile(P_FLAGS+"build\\.php\\?id=(\\d+)");
		m = p.matcher(urlString);
		if (m.find()) { //going into a build site
			Integer buildSite=Integer.valueOf(m.group(1));
			if (buildSite <= 18) { //going to BUILD_FIELD
				if ((previousPage==PreviousPage.BUILD_FIELD) && (previousBuilding==buildSite)) {
					log.trace("reloading Field:"+buildSite+" in:"+urlString);
					return; //reloading field, legal.
				}
				if (previousPage==PreviousPage.FIRST_PAGE) {
					log.trace("loading Field:"+buildSite+" in:"+urlString);
					return; //ok to fo from FIRST _PAGE to field site
				}
				log.debug("Go via Overview Page");
				this.utilGotoMainPage(urlString); //trying to go to field site from somewhere other than 
				return;                           //FIRST_PAGE, so going via FIRST_PAGE
			}
			
			if (buildSite >18) {
				if ((previousPage==PreviousPage.BUILD_BUILDING) && (previousBuilding==buildSite)) {
					log.trace("reloading Building:"+buildSite+" in:"+urlString);
					return; //reloading site, legal
				}
				if (previousPage==PreviousPage.SECOND_PAGE) {
					log.trace("loading Building:"+buildSite+" in:"+urlString);
					return; //ok to go from SECOND_PAGE to building
				}
				log.debug("Go via Centre Page");
				this.utilGotoSecondPage(urlString); //trying to go to building site from somewhere other than 
				return;                             //SECOND_PAGE, so going via SECOND_PAGE
			}
			log.trace("unknown previous page:"+urlString);
			return; //unknown previous page. 
		}
		log.trace("no fix:"+urlString);
		return; //no fixes found, so either its not neccesary, or its not programmed yet. fofo
	}
    
    private enum PreviousPage {
        UNKNOWN, FIRST_PAGE, SECOND_PAGE, BUILD_FIELD, BUILD_BUILDING
    } ;	// simple c like enum
    //private Map <PreviousPage, String> prevPageName = 
    private PreviousPage previousPage=PreviousPage.UNKNOWN;
    private int previousBuilding= 0;
    private	String	previousUrl = null;
    private void setPreviousPage (PreviousPage newPrevPage, int prevBuilding) {
    	previousPage=newPrevPage;
    	previousBuilding=prevBuilding;
    }
    
    private void accessOrderCheck (String urlString) {
		Pattern p;
		Matcher m;
		log.trace("check moving from:"+previousUrl+" to:"+urlString);
		previousUrl = urlString;
		//EventLog.log("urlString:"+urlString);
		p = Pattern.compile(P_FLAGS+"dorf1");
		m = p.matcher(urlString);
		String goingTo="";
		if (m.find()) {
				log.trace("FirstPage ok:"+urlString);
			setPreviousPage(PreviousPage.FIRST_PAGE, 0);
			return; //ok to go to FIRST_PAGE FROM ANYWHERE
		}
		p = Pattern.compile(P_FLAGS+"dorf2");
		m = p.matcher(urlString);
		if (m.find()) {
				log.trace("SecondPage ok:"+urlString);
			setPreviousPage(PreviousPage.SECOND_PAGE, 0);
			return; //ok to go to SECOND_PAGE FROM ANYWHERE
		}
		p = Pattern.compile(P_FLAGS+"build\\.php\\?id=(\\d+)");
		m = p.matcher(urlString);
		if (m.find()) { //going into a build site
			Integer buildSite=Integer.valueOf(m.group(1));
			if (buildSite <= 18) { //going to BUILD_FIELD
				goingTo="BUILD_FIELD";
				if (previousPage==PreviousPage.SECOND_PAGE) {
					EventLog.log("*************** going from SECOND_PAGE to "+goingTo+" **********");
				}
				if ((previousPage==PreviousPage.BUILD_FIELD) && (previousBuilding!=buildSite)) {
					EventLog.log("*************** going from BUILD_FIELD to different "+goingTo+" **********");
				}
				if (previousPage==PreviousPage.BUILD_BUILDING) {
					EventLog.log("*************** going from BUILD_BUILDING to "+goingTo+" **********");
				}
				setPreviousPage(PreviousPage.BUILD_FIELD, buildSite);
				return;
			}
			
			if (buildSite >18) {
				goingTo="BUILD_BUILDING";
				if (previousPage==PreviousPage.FIRST_PAGE) {
					EventLog.log("*************** going from FIRST_PAGE to "+goingTo+" **********");
				}
				if (previousPage==PreviousPage.BUILD_FIELD) {
					EventLog.log("*************** going from BUILD_FIELD to "+goingTo+" **********");
				}
				if ((previousPage==PreviousPage.BUILD_BUILDING) && (previousBuilding!=buildSite)) {
					EventLog.log("*************** going from BUILD_BUILDING to different "+goingTo+" **********");
				}
				setPreviousPage(PreviousPage.BUILD_BUILDING, buildSite);
				return;
			}
			return; //ok to go to SECOND_PAGE FROM ANYWHERE
		}
	}
}

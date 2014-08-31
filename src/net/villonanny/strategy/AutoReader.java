package net.villonanny.strategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import net.villonanny.ConversationException;
import net.villonanny.EventLog;
import net.villonanny.InvalidConfigurationException;
import net.villonanny.ReportMessageReader;
import net.villonanny.TimeWhenRunnable;
import net.villonanny.Util;
import net.villonanny.ReportMessageReader.EventPage;
import net.villonanny.ReportMessageReader.Report;
import net.villonanny.ReportMessageReader.ReportType;
import net.villonanny.entity.Valley;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class AutoReader extends ServerStrategy {
	// <serverStrategy class="AutoReader" enabled="true" uid="ar01">
	// 	<output append="true" format="log+event+csv+trade+attack+rein+scout" />
	// 	<delete type="trade" /> 
	// </serverStrategy>
    private final static Logger log = Logger.getLogger(AutoReader.class);
    private static final Integer		PAGES_PER_TIME = 3;
    private static final Integer		REPORTS_PER_PAGE = 10;
	
	private int		lastRead = 0;		// number read last time run
	private String	reportMode = "false";			// control if reading reports

	// may need to move more or alternative method to pass to ReportMesageReader
    private String storeReports = null;		// store in persistent store only set reports to false
	private	ReportMessageReader reader = new ReportMessageReader();
	
    private String	storedUrl = null;	// url to use in send/read
    private Util	storedUtil = null;		// for local access
    private Util	storedUserName = null;		// for local access

    private Boolean firstTime = true;	// flag to indicate first run
    private	static int	serverCount = 0;
	
	public TimeWhenRunnable execute() throws ConversationException, InvalidConfigurationException {
        log.info("Executing strategy " + super.getDesc());
        NDC.push(super.getDesc());
        try {
        	// ReportMessageReader reader = ReportMessageReader.getInstance();
        	// ReportMessageReader reader = new ReportMessageReader();
    		String	loginUrl = this.util.getServer().getLoginUrl();
			Long minPauseMinutes = config.getLong("/@minPauseMinutes", 60);
			Long nextPauseMinutes = minPauseMinutes;

    		reportMode = config.getString("/reports", null);					// simple format to just read
    		reportMode = config.getString("/reports/@enabled", reportMode);	// complex config with processing
    		reportMode = config.getString("/@enabled", reportMode);	// complex config with processing
    		reportMode = config.getString("/output/@format", reportMode);	// complex config with processing

    		if (firstTime) {
    			EventLog.log(loginUrl+" reportMode="+reportMode);
    			serverCount++;
    			log.debug(serverCount+" Servers reading reports");
    			// when should we reload the command table?
    			// need a reconfigure method?
    			// ReportMessageReader.getInstance().setCommands(config);
    		}
    		// set mode and store any commands
			// ReportMessageReader.getInstance().setReportsMode(reportMode, config);			
			reader.setReportsMode(reportMode, config);			
    		
    		
			int moreRead = 0;
			log.debug("reportMode("+reportMode+") lastRead="+lastRead);
			// gac add report processing
			if ((reportMode != null) && !reportMode.equals("false")) {
				// set the reporting mode and check reports
				// ReportMessageReader.getInstance().setReportsMode(reportMode);
				// EventLog.log(loginUrl+" about to read reportMode="+reportMode);
				moreRead = reader.readReports(util, loginUrl, reportMode);
				if (moreRead > (REPORTS_PER_PAGE*PAGES_PER_TIME)) {
	        		// minimum delay
		        	nextPauseMinutes = 1L;					
					log.debug("More to read");					
				} else {
					log.info("Reports done");					
				}
				lastRead = moreRead;
			}
    		// return timeWhenRunnable.NEVER;
			return new TimeWhenRunnable(System.currentTimeMillis() + (nextPauseMinutes * Util.MILLI_MINUTE), false); // Try again later

        } finally {
            NDC.pop();
        }
	}


}

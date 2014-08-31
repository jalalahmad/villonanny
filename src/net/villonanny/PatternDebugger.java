package net.villonanny;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

public class PatternDebugger {
	private final static Logger log = Logger.getLogger(PatternDebugger.class);
	private Pattern pattern;
	private String page;
	private String desc;
	
	public PatternDebugger(String filePath) throws IOException {
		readFromFile(filePath);
	}
	
	public PatternDebugger(String desc, Pattern pattern, String page) {
		this.desc = desc;
		this.pattern = pattern;
		this.page = page;
	}
	
	public void toFile(String outputDir) {
		String outputFilename = "match" +  pattern.toString().hashCode() + ".txt";
		// Globally synchronized to avoid file corruption when writing concurrently from different "servers"
		// Performance is not an issue
		synchronized (PatternDebugger.class) {
			try {
				File outDirFile = new File(outputDir);
				if (!outDirFile.exists()) {
					log.debug("Creating directory " + outDirFile.getAbsolutePath());
					outDirFile.mkdirs();
				}
				String fullPath = outputDir + "/" + outputFilename;
				PrintWriter out = new PrintWriter(new File(fullPath), Util.getEncodingString());
				out.println(new Date());
				out.println(desc);
				out.println(pattern.toString());
				out.println(page);
				out.close();
				log.debug("Pattern matching info dumped to file " + fullPath);
			} catch (IOException e) {
				log.error("Can't output pattern debug info", e);
			}
		}
	}
	
	private void readFromFile(String filePath) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));
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
		StringBuffer pageBuffer = new StringBuffer();
		line = in.readLine();
		while (line!=null) {
			pageBuffer.append(line + "\n");
			line = in.readLine();
		}
		in.close();
		this.page = pageBuffer.toString();
	}
	
	public Matcher getMatcher() {
		return this.pattern.matcher(page);
	}

}

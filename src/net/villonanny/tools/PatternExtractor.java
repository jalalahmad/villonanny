package net.villonanny.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Extract all v30 patterns from sources
 * (still working on it)
 *
 */
public final class PatternExtractor {
	static final Logger log = Logger.getLogger(PatternExtractor.class);
	
	private static final String START_DIR = "./src";
	private final File patternFile = new File("./src/net/villonanny/htmlPatterns.v30.new");
	private Writer patternWriter;
	private int patterNum = 0;

	public static void main(String[] args) throws IOException {
		// TODO mettere un avvertimento
		PatternExtractor patternExtractor = new PatternExtractor();
		patternExtractor.execute();
	}
	
	private PatternExtractor() {
	}
	
	private void execute() throws IOException {
		patternWriter = new FileWriter(patternFile);
		patternWriter.append("# HTML patterns for Travian v3.0\n");
		patternWriter.append("# No need for escaping quotes, but still need the double backslash \\\\\n");
		patternWriter.append("\n");
		patternWriter.append("# Include common definitions\n");
		patternWriter.append("include = htmlPatterns.common.properties\n");
		traverse(new File(START_DIR));
		patternWriter.close();
	}
	
    private void traverse(File element) {
    	if (element.isDirectory()) {
    		System.out.println("Traversing " + element);
    		File[] children = element.listFiles();
            for (int i=0; i<children.length; i++) {
                traverse(children[i]);
            }
    	} else {
    		try {
				processFile(element);
			} catch (IOException e) {
				System.err.println("Failed to process file " + quote(element.getAbsolutePath()));
				e.printStackTrace();
			}
    	}
    }

	private String quote(String absolutePath) {
		return "\"" + absolutePath + "\"";
	}

	private void processFile(File element) throws IOException {
		System.out.println("Processing " + element);
		if (element.getName().toLowerCase().endsWith(".java")) {
			// Load the content
			boolean filePrinted = false;
			RandomAccessFile inStream = new RandomAccessFile(element, "rw");
			FileChannel channel = inStream.getChannel();
			ByteBuffer bb = channel.map(FileChannel.MapMode.READ_ONLY, 0, (int)channel.size());
//			ByteBuffer bb = ByteBuffer.allocate((int)element.length());
//			channel.read(bb);
			Charset cs = Charset.forName("8859_1");
			CharsetDecoder cd = cs.newDecoder();
			CharBuffer cb = cd.decode(bb);
			Pattern p = Pattern.compile("(?s)Pattern.compile\\((.*?)\"?\\);");
			Matcher m = p.matcher(cb);
			while (m.find()) {
				if (!filePrinted) {
					patternWriter.append("\n# " + element.getName() + "\n");
					filePrinted = true;
				}
				String found = m.group(1);
				found = removeFirstQuote(found); 
				found = unescapeQuotes(found);
				found = removeFlags(found);
				found = replaceVariables(found);
				patternWriter.append(fileToClassname(element) +"." + patterNum + " = " + found + "\n");
				System.out.println(found);
				patterNum++;
			}
			channel.close();
		}
		
	}
	
	private String replaceVariables(String found) {
		return found.replaceAll("\"\\+ *[^\"+]*(?:\\+\"|$)", "%s");
	}

	private String removeFlags(String found) {
		found = found.replace("(?s)", "");
		found = found.replace("(?i)", "");
		found = found.replace("(?u)", "");
		found = found.replaceAll("Util.P_FLAGS[^+]*\\+ *", "");
		return found;
	}

	private String fileToClassname(File file) {
		final String ext = ".java";
		String filename = file.getName();
		if (filename.endsWith(ext)) {
			filename = filename.substring(0, filename.length() - ext.length());
		}
//		int pos = filename.indexOf('/');
//		if (pos > -1) {
//			filename = filename.substring(pos+1);
//		}
		filename = filename.substring(0, 1).toLowerCase() + filename.substring(1); // Lowercase fist letter
		return filename;
	}
	
	private String removeFirstQuote(String val) {
		try {
			int pos = val.indexOf('"');
			return val.substring(0, pos) + val.substring(pos+1);
		} catch (Exception e) {
			return val;
		} 
	}

	private String unescapeQuotes(String val) {
		return val.replaceAll("\\\\\"", "\""); // Replace \" with "
	}


}

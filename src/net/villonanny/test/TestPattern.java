package net.villonanny.test;

import java.io.File;
import java.util.regex.Matcher;

import net.villonanny.PatternDebugger;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;

/**
 * Test a pattern against an html page.
 * Pattern and html page are stored in a file (matchfile), in a directory specified by the <patternDebug path="<file_path>"> tag 
 *
 */
public class TestPattern {
	private static XMLConfiguration mainConfig;

	public static void main(String[] args) throws Exception {
		mainConfig = new XMLConfiguration("configuration.xml");
		mainConfig.setExpressionEngine(new XPathExpressionEngine());
		String inputDir = mainConfig.getString("patternDebug/@path"); 
		if (args.length==0) {
			System.out.println("Arguments:");
			System.out.println(" <matchfile> either absolute or relative to \"" + inputDir + "\"");
			System.out.println(" [<group> [<group>] ...] list of groups to display (integers)");
			return;
		}
		File inFile = new File(args[0]);
		if (!inFile.exists()) {
			inFile = new File(inputDir + "/" + args[0]);
		}
		PatternDebugger patternDebugger = new PatternDebugger(inFile.getAbsolutePath());
		Matcher m = patternDebugger.getMatcher();
		if (!m.find()) {
			System.out.println("Match failed");
			return;
		}
		System.out.println("Match ok");
		int i=1;
		while (args.length > i) {
			String groupString = args[i++];
			int group = Integer.parseInt(groupString);
			System.out.println("Group " + group + " = " + m.group(group));
		}
	}

}

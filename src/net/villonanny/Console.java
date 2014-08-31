package net.villonanny;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.villonanny.entity.Server;
import net.villonanny.entity.SkipRequested;
import net.villonanny.entity.SkipVillageRequested;
import net.villonanny.entity.Village;
import net.villonanny.strategy.Strategy;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

public class Console extends Thread {
	private static final Logger log = Logger.getLogger(Console.class);
	private static final String CMD_RESUME = "resume";
	private static final String CMD_SKIP = "skip";
	private static final String CMD_UNSKIP = "unskip";
	private static final String CMD_QUIT = "quit";
	private static final String CMD_HELP = "help";
	private static final String CMD_AWAKE = "awake";
	private static final String CMD_LIST = "list";
	private static final String CMD_LISTCURRENT = "listcurrent";
//	private static final String CMD_RELOAD = "reload";
	private final Map<String, String> help = new HashMap<String, String>();
	private boolean keepRunning = true;
	private final String KEY_MENU = ""; // <Enter>
	private final String KEY_SKIP = "s";
	private final String KEY_SKIPVILLAGE = "sv";
	private boolean pause = false;
	private boolean skipAction = false;
	private boolean skipVillage = false;
	private ConfigManager configManager;
//	private static final Console instance = new Console();
	private List<Server> serverList = new ArrayList<Server>();
	private final String QUICK_HELP="\n" +
		"Console enabled\n" + 
		"Press <Enter> to activate console\n" + 
		"Press s<Enter> to skip current action\n" + 
		"Press sv<Enter> to skip current village";

//	public static void main(String[] args) throws InterruptedException {
//		Console c = new Console();
//		c.start();
//		c.join();
//	}
	
	public Console(ConfigManager newConfigManager) {
		this.configManager = newConfigManager;
		help.put(CMD_RESUME, "resume operations");
		help.put(CMD_AWAKE, "awake all servers from sleep and resume");
		help.put(CMD_SKIP, "skip current/next action");
		help.put(CMD_UNSKIP, "do not skip current/next action");
		help.put(CMD_LIST, "list elements");
		help.put(CMD_LISTCURRENT, "list elements not waiting for something");
//		help.put(CMD_RELOAD, "reload configuration and resume");
		help.put(CMD_HELP, "this help");
		help.put(CMD_QUIT, "quit " + VilloNanny.class.getSimpleName());
	}
	
	public boolean isQuitting() {
		return !isKeepRunning();
	}

	public void run() {
		EventLog.log(QUICK_HELP);
		NDC.push(this.getClass().getSimpleName());
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			while (isKeepRunning()) {
				try {
					String line = in.readLine();
					if (KEY_MENU.equals(line)) {
						log.debug("Console activated - execution paused");
						consoleOn();
					} else if (KEY_SKIP.equals(line)) {
						skipVillage=false;
						skipAction=true;
						println("Current or next action will be skipped");
					} else if (KEY_SKIPVILLAGE.equals(line)) {
						skipVillage=true;
						skipAction=false;
						println("Current or next village will be skipped");
					} else {
						continue;
					}
				} catch (IOException e) {
					log.debug("Can't read input", e);
					EventLog.log("Error reading console input. Sleeping 1 minute...");
					Util.sleep(60000); // 60 seconds sleep
					// Ignore
				}
//				Util.sleep(1000);	
			}
		} finally {
			NDC.pop();
		}
	}
	
	private void consoleOn() throws IOException {
		suspendAll();
		println("\nOperations suspended. Entering console...");
		println("Type 'help' for help, type 'resume' to resume operations");
		if (skipAction) {
			println("skip is active; type 'noskip' to clear");
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String[] command = null;
		try {
			command = userCommand(in);
			while (!command[0].equalsIgnoreCase(CMD_RESUME)) {
				if (command[0].equalsIgnoreCase(CMD_HELP)) {
					help();
				} else if (command[0].equalsIgnoreCase(CMD_QUIT)) {
					quit();
					break;
				} else if (command[0].equalsIgnoreCase(CMD_AWAKE)) {
					// Need to resume before awakening, or threads will block
					resumeAll();
//					String desc = null;
//					if (command.length>1) {
//						desc=command[1];
//					}
					for (Thread server : serverList) {
						server.interrupt();
//						if (desc==null || server.getServerDesc().equalsIgnoreCase(desc)) {
//							server.awake();
//						} 
					}
					EventLog.log("All servers awakened.");
					break;
				} else if (command[0].equalsIgnoreCase(CMD_SKIP)) {
					skipAction=true;
					println("Skipping next action");
				} else if (command[0].equalsIgnoreCase(CMD_UNSKIP)) {
					skipAction=false;
					println("Skipping cleared");
				} else if (command[0].equalsIgnoreCase(CMD_LIST)) {
					list();
				} else if (command[0].equalsIgnoreCase(CMD_LISTCURRENT)) {
					listCurrent();
//				} else if (command[0].equalsIgnoreCase(CMD_RELOAD)) {
//					// Just do nothing and break (same as resume)
//					// DO NOT ConfigManager.reloadIfChanged();
//					break;
				} else {
					println("?");
				}
				command = userCommand(in);
			}
		} finally {
			resumeAll();
			if (command!=null && !command[0].equalsIgnoreCase(CMD_QUIT)) {
				EventLog.log("Resuming...");
				EventLog.log(QUICK_HELP);
				// Do not force reload: for some reason listeners are not notified
				// configManager.reloadIfChanged();
				configManager.getString("/", null); // Just to trigger config reload immediately
			}
		}
	}
	
	private void list() {
		Map<String, Server> allServers = VilloNanny.getInstance().getAllServers();
		for (Server server : allServers.values()) {
			boolean enabled = server.isEnabled();
			println(server.getServerDesc() + (enabled?" - active":" (disabled)"));
			for (Village village : server.getVillages()) {
				println(" Village: " + village.getDesc() + (enabled?" - active":" (disabled)"));
				// only report strategies if enabled - getTimeWhenRunnable() not safe if not running
				if (enabled) {
					for (Strategy strategy : village.getStrategies()) {
						// enabled = village.isEnabled();
						log.debug(server.getServerDesc()+" ("+village.getDesc()+") strategy "+strategy.getDesc());
						println("  Strategy: " + strategy.getDesc() + " waiting until "+strategy.getTimeWhenRunnable());
					}					
				}
			}
		}
	}

	private void listCurrent() { //list all villages with valid timewhenrunnable, i.e. those not waiting for something, or have ended
		Map<String, Server> allServers = VilloNanny.getInstance().getAllServers();
		for (Server server : allServers.values()) {
			boolean enabled = server.isEnabled();
			println(server.getServerDesc() + (enabled?" - active":" (disabled)"));
			for (Village village : server.getVillages()) {
				println(" Village: " + village.getDesc() + (enabled?" - active":" (disabled)"));
				// only report strategies if enabled - getTimeWhenRunnable() not safe if not running
				if (enabled) {
					for (Strategy strategy : village.getStrategies()) {
						// enabled = village.isEnabled();
						TimeWhenRunnable timeWhenRunnable = strategy.getTimeWhenRunnablePassive(); //get the actual timewhenRunnable variable stored in Strategy
						long now = new Date().getTime();
						if (timeWhenRunnable!=null) {
							if (timeWhenRunnable.getTime() < (now + 24 * (Util.MILLI_HOUR))) {
								log.debug(server.getServerDesc() + " ("
										+ village.getDesc() + ") strategy "
										+ strategy.getDesc());
								println("  Strategy: " + strategy.getDesc()
										+ " waitting until "
										+ strategy.getTimeWhenRunnable());
							}
						}
					}					
				}
			}
		}
	}

	private String[] userCommand(BufferedReader in) throws IOException {
		print("> ");
		String line = in.readLine();
		if (line!=null && line.trim().length()>0) {
			return line.split(" +");
		} else {
			return new String[] {""};
		}

	}
	
	private void quit() {
		log.info("Quit command issued");
		// NDC.remove();
//		System.exit(1);
		terminate();
		List<Server>clone = new ArrayList<Server>(serverList);
		for (Server server : clone) {
			server.setEnabledAndStartStop(false);
		}
	}

	private void help() {
		int size = 0;
		for (String cmd : help.keySet()) {
			int l = cmd.length();
			if (l>size) {
				size=l;
			}
		}
		for (String cmd : help.keySet()) {
			String h = help.get(cmd);
			println(String.format("%-"+size+"s : %s", cmd, h));
		}		
	}

	private void println(String s) {
		System.out.println(s);
	}

	private void print(String s) {
		System.out.print(s);
	}

	private synchronized void suspendAll() {
		pause=true;
	}
	
	private synchronized void resumeAll() {
		pause=false;
		notifyAll(); // awakens all threads blocked in the checkPause() wait()
	}
	
	public synchronized void checkPause() {
		if (pause) {
//			Thread thread = Thread.currentThread();
//			pausedThreads.add(thread);
			try {
				log.debug("Pausing...");
				wait();
			} catch (InterruptedException e) {
				// Nothing to do
			}
			log.debug("...resuming after pause");
		}
	}
	
	public synchronized void checkSkip() throws SkipVillageRequested, SkipRequested {
		if (skipVillage) {
			skipAction=false;
			skipVillage=false;
			EventLog.log("Skipping village");
			throw new SkipVillageRequested();
		}
		if (skipAction) {
			skipAction=false;
			skipVillage=false;
			EventLog.log("Skipping action");
			throw new SkipRequested();
		}
	}
	
	public synchronized void terminate() {
		// Must be synchronized because the attribute is accessed from different threads
		keepRunning=false;
		this.interrupt();
		resumeAll();
	}
	
	public void checkFlags() {
		checkSkip();
		checkPause();
	}

	public synchronized void addServerThread(Server server) {
		// Must be synchronized because the attribute is accessed from different threads
		serverList.add(server);
	}

	public synchronized void removeServerThread(Server server) {
		// Must be synchronized because the attribute is accessed from different threads
		serverList.remove(server);
		if (serverList.size()==0) {
			log.info("All servers stopped");
			terminate();
		}
	}

	public synchronized boolean isKeepRunning() {
		// Must be synchronized because the attribute is accessed from different threads
		return keepRunning;
	}

}

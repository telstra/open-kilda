package org.bitbucket.kilda.controller;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

//import org.apache.zookeeper.KeeperException;
//import org.apache.zookeeper.WatchedEvent;
//import org.apache.zookeeper.Watcher;
//import org.apache.zookeeper.ZooKeeper;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

// NB: Using log4j directly in Main to be able to print out some information that is
//		not available in the SLF4J API (ie get log level)
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main is the class that kicks off the controller.
 *
 * The controller starts with reaching out to Zookeeper. That is a critical
 * piece of the platform and must be in place for anything real to happen.
 *
 */
public class Main {
	// private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private static final Logger logger = LogManager.getLogger(Main.class);
	private static Context context; 
	
	public static final int ZK_DEFAULT_PORT = 2181;
	public static final int KAFKA_DEFAULT_PORT = 9092;

	private static void Initialize(String[] args) {
		Thread.currentThread().setName("Kilda.Main");
		logger.info("INITIALIZING Kilda");
		logger.info("logger level: " + logger.getLevel());
		logger.debug("args count: " + args.length);
		logger.debug("args value: " + Arrays.toString(args));
		// String hostPort = args[0];
		// String znode = args[1];
		context = new Context();
	}

	private static void Startup() {
		logger.info("STARTING Kilda..");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				Main.Shutdown();
			}
		});
		// new Executor(hostPort, znode, filename,
		// exec).run();
	}

	/**
	 * NB: Currently, this isn't called during a normal exit, only during a shutdown event.
	 */
	private static void Shutdown() {
		logger.info("EXITING Kilda");
	}

	public static void main(String[] args) {
		try {
			Main.Initialize(args);
			Main.Startup(); 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

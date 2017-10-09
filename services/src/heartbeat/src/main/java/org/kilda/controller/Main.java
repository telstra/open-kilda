/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.bitbucket.kilda.controller;

import java.util.Arrays;

import javax.inject.Inject;

//import org.apache.zookeeper.KeeperException;
//import org.apache.zookeeper.WatchedEvent;
//import org.apache.zookeeper.Watcher;
//import org.apache.zookeeper.ZooKeeper;

// NB: Using log4j directly in Main to be able to print out some information that is
//		not available in the SLF4J API (ie get log level)
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitbucket.kilda.controller.guice.module.YamlConfigModule;
import org.bitbucket.kilda.controller.heartbeat.Heartbeat;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Main is the class that kicks off the controller.
 *
 * The controller starts with reaching out to Zookeeper. That is a critical
 * piece of the platform and must be in place for anything real to happen.
 *
 */
public class Main {
	
	private static final Logger logger = LogManager.getLogger(Main.class);
	
	private static Context context;

	public static final int ZK_DEFAULT_PORT = 2181;
	public static final int KAFKA_DEFAULT_PORT = 9092;

	@Inject
	private Heartbeat hb;
	
;
	
	private static void Initialize(String[] args) {
		Thread.currentThread().setName("Kilda.Main");
		logger.info("INITIALIZING Kilda");
		logger.info("logger level: " + logger.getLevel());
		logger.debug("args count: " + args.length);
		logger.debug("args value: " + Arrays.toString(args));
		// NB: This is a poor mans config parser. It should move to something
		// more robust.
		String configfile = "";
		for (String arg : args) {
			if (arg.startsWith("--config")) {
				configfile = arg.substring(arg.lastIndexOf("=") + 1);
			}
		}
		logger.debug("configfile: " + configfile);
		context = new Context(configfile);
	}

	private  void Startup() {
		logger.info("STARTING Kilda..");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Main.Shutdown();
			}
		});
 
		hb.start();
	}

	/**
	 * NB: Currently, this isn't called during a normal exit, only during a
	 * shutdown event.
	 */
	private static void Shutdown() {
		logger.info("EXITING Kilda");
		// Thread.
	}

	public static void main(String[] args) {
		try {
			Main.Initialize(args);
			Injector injector = Guice.createInjector(new YamlConfigModule(System.getProperty("controller.config.overrides.file")));
			
			Main main = injector.getInstance(Main.class);
			main.Startup();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}

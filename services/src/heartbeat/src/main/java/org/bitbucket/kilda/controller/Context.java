package org.bitbucket.kilda.controller;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Context - manage the various settings that are important for the controller
 * to function.
 */
public class Context {
	private static final Logger logger = LoggerFactory.getLogger(Context.class);
	private static final String DEFAULT_PROPS = "/kilda-defaults.properties";

	private String overrides;
	private Properties props = new Properties();

	public Context() {
		this.intialize();
	}

	/**
	 * @param overrides
	 *            the path to a file that overrides some/all default values
	 */
	public Context(String overrides) {
		this.overrides = overrides;
		this.intialize();
	}

	/**
	 * @return the Properties. Only valid after initialize is called.
	 */
	public Properties getProps() {
		return props;
	}

	/*
	 * A helper function leveraged by @readFromResources and @readFromFile
	 */
	private static Properties readFromStream(InputStream in, Properties fromThis) {
		Properties props = new Properties(fromThis);
		try {
			props.load(in);
			in.close();
		} catch (IOException e) {
			logger.error("There was a problem loading the resource file.");
			e.printStackTrace();
		}
		logger.debug("Properties: " + props);
		return props;
	}

	/**
	 * readFromResource reads from the jar file, which has a different strategy
	 * compared to the direct filesystem
	 * 
	 * @param fromThis
	 *            The new Properties object will start from this
	 * @return new Properties, will the contents of the file overlaying fromThis
	 */
	private Properties readFromResource(String resourceFile, Properties fromThis) {
		logger.debug("Reading Properites From: " + resourceFile);
		return readFromStream(getClass().getResourceAsStream(resourceFile), fromThis);
	}

	/**
	 * readFromFile will open a file stream for Property input. Slightly
	 * different call compared to readFromResource.
	 * 
	 * @param propsFile
	 *            the name of the file to read.
	 * @param fromThis
	 *            the seed properties. The stuff in the file, if same name, will
	 *            overwrite.
	 * @return the combined set of properties from fromThis and the file.
	 */
	private Properties readFromFile(String propsFile, Properties fromThis) {
		try {
			logger.debug("Reading Properites From: " + propsFile);
			return readFromStream(new FileInputStream(propsFile), fromThis);
		} catch (FileNotFoundException e) {
			logger.error("Can't find property file " + propsFile);
			e.printStackTrace();
		}
		// Fail fast .. if the file isn't found, exit.
		System.exit(1);
		// Java doesn't know that exit will exit, so I need a return
		return fromThis;
	}

	private void intialize() {
		// create and load default properties
		props = readFromResource(DEFAULT_PROPS, props);
		if (overrides != null && overrides.length() > 0) {
			props = readFromFile(overrides, props);
		}
		logger.info("FINAL " + this);
	}

	@Override
	public String toString() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		props.list(ps);
		String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

		return "PROPERTIES: " + content;
	}

}

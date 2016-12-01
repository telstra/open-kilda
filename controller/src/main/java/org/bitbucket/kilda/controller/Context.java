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
import org.slf4j.event.Level;

/**
 * Context - manage the various settings that are important for the controller to function. 
 */
public class Context {
	private static final Logger logger = LoggerFactory.getLogger(Context.class);
	private static final String DEFAULT_PROPS =  "/kilda-defaults.properties";

	private String overrides;
	private Properties props = new Properties();
	
	public Context() {
		this.intialize();
	}

	/**
	 * @param overrides the path to a file that overrides some/all default values
	 */
	public Context(String overrides) {
		this.overrides = overrides;
		this.intialize();
	}


	/**
	 * readFromResource reads from the jar file, which has a different strategy
	 * compared to the direct filesystem
	 * 
	 * @param fromThis The new Properties object will start from this
	 * @return new Properties, will the contents of the file overlaying fromThis
	 */
	private Properties readFromResource(String resourceFile, Properties fromThis) {
		Properties resourceProps = new Properties(fromThis);

		try {
			InputStream in = getClass().getResourceAsStream(resourceFile); 						
			resourceProps.load(in);
			logger.debug("From: " +resourceFile+ " Properties: " + resourceProps);
			in.close();			
		} catch (FileNotFoundException e) {
			logger.error("Can't find resource property file " + resourceFile);
			e.printStackTrace();
		} catch (IOException e) {
			logger.error("There was a problem closing the resource properties file " + resourceFile);
			e.printStackTrace();
		}
		return resourceProps;
	}

	private Properties readFromFile(String propsFile, Properties fromThis) {
		Properties props = new Properties(fromThis);

		try {
			InputStream in = new FileInputStream(propsFile);
			
			props.load(in);
			logger.debug("From: " +propsFile+ " Properties: " + props);
			in.close();			
		} catch (FileNotFoundException e) {
			logger.error("Can't find property file " + propsFile);
			e.printStackTrace();
		} catch (IOException e) {
			logger.error("There was a problem closing the properties file " + propsFile);
			e.printStackTrace();
		}
		return props;
	}
	
	
	private void intialize() {
		// create and load default properties
		props = readFromResource(DEFAULT_PROPS, props);

		// TODO: add the read env variable
		String TEST = "src/test/resources/test.properties";
		props = readFromFile(TEST, props);
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

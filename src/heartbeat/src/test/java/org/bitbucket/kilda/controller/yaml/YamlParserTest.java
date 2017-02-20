package org.bitbucket.kilda.controller.yaml;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class YamlParserTest {
	
	private static final String TEST_DEFAULTS = "/kilda-defaults.yml";
	
	private static final String TEST_OVERRIDES = "kilda-overrides.yml";
	
	private static final String TOP = "test1";
	
	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();
	
	@Test
	public void loadAsMap() throws IOException {
		// Create a temporary file.
	    final File tempFile = tempFolder.newFile(TEST_OVERRIDES);
	   
	    // Write something to it.
	    FileUtils.writeStringToFile(tempFile, "test1:\n  string: bar\n  long: !!java.lang.Long 20\n  noDefaultString: noDefault", Charset.defaultCharset());

	    YamlParser parser = new YamlParser(tempFile.getAbsolutePath());
		parser.setDefaultsFilename(TEST_DEFAULTS);		
		
		Map<String, Object> map = parser.loadAsMap();
		
		// override "foo" with "bar"
		Object string  = map.get(TOP + ".string");
		assertNotNull(string);
		assertTrue(string instanceof String);
		assertEquals("bar", (String)string);
		
		// default 3
		Object integer = map.get(TOP + ".integer");
		assertNotNull(integer);
		assertTrue(integer instanceof Integer);
		assertEquals(new Integer(3), (Integer)integer);
		
		// override 1 with 20
		Object long1 = map.get(TOP + ".long");
		assertNotNull(long1);
		assertTrue(long1 instanceof Long);
		assertEquals(new Long(20), (Long)long1);
		
		// default of TRUE
		Object boolean1 = map.get(TOP + ".boolean");
		assertNotNull(boolean1);
		assertTrue(boolean1 instanceof Boolean);
		assertTrue((Boolean)boolean1);
		
		Object noDefaultString  = map.get(TOP + ".noDefaultString");
		assertNotNull(noDefaultString);
		assertTrue(noDefaultString instanceof String);
		assertEquals("noDefault", (String)noDefaultString);
	}

}

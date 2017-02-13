package org.bitbucket.kilda.controller.yaml;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class YamlParserTest {
	
	private static final String TEST_PROFILE = "test";
	
	private static final String TOP = "test1";
	
	private YamlParser parser;
	
	@Before
	public void before() {
		parser = new YamlParser(TEST_PROFILE);
	}
	
	@Test
	public void loadAsMap() {
		Map<String, Object> map = parser.loadAsMap();
		
		assertEquals(4, map.size());
		
		Object string = map.get(TOP + ".string");
		assertNotNull(string);
		assertTrue(string instanceof String);
		assertEquals("foo", (String)string);
		
		Object integer = map.get(TOP + ".integer");
		assertNotNull(integer);
		assertTrue(integer instanceof Integer);
		assertEquals(Integer.valueOf(3), (Integer)integer);
		
		Object long1 = map.get(TOP + ".long");
		assertNotNull(long1);
		assertTrue(long1 instanceof Long);
		assertEquals(Long.valueOf(1), (Long)long1);
		
		Object boolean1 = map.get(TOP + ".boolean");
		assertNotNull(boolean1);
		assertTrue(boolean1 instanceof Boolean);
		assertTrue((Boolean)boolean1);
	}

}

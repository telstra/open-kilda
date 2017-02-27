package org.bitbucket.kilda.controller.yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class YamlParser {
	
	private static final Logger logger = LoggerFactory.getLogger(YamlParser.class);
	
	private static final String APPLICATION_DEFAULTS_FILENAME = "/kilda-defaults.yml";
	
	private String defaultsFilename;

	private final String overridesFilename;
	
	public YamlParser(String overridesFilename) {
		this.overridesFilename = overridesFilename;
	}
	
	public Map<String, Object> loadAsMap() {
		Map<String, Object> defaults = getDefaults();
		
		// no overrides to process
		if (overridesFilename == null) {
			logger.warn("no overrides file specified, loading default config only");
			return defaults;
		}
		
        return getOverridesAndDefaults(defaults);
	}
	
	private Map<String, Object> getOverridesAndDefaults(Map<String, Object> defaults) {
		Map<String, Object> overrides = getOverrides();
		
		Map<String, Object> map = new HashMap<String, Object>();
		
		map.putAll(overrides);
		
		for (String key : defaults.keySet()) {
			if (!overrides.containsKey(key)) {
			    map.put(key, defaults.get(key));	
			}
		}
		
		return map;		
	}
	
	private Map<String, Object> getOverrides() {
		try {
			Yaml  yaml = new Yaml();
			Object object = yaml.load(new FileInputStream(overridesFilename));
			return getFlattenedMap(asMap(object));	
		} catch (FileNotFoundException e) {
			throw new RuntimeException("can't read file " + overridesFilename);			
		}				
	}

	private Map<String, Object> getDefaults() {
		Yaml  yaml = new Yaml();
		Object object = yaml.load(ClassLoader.class.getResourceAsStream(getDefaultsFilename()));	
		return getFlattenedMap(asMap(object));		
	}
	
	
	private final Map<String, Object> getFlattenedMap(Map<String, Object> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		buildFlattenedMap(result, source, null);
		return result;
	}
	
	private Map<String, Object> asMap(Object object) {
		// YAML can have numbers as keys
		Map<String, Object> result = new LinkedHashMap<>();
		if (!(object instanceof Map)) {
			// A document can be a text literal
			result.put("document", object);
			return result;
		}

		Map<Object, Object> map = (Map<Object, Object>) object;
		for (Entry<Object, Object> entry : map.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Map) {
				value = asMap(value);
			}
			Object key = entry.getKey();
			if (key instanceof CharSequence) {
				result.put(key.toString(), value);
			}
			else {
				// It has to be a map key in this case
				result.put("[" + key.toString() + "]", value);
			}
		}
		return result;
	}

	private void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
		for (Entry<String, Object> entry : source.entrySet()) {
			String key = entry.getKey();
			if (StringUtils.isNotBlank(path)) {
				if (key.startsWith("[")) {
					key = path + key;
				}
				else {
					key = path + '.' + key;
				}
			}
			Object value = entry.getValue();
			if (value instanceof String) {
				result.put(key, value);
			}
			else if (value instanceof Map) {
				// Need a compound key
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) value;
				buildFlattenedMap(result, map, key);
			}
			else if (value instanceof Collection) {
				// Need a compound key
				@SuppressWarnings("unchecked")
				Collection<Object> collection = (Collection<Object>) value;
				int count = 0;
				for (Object object : collection) {
					buildFlattenedMap(result,
							Collections.singletonMap("[" + (count++) + "]", object), key);
				}
			}
			else {
				result.put(key, (value != null ? value : ""));
			}
		}
	}
	
	String getDefaultsFilename() {
		if (defaultsFilename == null) {
		    setDefaultsFilename(APPLICATION_DEFAULTS_FILENAME);	
		}
		
		return defaultsFilename;
	}

	void setDefaultsFilename(String defaultsFilename) {
		this.defaultsFilename = defaultsFilename;
	}
	
}

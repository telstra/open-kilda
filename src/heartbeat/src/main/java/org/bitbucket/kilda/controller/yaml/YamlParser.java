package org.bitbucket.kilda.controller.yaml;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.yaml.snakeyaml.Yaml;

public class YamlParser {

	private final String profile;
	
	public YamlParser(String profile) {
		this.profile = profile;
	}
	
	public Map<String, Object> loadAsMap() {
		Yaml yaml = new Yaml();
		Object object = yaml.load(ClassLoader.class.getResourceAsStream("/application-" + profile + ".yml"));
	
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
	
}

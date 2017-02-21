package org.bitbucket.kilda.controller.guice.module;

import java.util.Map;
import org.bitbucket.kilda.controller.yaml.YamlParser;

import com.google.inject.AbstractModule;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.name.Names;

public class YamlConfigModule extends AbstractModule {
	
	private final YamlParser parser;
	
	public YamlConfigModule(String profile) {
		this.parser = new YamlParser(profile);
	}

	@Override
	protected void configure() {
		Map<String, Object> config = parser.loadAsMap();
		
		for (String name : config.keySet()) {
	        Object value = config.get(name);
	        
	        ConstantBindingBuilder builder = bindConstant().annotatedWith(Names.named(name));
	        if (value instanceof String) {
	    	    builder.to((String)value);	        	
	        } else if (value instanceof Integer) {
	        	builder.to((Integer)value);
	        }  else if (value instanceof Long) {
	        	builder.to((Long)value);	
	        }  else if (value instanceof Boolean) {
	        	builder.to((Boolean)value);	
	        } else {
	        	// TODO - throw more appropriate exception?
	        	throw new RuntimeException("don't know how to bind constant to value of type" + value.getClass());
	        }
		}
	}
	


}

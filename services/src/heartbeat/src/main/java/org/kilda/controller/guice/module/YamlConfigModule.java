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

package org.bitbucket.kilda.controller.guice.module;

import java.util.Map;
import org.bitbucket.kilda.controller.yaml.YamlParser;

import com.google.inject.AbstractModule;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.name.Names;

public class YamlConfigModule extends AbstractModule {
	
	private final YamlParser parser;
	
	public YamlConfigModule(String overridesFilename) {
		this.parser = new YamlParser(overridesFilename);
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

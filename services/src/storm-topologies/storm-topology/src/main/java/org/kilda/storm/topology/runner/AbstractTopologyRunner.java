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

package org.bitbucket.kilda.storm.topology.runner;

import org.bitbucket.kilda.storm.topology.guice.module.YamlConfigModule;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public abstract class AbstractTopologyRunner {

	public static void startup(Class<? extends AbstractTopologyRunner> klass, Module module)  {
		
		String overridesFile = null;
		if (System.getProperty("storm.topology.config.overrides.file") != null) {
			overridesFile = System.getProperty("storm.topology.config.overrides.file");	
		} else {
			overridesFile = klass.getResource("kilda-overrides.yml").getPath();
		}
		
		Injector injector = Guice
				.createInjector(new YamlConfigModule(overridesFile), module);

		AbstractTopologyRunner runner = injector.getInstance(klass);
        runner.run();
	}
	
	abstract protected void run();
	
}

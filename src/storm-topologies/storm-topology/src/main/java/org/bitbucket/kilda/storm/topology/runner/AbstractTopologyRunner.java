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

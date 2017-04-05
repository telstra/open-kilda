package org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module;

import org.bitbucket.kilda.storm.topology.guice.module.YamlConfigModule;
import org.bitbucket.kilda.storm.topology.switchevent.activated.runner.LocalTopologyRunner;

public class TestYamlConfigModule extends YamlConfigModule {

	public TestYamlConfigModule() {
		super(LocalTopologyRunner.class.getResource("kilda-overrides.yml").getPath());
	}

}

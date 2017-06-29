package org.bitbucket.kilda.storm.topology.switchevent.activated.runner;

import org.bitbucket.kilda.storm.topology.runner.AbstractLocalTopologyRunner;
import org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module.Module;

public class LocalTopologyRunner extends AbstractLocalTopologyRunner {

	public static void main(String[] args)  {		
		AbstractLocalTopologyRunner.startup(LocalTopologyRunner.class, new Module());
	}
	
}
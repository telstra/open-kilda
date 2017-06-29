package org.bitbucket.kilda.storm.topology.switchevent.activated.runner;

import org.bitbucket.kilda.storm.topology.runner.AbstractRemoteTopologyRunner;
import org.bitbucket.kilda.storm.topology.runner.AbstractTopologyRunner;
import org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module.Module;

public class RemoteTopologyRunner extends AbstractRemoteTopologyRunner {

	public static void main(String[] args)  {		
		AbstractTopologyRunner.startup(RemoteTopologyRunner.class, new Module());
	}
	
}
package org.bitbucket.kilda.storm.topology.runner;

import javax.inject.Inject;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.utils.Utils;

public abstract class AbstractLocalTopologyRunner extends AbstractTopologyRunner {

	private static final int TEN_MINUTES = 600000;
	
	@Inject
	private IStormTopologyBuilder builder;
	
	protected void run() {
		Config config = new Config();
		config.setDebug(true);

		LocalCluster cluster = new LocalCluster();
		cluster.submitTopology(builder.getTopologyName(), config, builder.build());

		Utils.sleep(TEN_MINUTES);
		cluster.killTopology(builder.getTopologyName());
		cluster.shutdown();		
	}
	
}
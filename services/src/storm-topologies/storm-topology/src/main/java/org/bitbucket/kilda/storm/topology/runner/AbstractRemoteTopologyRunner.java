package org.bitbucket.kilda.storm.topology.runner;

import javax.inject.Inject;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.AuthorizationException;
import org.apache.storm.generated.InvalidTopologyException;
import org.bitbucket.kilda.storm.topology.exception.StormException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractRemoteTopologyRunner extends AbstractTopologyRunner {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractRemoteTopologyRunner.class);
	
	@Inject
	private IStormTopologyBuilder builder;
	
	protected void run() {
		Config config = new Config();
		config.setNumWorkers(2);
		config.setMessageTimeoutSecs(120);

		try {
			StormSubmitter.submitTopology(builder.getTopologyName(), config, builder.build());
		} catch (AlreadyAliveException | InvalidTopologyException | AuthorizationException e) {
			String message = "could not submit topology" + builder.getTopologyName();
			logger.error(message, e);
			throw new StormException(message, e);
		}	
	}
	
}
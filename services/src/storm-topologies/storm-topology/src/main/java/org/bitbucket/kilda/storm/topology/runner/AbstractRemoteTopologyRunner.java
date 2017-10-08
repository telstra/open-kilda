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
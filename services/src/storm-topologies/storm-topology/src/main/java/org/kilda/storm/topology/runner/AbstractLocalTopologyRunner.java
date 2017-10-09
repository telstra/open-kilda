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

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

package org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module;

import javax.inject.Named;

import org.bitbucket.kilda.storm.topology.runner.IStormTopologyBuilder;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.IConfirmer;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.ICorrelator;
import org.bitbucket.kilda.storm.topology.switchevent.activated.runner.StormTopologyBuilder;
import org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs.IWebTargetFactory;
import org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs.WebTargetConfirmer;
import org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs.WebTargetCorrelator;
import org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs.WebTargetFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class Module extends AbstractModule {
	
	@Override
	protected void configure() {
		bind(IStormTopologyBuilder.class).to(StormTopologyBuilder.class);
		bind(IConfirmer.class).to(WebTargetConfirmer.class);
		bind(ICorrelator.class).to(WebTargetCorrelator.class);
	}
	
	@Provides
	@Named("OfsWebTarget")
	IWebTargetFactory ofsWebTarget(@Named("kafka.bolt.confirmation.ofsUri") String uri) {
		return new WebTargetFactory(uri);
	}
	
	@Provides
	@Named("TopologyEngineWebTarget")
	IWebTargetFactory topologyEngineWebTarget(@Named("kafka.bolt.correlation.topologyEngineUri") String uri) {
		return new WebTargetFactory(uri);
	}

}

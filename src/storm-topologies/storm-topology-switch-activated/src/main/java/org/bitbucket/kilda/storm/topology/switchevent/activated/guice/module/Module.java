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

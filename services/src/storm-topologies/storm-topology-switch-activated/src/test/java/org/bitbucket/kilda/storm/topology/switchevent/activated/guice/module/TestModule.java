package org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.IConfirmer;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.ICorrelator;

import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(IConfirmer.class).to(TrueConfirmer.class);		
		bind(ICorrelator.class).to(TrueCorrelator.class);
	}

}

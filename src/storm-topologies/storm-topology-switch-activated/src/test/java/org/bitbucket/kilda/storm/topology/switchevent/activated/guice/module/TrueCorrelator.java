package org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.ICorrelator;

public class TrueCorrelator implements ICorrelator {

	@Override
	public void prepare() {
	}

	@Override
	public boolean correlate(String switchId) {
		return true;
	}

}

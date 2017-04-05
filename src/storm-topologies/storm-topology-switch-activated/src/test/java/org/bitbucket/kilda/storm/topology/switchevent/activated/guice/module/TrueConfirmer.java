package org.bitbucket.kilda.storm.topology.switchevent.activated.guice.module;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.IConfirmer;

public class TrueConfirmer implements IConfirmer {

	@Override
	public void prepare() {
	}

	@Override
	public boolean confirm(String switchId) {
		return true;
	}

}

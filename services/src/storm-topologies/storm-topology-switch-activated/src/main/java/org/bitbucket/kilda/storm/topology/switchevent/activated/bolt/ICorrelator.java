package org.bitbucket.kilda.storm.topology.switchevent.activated.bolt;

import java.io.Serializable;

public interface ICorrelator extends Serializable {
	
	void prepare();
	
	boolean correlate(String switchId);

}

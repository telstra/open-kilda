package org.bitbucket.kilda.storm.topology.switchevent.activated.bolt;

import java.io.Serializable;

public interface IConfirmer extends Serializable {
	
	void prepare();
	
	boolean confirm(String switchId);

}

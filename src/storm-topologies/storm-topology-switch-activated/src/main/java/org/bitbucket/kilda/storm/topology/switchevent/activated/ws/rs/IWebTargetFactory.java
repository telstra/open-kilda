package org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs;

import java.io.Serializable;

import javax.ws.rs.client.WebTarget;

public interface IWebTargetFactory extends Serializable {
	
	WebTarget create();

}

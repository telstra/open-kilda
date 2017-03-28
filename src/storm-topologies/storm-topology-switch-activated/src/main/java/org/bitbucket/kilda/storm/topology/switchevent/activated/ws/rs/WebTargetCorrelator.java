package org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.Correlation;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.ICorrelator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebTargetCorrelator implements ICorrelator {

	private static final Logger logger = LoggerFactory.getLogger(WebTargetCorrelator.class);

	@Inject
	@Named("TopologyEngineWebTarget")
	private IWebTargetFactory webTargetFactory;
	
	private WebTarget webTarget;
	
	@Override
	public boolean correlate(String switchId) {		
		Response response = webTarget.path("correlate/").path(switchId).request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).get();
		if (response.getStatus() !=  Response.Status.OK.getStatusCode()) {
			logger.error("Topology engine response for correlation " + response);
			return false;
		}
		
		Correlation correlation = response.readEntity(Correlation.class);
		return correlation.isCorrelated();
	}

	@Override
	public void prepare() {
		webTarget = webTargetFactory.create();
	}

}

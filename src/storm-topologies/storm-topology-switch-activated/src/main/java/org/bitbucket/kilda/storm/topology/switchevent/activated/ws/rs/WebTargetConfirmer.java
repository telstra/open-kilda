package org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.Confirmation;
import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.IConfirmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
public class WebTargetConfirmer implements IConfirmer {

	private static final Logger logger = LoggerFactory.getLogger(WebTargetConfirmer.class);

	@Inject
	@Named("OfsWebTarget")
	private IWebTargetFactory webTargetFactory;
	
	private WebTarget webTarget;
	
	@Override
	public boolean confirm(String switchId) {
		WebTarget webTarget = webTargetFactory.create();
		
		Response response = webTarget.path("confirm/").path(switchId).request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).get();
		if (response.getStatus() !=  Response.Status.OK.getStatusCode()) {
			logger.error("OFS response from confirmation " + response);
			return false;
		}
		
		Confirmation confirmation = response.readEntity(Confirmation.class);
		return confirmation.isConfirmed();
	}

	@Override
	public void prepare() {
		webTarget = webTargetFactory.create();
	}

}

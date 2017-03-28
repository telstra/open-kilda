package org.bitbucket.kilda.storm.topology.switchevent.activated.mock.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.bitbucket.kilda.storm.topology.switchevent.activated.bolt.Confirmation;

@Path("confirm/{switchId}")
@Produces(MediaType.APPLICATION_JSON)
public class OfsResource {
	
	@GET    
    public Confirmation get(@PathParam("switchId") String switchId) {
        return new Confirmation(true);
    }

}

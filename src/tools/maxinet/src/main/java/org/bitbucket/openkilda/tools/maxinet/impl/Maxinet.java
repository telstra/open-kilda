package org.bitbucket.openkilda.tools.maxinet.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitbucket.openkilda.tools.maxinet.IMaxinet;
import org.bitbucket.openkilda.tools.maxinet.Topo;

@Named
public class Maxinet implements IMaxinet {

	private static final Logger logger = LoggerFactory.getLogger(Maxinet.class);
	
	@Inject
	private WebTarget webTarget;
	
	private Cluster cluster;
	
	private Topo topo;
	
	private Experiment experiment;
	
	@Override
	public IMaxinet cluster(String name, Integer minWorkers, Integer maxWorkers) {		
	    logger.debug("starting cluster " + this);
	    
        Response response = webTarget.path("frontend/cluster").request(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON).post(Entity.json(new Cluster(name, minWorkers, maxWorkers)));
		this.cluster = response.readEntity(Cluster.class);
		return this;
	}

	@Override
	public IMaxinet experiment(String name, Topo topo) {		
		logger.debug("creating experiment " + experiment);

		Response response = webTarget.path("frontend/experiment").request(MediaType.APPLICATION_JSON).post(Entity.json(new Experiment(name, cluster, topo)));
		this.experiment = response.readEntity(Experiment.class);
		this.topo = topo;
		
		return this;
	}
	
	@Override
	public IMaxinet setup() {
		logger.debug("setting up experiment " + this);
		
		Response response = webTarget.path("experiment/setup/").path(experiment.getName()).request(MediaType.APPLICATION_JSON).get();
		this.experiment = response.readEntity(Experiment.class);
		return this;
	}

	@Override
	public IMaxinet stop() {
		logger.debug("setting up experiment " + this);
		
		Response response = webTarget.path("experiment/stop/").path(experiment.getName()).request(MediaType.APPLICATION_JSON).get();
		this.experiment = response.readEntity(Experiment.class);
		return this;
	}

}

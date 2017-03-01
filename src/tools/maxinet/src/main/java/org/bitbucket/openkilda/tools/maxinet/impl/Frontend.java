package org.bitbucket.openkilda.tools.maxinet.impl;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitbucket.openkilda.tools.maxinet.ICluster;
import org.bitbucket.openkilda.tools.maxinet.IExperiment;
import org.bitbucket.openkilda.tools.maxinet.IFrontend;
import org.bitbucket.openkilda.tools.maxinet.Topo;

@Named
public class Frontend implements IFrontend {

	private static final Logger logger = LoggerFactory.getLogger(Frontend.class);
	
	@Inject
	private WebTarget webTarget;
	
	@Override
	public ICluster cluster(String name, Integer minWorkers, Integer maxWorkers) {
		return new Cluster(webTarget, name, minWorkers, maxWorkers);
	}

	@Override
	public IExperiment experiment(String name, ICluster cluster, Topo topo) {
		Experiment experiment = new Experiment(webTarget, name, cluster, topo);	
		
		logger.debug("creating experiment " + experiment);

		Response response = webTarget.path("frontend/experiment").request(MediaType.APPLICATION_JSON).post(Entity.json(experiment));
		Experiment e = response.readEntity(Experiment.class);
		e.setWebTarget(webTarget);
		return e;
	}

}

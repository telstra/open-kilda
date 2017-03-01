package org.bitbucket.openkilda.tools.maxinet.impl;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bitbucket.openkilda.tools.maxinet.ICluster;
import org.bitbucket.openkilda.tools.maxinet.IExperiment;
import org.bitbucket.openkilda.tools.maxinet.Topo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "cluster", "topo" })
public class Experiment implements IExperiment {

	private static final Logger logger = LoggerFactory.getLogger(Experiment.class);
		
	private WebTarget webTarget;

	@JsonProperty("name")
	private String name;

	@JsonProperty("cluster")
	private ICluster cluster;

	@JsonProperty("topo")
	private Topo topo;

	public Experiment() {
	}

	public Experiment(WebTarget webTarget, String name, ICluster cluster, Topo topo) {
		super();
		this.webTarget = webTarget;
		this.name = name;
		this.cluster = cluster;
		this.topo = topo;
	}

	@Override
	public IExperiment setup() {
		logger.debug("setting up experiment " + this);
		Response response = webTarget.path("experiment/setup/").path(name).request(MediaType.APPLICATION_JSON).get();
		return response.readEntity(Experiment.class);
	}

	@Override
	public String getName() {
		return name;
	}

	public void setWebTarget(WebTarget webTarget) {
		this.webTarget = webTarget;
	}

}

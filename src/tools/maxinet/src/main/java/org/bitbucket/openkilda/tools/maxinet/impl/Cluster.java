package org.bitbucket.openkilda.tools.maxinet.impl;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bitbucket.openkilda.tools.maxinet.ICluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "minWorkers", "maxWorkers" })
public class Cluster implements ICluster {

	private static final Logger logger = LoggerFactory.getLogger(Cluster.class);

	private static final Integer CONNECTION_TIMEOUT_MS = 5000;

	private static final Integer MIN_MAX_WORKERS = 1;

	private WebTarget webTarget;

	@JsonProperty("name")
	private String name;

	@JsonProperty("minWorkers")
	private Integer minWorkers;

	@JsonProperty("maxWorkers")
	private Integer maxWorkers;

	private MaxinetServer server;

	public Cluster() {
	}

	public Cluster(WebTarget webTarget, String name) {
		this(webTarget, name, MIN_MAX_WORKERS, MIN_MAX_WORKERS);
	}

	public Cluster(WebTarget webTarget, String name, Integer minWorkers, Integer maxWorkers) {
		super();
		this.webTarget = webTarget;
		this.name = name;
		this.minWorkers = minWorkers;
		this.maxWorkers = maxWorkers;
	}

	public Integer getMinWorkers() {
		return minWorkers;
	}

	public void setMinWorkers(Integer minWorkers) {
		this.minWorkers = minWorkers;
	}

	public Integer getMaxWorkers() {
		return maxWorkers;
	}

	public void setMaxWorkers(Integer maxWorkers) {
		this.maxWorkers = maxWorkers;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ICluster start() {
		logger.debug("starting cluster " + this);
		Response response = webTarget.path("frontend/cluster").request(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON).post(Entity.json(this));
		return response.readEntity(Cluster.class);
	}

}

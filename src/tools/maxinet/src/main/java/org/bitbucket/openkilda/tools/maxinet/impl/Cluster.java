package org.bitbucket.openkilda.tools.maxinet.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "minWorkers", "maxWorkers" })
public class Cluster {

	private static final Integer MIN_MAX_WORKERS = 1;

	@JsonProperty("name")
	private String name;

	@JsonProperty("minWorkers")
	private Integer minWorkers;

	@JsonProperty("maxWorkers")
	private Integer maxWorkers;

	public Cluster() {
	}

	public Cluster(String name) {
		this(name, MIN_MAX_WORKERS, MIN_MAX_WORKERS);
	}

	public Cluster(String name, Integer minWorkers, Integer maxWorkers) {
		super();
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

	public String getName() {
		return name;
	}

}

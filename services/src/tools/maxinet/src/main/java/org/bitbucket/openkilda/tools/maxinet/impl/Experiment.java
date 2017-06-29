package org.bitbucket.openkilda.tools.maxinet.impl;

import org.bitbucket.openkilda.tools.maxinet.Topo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "name", "cluster", "topo" })
public class Experiment {

	@JsonProperty("name")
	private String name;

	@JsonProperty("cluster")
	private Cluster cluster;

	@JsonProperty("topo")
	private Topo topo;

	public Experiment() {
	}

	public Experiment(String name, Cluster cluster, Topo topo) {
		super();
		this.name = name;
		this.cluster = cluster;
		this.topo = topo;
	}

	public String getName() {
		return name;
	}

}

/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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

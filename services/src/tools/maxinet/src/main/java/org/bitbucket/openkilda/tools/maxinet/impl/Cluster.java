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

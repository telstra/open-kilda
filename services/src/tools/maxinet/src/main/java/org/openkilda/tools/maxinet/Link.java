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

package org.openkilda.tools.maxinet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "node1",
    "node2"
    })
public class Link {

	@JsonProperty("node1")
	private Node node1;
	
	@JsonProperty("node2")
	private Node node2;

	public Link() {		
	}
	
	public Link(Node node1, Node node2) {
		this.node1 = node1;
		this.node2 = node2;
	}

	@JsonProperty("node1")
	public Node getNode1() {
		return node1;
	}

	@JsonProperty("node2")
	public Node getNode2() {
		return node2;
	}

	@JsonProperty("node2")
	public void setNode2(Node node2) {
		this.node2 = node2;
	}

}

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
    "name",
    "pos",
    "type"
    })
public class Host implements Node {
	
	@JsonProperty("name")
	private String name;
	
	@JsonProperty("pos")
	private String pos;
	
	@JsonProperty("type")
	private NodeType type;

	public Host() {		
		this.type = NodeType.HOST;
	}

	public Host(String name) {
		this(name, null);
	}
	
	public Host(String name, String pos) {
		super();
		this.name = name;
		this.pos = pos;
		this.type = NodeType.HOST;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPos() {
		return pos;
	}

	public void setPos(String pos) {
		this.pos = pos;
	}
	
	public void setType(NodeType type) {
		this.type = type;
	}

	@Override
	public NodeType getType() {
		return type;
	}

}

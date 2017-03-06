package org.bitbucket.openkilda.tools.maxinet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "workerId",
    "type"
    })
public class Switch implements Node {

	@JsonProperty("name")
	private String name;

	@JsonProperty("workerId")
	private Integer workerId;
	
	@JsonProperty("type")
	private NodeType type;
	
	public Switch() {
		this.type = NodeType.SWITCH;
	}
	
	public Switch(String name) {
		this(name, null);
	}
	
	public Switch(String name, Integer workerId) {
		super();
		this.name = name;
		this.workerId = workerId;
		this.type = NodeType.SWITCH;
	}

	public String getName() {
		return name;
	}

	public Integer getWorkerId() {
		return workerId;
	}

	public void setWorkerId(Integer workerId) {
		this.workerId = workerId;
	}
	
	public void setType(NodeType type) {
		this.type = type;
	}

	@Override
	public NodeType getType() {
		return type;
	}
	
}

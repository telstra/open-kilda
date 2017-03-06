package org.bitbucket.openkilda.tools.maxinet;

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

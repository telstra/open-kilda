package org.bitbucket.openkilda.tools.maxinet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "node1",
    "node2"
    })
public class Switch implements Node {

	@JsonProperty("name")
	private String name;

	public Switch(String name) {
		super();
		this.name = name;
	}

	public String getName() {
		return name;
	}
	
}

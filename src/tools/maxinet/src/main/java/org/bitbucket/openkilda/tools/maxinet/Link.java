package org.bitbucket.openkilda.tools.maxinet;

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

package org.bitbucket.openkilda.tools.maxinet;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ @Type(value = Host.class, name = "HOST"), @Type(value = Switch.class, name = "SWITCH") })
public interface Node {

	String getName();
	
	NodeType getType();

}

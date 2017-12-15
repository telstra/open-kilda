package org.openkilda.atdd.utils.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StaticFlowEntry.class, name = "COMMAND")})
public class AbstractStaicEntry implements Serializable {
    @JsonProperty("name")
    private final String name;

    @JsonProperty("switch")
    private final String switchDPID;

    @JsonCreator
    public AbstractStaicEntry(
            @JsonProperty("name") String name,
            @JsonProperty("switch") String switchDPID) {
        this.name = name;
        this.switchDPID = switchDPID;
    }
}

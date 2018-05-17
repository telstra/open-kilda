package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class Vlan implements Serializable {

    @JsonProperty("vlan")
    private final int vlanTag;

    @JsonCreator
    public Vlan(@JsonProperty("vlan") int vlanTag) {
        this.vlanTag = vlanTag;
    }
}

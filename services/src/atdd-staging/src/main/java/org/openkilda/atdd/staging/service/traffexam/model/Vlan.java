package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class Vlan implements Serializable {
    @JsonProperty("vlan")
    private final int vlanTag;

    @JsonCreator
    public Vlan(@JsonProperty("vlan") int vlanTag) {
        this.vlanTag = vlanTag;
    }

    public int getVlanTag() {
        return vlanTag;
    }
}

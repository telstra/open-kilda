package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class Bandwidth implements Serializable {
    @JsonProperty("value")
    private int valueKbps;  /* (kilo-bit per second) */

    @JsonCreator
    public Bandwidth(@JsonProperty("value") int kbps) {
        valueKbps = kbps;
    }

    public int getKbps() {
        return valueKbps;
    }
}

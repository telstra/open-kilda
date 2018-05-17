package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class Bandwidth implements Serializable {

    @JsonProperty("value")
    private int kbps;  /* (kilo-bit per second) */

    @JsonCreator
    public Bandwidth(@JsonProperty("value") int kbps) {
        this.kbps = kbps;
    }
}

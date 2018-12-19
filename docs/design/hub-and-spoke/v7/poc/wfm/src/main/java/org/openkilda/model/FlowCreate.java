package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.istack.Nullable;
import lombok.Value;

import java.io.Serializable;


@Value
public class FlowCreate implements Serializable {

    @JsonProperty("length")
    private int length;

    @JsonProperty("error")
    private Error error;

    @JsonCreator
    public FlowCreate(@JsonProperty("length") int length, @JsonProperty("error") @Nullable Error error) {
        this.length = length;
        this.error = error;
    }

    public enum Error {
        IN_HUB,
        IN_WORKER
    }
}

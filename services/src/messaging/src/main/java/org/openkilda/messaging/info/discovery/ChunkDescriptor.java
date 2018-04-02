package org.openkilda.messaging.info.discovery;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class ChunkDescriptor implements Serializable {
    @JsonProperty("current")
    private int current;

    @JsonProperty("total")
    private int total;

    public ChunkDescriptor(
            @JsonProperty("current") int current,
            @JsonProperty("total") int total) {
        this.current = current;
        this.total = total;
    }

    public int getCurrent() {
        return current;
    }

    public int getTotal() {
        return total;
    }
}

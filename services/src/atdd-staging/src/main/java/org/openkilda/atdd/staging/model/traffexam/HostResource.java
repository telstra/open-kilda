package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

public abstract class HostResource implements Serializable {
    @JsonProperty("idnr")
    private final UUID id;

    @JsonIgnore
    private Host host = null;

    @JsonCreator
    public HostResource(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }
}

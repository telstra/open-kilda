package org.openkilda.atdd.staging.model.traffexam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class ConsumerEndpoint extends Endpoint {
    @JsonProperty("bind_port")
    private Integer bindPort;

    public ConsumerEndpoint(UUID bindAddressId) {
        this(null, bindAddressId);
    }

    @JsonCreator
    public ConsumerEndpoint(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("bind_address") UUID bindAddressId) {
        super(id, bindAddressId);
    }

    public Integer getBindPort() {
        return bindPort;
    }
}

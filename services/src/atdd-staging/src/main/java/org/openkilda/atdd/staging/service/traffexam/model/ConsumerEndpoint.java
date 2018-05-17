package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.UUID;

@Value
public class ConsumerEndpoint extends Endpoint {
    @JsonProperty("bind_port")
    private Integer bindPort;

    public ConsumerEndpoint(UUID bindAddressId) {
        this(null, bindAddressId, null);
    }

    @JsonCreator
    public ConsumerEndpoint(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("bind_address") UUID bindAddressId,
            @JsonProperty("bind_port") Integer bindPort) {
        super(id, bindAddressId);

        this.bindPort = bindPort;
    }
}

package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="type")
@JsonSubTypes({
        @JsonSubTypes.Type(value=ConsumerEndpoint.class, name="consumer"),
        @JsonSubTypes.Type(value=ProducerEndpoint.class, name="producer")})
public abstract class Endpoint extends HostResource {
    @JsonProperty("bind_address")
    private final UUID bindAddressId;

    public Endpoint(UUID id, UUID bindAddressId) {
        super(id);
        this.bindAddressId = bindAddressId;
    }

    public UUID getBindAddressId() {
        return bindAddressId;
    }
}

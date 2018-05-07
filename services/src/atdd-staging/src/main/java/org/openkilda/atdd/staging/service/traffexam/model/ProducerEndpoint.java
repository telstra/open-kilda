package org.openkilda.atdd.staging.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.UUID;

public class ProducerEndpoint extends Endpoint {
    @JsonDeserialize(using = BandwidthJsonDeserializer.class)
    @JsonSerialize(using = BandwidthJsonSerializer.class)
    private Bandwidth bandwidth = null;

    @JsonProperty("burst_pkt")
    private int burstPkt = 0;

    @JsonDeserialize(using = TimeLimitJsonDeserializer.class)
    @JsonSerialize(using = TimeLimitJsonSerializer.class)
    private TimeLimit time = null;

    @JsonProperty("remote_address")
    private final EndpointAddress targetAddress;

    public ProducerEndpoint(EndpointAddress targetAddress) {
        this(null, null, targetAddress);
    }

    public ProducerEndpoint(UUID bindAddressId, EndpointAddress targetAddress) {
        this(null, bindAddressId, targetAddress);
    }

    @JsonCreator
    public ProducerEndpoint(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("bind_address") UUID bindAddressId,
            @JsonProperty("remote_address") EndpointAddress targetAddress) {
        super(id, bindAddressId);
        this.targetAddress = targetAddress;
    }

    public Bandwidth getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(Bandwidth bandwidth) {
        this.bandwidth = bandwidth;
    }

    public int getBurstPkt() {
        return burstPkt;
    }

    public void setBurstPkt(int burstPkt) {
        this.burstPkt = burstPkt;
    }

    public TimeLimit getTime() {
        return time;
    }

    public void setTime(TimeLimit time) {
        this.time = time;
    }

    public EndpointAddress getTargetAddress() {
        return targetAddress;
    }
}

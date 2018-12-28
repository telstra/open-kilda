package org.openkilda.messaging.command.discovery;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscoveryFilterEntity implements Serializable {
    @JsonProperty("switch")
    public final SwitchId switchId;

    @JsonProperty("port")
    public final int portId;

    @JsonCreator
    public DiscoveryFilterEntity(
            @JsonProperty("switch") SwitchId switchId,
            @JsonProperty("port") int portId) {
        this.switchId = switchId;
        this.portId = portId;
    }
}

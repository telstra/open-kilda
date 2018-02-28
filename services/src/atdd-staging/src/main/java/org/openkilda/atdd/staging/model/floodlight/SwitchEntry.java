package org.openkilda.atdd.staging.model.floodlight;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SwitchEntry {

    @JsonProperty("inetAddress")
    private String address;

    @JsonProperty("connectedSince")
    private String connectedSince;

    @JsonProperty("openFlowVersion")
    private String oFVersion;

    @JsonProperty("switchDPID")
    private String switchId;

}

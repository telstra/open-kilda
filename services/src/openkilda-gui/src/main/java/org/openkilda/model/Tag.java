package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"port", "switchid"})
public class Tag {

    @JsonProperty("port")
    private String port;
    
    @JsonProperty("switchid")
    private String switchId;
    
    public String getPort() {
        return port;
    }
    
    public void setPort(String port) {
        this.port = port;
    }
    
    public String getSwitchId() {
        return switchId;
    }
    
    public void setSwitchId(String switchId) {
        this.switchId = switchId;
    }
    
    
    
}

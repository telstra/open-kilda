package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"speed", "path", "available_bandwidth", "state"})
public class IslLink {

    @JsonProperty("speed")
    private Integer speed;
    @JsonProperty("path")
    private List<IslPath> path = null;
    @JsonProperty("available_bandwidth")
    private Integer availableBandwidth;

    @JsonProperty("state")
    private String state;

    public Integer getSpeed() {
        return speed;
    }

    public void setSpeed(final Integer speed) {
        this.speed = speed;
    }

    public List<IslPath> getPath() {
        return path;
    }

    public void setPath(final List<IslPath> path) {
        this.path = path;
    }

    public Integer getAvailableBandwidth() {
        return availableBandwidth;
    }

    public void setAvailableBandwidth(final Integer availableBandwidth) {
        this.availableBandwidth = availableBandwidth;
    }


    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "IslLink [speed=" + speed + ", path=" + path + ", availableBandwidth="
                + availableBandwidth + ", state=" + state + "]";
    }

}

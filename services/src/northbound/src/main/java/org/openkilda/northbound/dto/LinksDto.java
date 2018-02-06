package org.openkilda.northbound.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinksDto {

    private long speed;

    @JsonProperty("available_bandwidth")
    private long availableBandwidth;

    private List<PathDto> path;

    /*
     * TODO: Will need to add all other properties, or just all properties with duplicates of the others.
     *       With the advent of link_props, the operator can upload additional properties, which will
     *       need to be returned with this class.
     */

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public long getAvailableBandwidth() {
        return availableBandwidth;
    }

    public void setAvailableBandwidth(long availableBandwidth) {
        this.availableBandwidth = availableBandwidth;
    }

    public List<PathDto> getPath() {
        return path;
    }

    public void setPath(List<PathDto> path) {
        this.path = path;
    }
}

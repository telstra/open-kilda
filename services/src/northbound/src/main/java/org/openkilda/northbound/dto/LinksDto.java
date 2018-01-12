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
    private long availableBandidth;

    private List<PathDto> path;

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public long getAvailableBandidth() {
        return availableBandidth;
    }

    public void setAvailableBandidth(long availableBandidth) {
        this.availableBandidth = availableBandidth;
    }

    public List<PathDto> getPath() {
        return path;
    }

    public void setPath(List<PathDto> path) {
        this.path = path;
    }
}

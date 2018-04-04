package org.openkilda.northbound.dto;


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.info.event.IslChangeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinksDto {

    private long speed;

    @JsonProperty("available_bandwidth")
    private long availableBandwidth;

    @JsonProperty("state")
    protected IslChangeType state;

    private List<PathDto> path;

    /**
     * With the advent of link_props, a link can have zero or more extra props. This field will
     * be used to store that information. Technically, it'll store all unrecognized keys .. unless
     * we add a filter somewhere.
     */
    Map<String, String> otherFields = new HashMap<>();

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

    public IslChangeType getState() {
        return state;
    }

    public void setState(IslChangeType state) {
        this.state = state;
    }

    // Capture all other fields that Jackson do not match other members
    @JsonAnyGetter
    public Map<String, String> otherFields() {
        return otherFields;
    }

    /**
     * This will store any unrecognized key in the json object.
     * There are some values that may not be necessary, and/or we'd prefer to hide .. like clazz
     */
    @JsonAnySetter
    public void setOtherField(String name, String value) {
        otherFields.put(name, value);
    }

}

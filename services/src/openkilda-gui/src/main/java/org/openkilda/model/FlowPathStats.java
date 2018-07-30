package org.openkilda.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "startDate", "endDate", "downsample", "switches", "direction"})
public class FlowPathStats {

    @JsonProperty("flowid")
    private String flowid;
    
    @JsonProperty("direction")
    private String direction;

    @JsonProperty("startdate")
    private String startDate;

    @JsonProperty("enddate")
    private String endDate;

    @JsonProperty("downsample")
    private String downsample;

    @JsonProperty("switches")
    private List<String> switches;

    public String getFlowid() {
        return flowid;
    }

    public void setFlowid(String flowid) {
        this.flowid = flowid;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getDownsample() {
        return downsample;
    }

    public void setDownsample(String downsample) {
        this.downsample = downsample;
    }

    public List<String> getSwitches() {
        return switches;
    }

    public void setSwitches(List<String> switches) {
        this.switches = switches;
    }

    public String getDirection() {
        return direction;
    }
    

    public void setDirection(String direction) {
        this.direction = direction;
    }
    
}

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class PathResponse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "flowpath"})
public class FlowPath {

    @JsonProperty("flowid")
    private String flowid;

    @JsonProperty("flowpath")
    private PathInfoData flowpath;

    /**
     * Gets the flowid.
     *
     * @return the flowid
     */
    @JsonProperty("flowid")
    public String getFlowid() {
        return flowid;
    }

    /**
     * Sets the flowid.
     *
     * @param flowid the new flowid
     */
    @JsonProperty("flowid")
    public void setFlowid(final String flowid) {
        this.flowid = flowid;
    }

    /**
     * Gets the flowpath.
     *
     * @return the flowpath
     */
    @JsonProperty("flowpath")
    public PathInfoData getFlowpath() {
        return flowpath;
    }

    /**
     * Sets the flowpath.
     *
     * @param flowpath the new flowpath
     */
    @JsonProperty("flowpath")
    public void setFlowpath(final PathInfoData flowpath) {
        this.flowpath = flowpath;
    }

}

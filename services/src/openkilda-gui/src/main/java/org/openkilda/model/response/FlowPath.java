package org.openkilda.model.response;

import org.openkilda.integration.model.response.PathInfoData;

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

    /** The flowid. */
    @JsonProperty("flowid")
    private String flowid;

    /** The flowpath. */
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
    public void setFlowid(String flowid) {
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
    public void setFlowpath(PathInfoData flowpath) {
        this.flowpath = flowpath;
    }

}

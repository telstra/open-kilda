package org.openkilda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class FlowStatusResponse.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"flowid", "status"})
public class FlowStatus implements Serializable {

    /** The flowid. */
    @JsonProperty("flowid")
    private String flowid;

    /** The status. */
    @JsonProperty("status")
    private String status;

    /** The Constant serialVersionUID. */
    private final static long serialVersionUID = -8688238020704231665L;

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
     * Gets the status.
     *
     * @return the status
     */
    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the new status
     */
    @JsonProperty("status")
    public void setStatus(final String status) {
        this.status = status;
    }

}

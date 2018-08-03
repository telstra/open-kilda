package org.openkilda.integration.model.response;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"flowid", "flowpath_forward", "flowpath_reverse"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowPayload implements Serializable {

    private static final long serialVersionUID = -611895293779307399L;

    @JsonProperty("flowid")
    private String flowId;
    
    @JsonProperty("flowpath_forward")
    private List<FlowPathNode> forward;
    
    @JsonProperty("flowpath_reverse")
    private List<FlowPathNode> reverse;
    
    @JsonProperty("flowid")
    public String getFlowId() {
        return flowId;
    }
    
    @JsonProperty("flowid")
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    @JsonProperty("flowpath_forward")
    public List<FlowPathNode> getForward() {
        return forward;
    }

    @JsonProperty("flowpath_forward")
    public void setForward(List<FlowPathNode> forward) {
        this.forward = forward;
    }

    @JsonProperty("flowpath_reverse")
    public List<FlowPathNode> getReverse() {
        return reverse;
    }

    @JsonProperty("flowpath_reverse")
    public void setReverse(List<FlowPathNode> reverse) {
        this.reverse = reverse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getForward(), getReverse());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        FlowPayload that = (FlowPayload) object;
        return Objects.equals(getForward(), that.getForward())
                && Objects.equals(getReverse(), that.getReverse());
    }

    @Override
    public String toString() {
        return "FlowPayload [flowId= " + flowId + "flowpath_forward=" + forward
                + ", flowpath_reverse=" + reverse + "]";
    }

}

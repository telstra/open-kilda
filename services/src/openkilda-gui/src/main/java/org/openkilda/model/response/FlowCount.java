package org.openkilda.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class FlowsCount.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"source_switch", "target_switch", "flow_count"})
public class FlowCount {

    /** The src switch. */
    @JsonProperty("source_switch")
    private String srcSwitch;

    /** The dst switch. */
    @JsonProperty("target_switch")
    private String dstSwitch;

    /** The flow count. */
    @JsonProperty("flow_count")
    private Integer flowCount;

    /**
     * Gets the src switch.
     *
     * @return the src switch
     */
    @JsonProperty("source_switch")
    public String getSrcSwitch() {
        return srcSwitch;
    }

    /**
     * Sets the src switch.
     *
     * @param srcSwitch the new src switch
     */
    @JsonProperty("source_switch")
    public void setSrcSwitch(final String srcSwitch) {
        this.srcSwitch = srcSwitch;
    }

    /**
     * Gets the dst switch.
     *
     * @return the dst switch
     */
    @JsonProperty("target_switch")
    public String getDstSwitch() {
        return dstSwitch;
    }

    /**
     * Sets the dst switch.
     *
     * @param dstSwitch the new dst switch
     */
    @JsonProperty("target_switch")
    public void setDstSwitch(final String dstSwitch) {
        this.dstSwitch = dstSwitch;
    }

    /**
     * Gets the flow count.
     *
     * @return the flow count
     */
    @JsonProperty("flow_count")
    public Integer getFlowCount() {
        return flowCount;
    }

    /**
     * Sets the flow count.
     *
     * @param flowCount the new flow count
     */
    @JsonProperty("flow_count")
    public void setFlowCount(final Integer flowCount) {
        this.flowCount = flowCount;
    }

    public void incrementFlowCount() {
        flowCount++;
    }

    @Override
    public int hashCode() {
        return srcSwitch.hashCode() + dstSwitch.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if(obj instanceof FlowCount) {
            FlowCount flowsCount = (FlowCount) obj;
            return (flowsCount.srcSwitch.equalsIgnoreCase(srcSwitch) &&
                    flowsCount.dstSwitch.equalsIgnoreCase(dstSwitch)) ||
                    (flowsCount.srcSwitch.equalsIgnoreCase(dstSwitch) &&
                    flowsCount.dstSwitch.equalsIgnoreCase(srcSwitch));
        }
        return false;
    }

}

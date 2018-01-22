package org.openkilda.model;

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

    @JsonProperty("source_switch")
    private String srcSwitch;

    @JsonProperty("target_switch")
    private String dstSwitch;

    
    @JsonProperty("flow_count")
    private Integer flowCount;

    /**
     * Gets the src switch.
     *
     * @return the src switch
     */
    public String getSrcSwitch() {
        return srcSwitch;
    }

    /**
     * Sets the src switch.
     *
     * @param srcSwitch the new src switch
     */
    public void setSrcSwitch(final String srcSwitch) {
        this.srcSwitch = srcSwitch;
    }

    /**
     * Gets the dst switch.
     *
     * @return the dst switch
     */
    public String getDstSwitch() {
        return dstSwitch;
    }

    /**
     * Sets the dst switch.
     *
     * @param dstSwitch the new dst switch
     */
    public void setDstSwitch(final String dstSwitch) {
        this.dstSwitch = dstSwitch;
    }

    /**
     * Gets the flow count.
     *
     * @return the flow count
     */
    public Integer getFlowCount() {
        return flowCount;
    }

    /**
     * Sets the flow count.
     *
     * @param flowCount the new flow count
     */
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

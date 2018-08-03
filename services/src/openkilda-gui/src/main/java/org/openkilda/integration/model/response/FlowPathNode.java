package org.openkilda.integration.model.response;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The Class Path.
 *
 * @author Gaurav Chugh
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switch_id", "switch_name", "input_port", "output_port", })
public class FlowPathNode implements Serializable {

    private final static long serialVersionUID = -4515006227265225751L;
    
    /** The switch id. */
    @JsonProperty("switch_id")
    private String switchId;

    /** The switch id. */
    @JsonProperty("switch_name")
    private String switchName;

    /** The in port no. */
    @JsonProperty("input_port")
    private Integer inputPort;

    /** The out port no. */
    @JsonProperty("output_port")
    private Integer outputPort;


    @JsonCreator
    public FlowPathNode() {

    }

    @JsonCreator
    public FlowPathNode(@JsonProperty("input_port") Integer inputPort,
            @JsonProperty("output_port") Integer outputPort,
            @JsonProperty("switch_id") String switchId,
            @JsonProperty("switch_name") String switchName) {
        setInputPort(inputPort);
        setOutputPort(outputPort);
        setSwitchId(switchId);
        setSwitchName(switchName);
    }

    /**
     * Gets the switch id.
     *
     * @return the switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets the switch id.
     *
     * @param switchId the new switch id
     */
    public void setSwitchId(final String switchId) {
        this.switchId = switchId;
    }

    /**
     * Gets the inputPort
     *
     * @return the inputPort
     */
    public Integer getInputPort() {
        return inputPort;
    }

    /**
     * Sets the input port
     *
     * @param inputPort the new input port
     */
    public void setInputPort(final Integer inputPort) {
        this.inputPort = inputPort;
    }

    /**
     * Gets the output port 
     *
     * @return the output port 
     */
    public Integer getOutputPort() {
        return outputPort;
    }

    /**
     * Sets the output port 
     *
     * @param outputPort the new output port 
     */
    public void setOutputPort(final Integer outputPort) {
        this.outputPort = outputPort;
    }

    public String getSwitchName() {
        return switchName;
    }

    public void setSwitchName(String switchName) {
        this.switchName = switchName;
    }

    @Override
    public String toString() {
        return "PathNode [switchId=" + switchId + ", switchName=" + switchName + ", inputPort="
                + inputPort + ", outputPort=" + outputPort + "]";
    }

}

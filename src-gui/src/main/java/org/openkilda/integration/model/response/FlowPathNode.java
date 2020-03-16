/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.integration.model.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/**
 * The Class Path.
 *
 * @author Gaurav Chugh
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"switch_id", "switch_name", "input_port", "output_port", })
public class FlowPathNode implements Serializable {

    private static final long serialVersionUID = -4515006227265225751L;
    
    @JsonProperty("switch_id")
    private String switchId;

    @JsonProperty("switch_name")
    private String switchName;

    @JsonProperty("input_port")
    private Integer inputPort;

    @JsonProperty("output_port")
    private Integer outputPort;

    @JsonCreator
    public FlowPathNode() {

    }

    /**
     * Instantiates a new flow path node.
     *
     * @param inputPort the input port
     * @param outputPort the output port
     * @param switchId the switch id
     * @param switchName the switch name
     */
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
     * Gets the inputPort.
     *
     * @return the inputPort
     */
    public Integer getInputPort() {
        return inputPort;
    }

    /**
     * Sets the input port.
     *
     * @param inputPort the new input port
     */
    public void setInputPort(final Integer inputPort) {
        this.inputPort = inputPort;
    }

    /**
     * Gets the output port .
     *
     * @return the output port 
     */
    public Integer getOutputPort() {
        return outputPort;
    }

    /**
     * Sets the output port .
     *
     * @param outputPort the new output port 
     */
    public void setOutputPort(final Integer outputPort) {
        this.outputPort = outputPort;
    }

    /**
     * Gets the switch name.
     *
     * @return the switch name
     */
    public String getSwitchName() {
        return switchName;
    }

    /**
     * Sets the switch name.
     *
     * @param switchName the new switch name
     */
    public void setSwitchName(String switchName) {
        this.switchName = switchName;
    }

    @Override
    public String toString() {
        return "PathNode [switchId=" + switchId + ", switchName=" + switchName + ", inputPort="
                + inputPort + ", outputPort=" + outputPort + "]";
    }

}

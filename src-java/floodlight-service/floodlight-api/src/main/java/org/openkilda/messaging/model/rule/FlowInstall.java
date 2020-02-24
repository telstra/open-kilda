/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.model.rule;

import org.openkilda.model.OutputVlanType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents rule entity for FLOW_MOD/INSTALL OpenFlow command.
 */
@JsonSerialize
public class FlowInstall extends FlowDelete implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Rule output port.
     */
    @JsonProperty(RuleConstants.OUTPUT_PORT)
    private int outputPort;

    /**
     * Rule output vlan id, 0 if vlan pop.
     */
    @JsonProperty(RuleConstants.OUTPUT_VLAN)
    private int outputVlan;

    /**
     * Rule output vlan operation.
     */
    @JsonProperty(RuleConstants.OUTPUT_VLAN_TYPE)
    private OutputVlanType outputVlanType;

    /**
     * Rule meter id, 0 if not ingress rule.
     */
    @JsonProperty(RuleConstants.METER_ID)
    private int meterId;

    /**
     * Rule priority.
     */
    @JsonProperty(RuleConstants.PRIORITY)
    private int priority;

    /**
     * Default constructor.
     */
    public FlowInstall() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId         rule flow id
     * @param cookie         rule cookie
     * @param switchId       rule switch id
     * @param matchPort      rule match port
     * @param matchVlan      rule match vlan id
     * @param outputPort     rule output port
     * @param outputVlan     rule output vlan id
     * @param outputVlanType rule output vlan operation
     * @param meterId        rule meter id
     * @param priority       rule priority
     */
    public FlowInstall(@JsonProperty(RuleConstants.FLOW_ID) String flowId,
                       @JsonProperty(RuleConstants.COOKIE) int cookie,
                       @JsonProperty(RuleConstants.SWITCH_ID) String switchId,
                       @JsonProperty(RuleConstants.MATCH_PORT) int matchPort,
                       @JsonProperty(RuleConstants.MATCH_VLAN) int matchVlan,
                       @JsonProperty(RuleConstants.OUTPUT_PORT) int outputPort,
                       @JsonProperty(RuleConstants.OUTPUT_VLAN) int outputVlan,
                       @JsonProperty(RuleConstants.OUTPUT_VLAN_TYPE) OutputVlanType outputVlanType,
                       @JsonProperty(RuleConstants.METER_ID) int meterId,
                       @JsonProperty(RuleConstants.PRIORITY) int priority) {
        super(flowId, cookie, switchId, matchPort, matchVlan);
        this.outputPort = outputPort;
        this.outputVlan = outputVlan;
        this.outputVlanType = outputVlanType;
        this.meterId = meterId;
        this.priority = priority;
    }

    /**
     * Gets output port.
     *
     * @return output port
     */
    public int getOutputPort() {
        return outputPort;
    }

    /**
     * Sets output port.
     *
     * @param outputPort output port
     */
    public void setOutputPort(int outputPort) {
        this.outputPort = outputPort;
    }

    /**
     * Gets output vlan.
     *
     * @return output vlan
     */
    public int getOutputVlan() {
        return outputVlan;
    }

    /**
     * Sets output vlan.
     *
     * @param outputVlan output vlan
     */
    public void setOutputVlan(int outputVlan) {
        this.outputVlan = outputVlan;
    }

    /**
     * Gets output vlan type.
     *
     * @return output vlan type
     */
    public OutputVlanType getOutputVlanType() {
        return outputVlanType;
    }

    /**
     * Sets output vlan type.
     *
     * @param outputVlanType output vlan type
     */
    public void setOutputVlanType(OutputVlanType outputVlanType) {
        this.outputVlanType = outputVlanType;
    }

    /**
     * Gets meter id.
     *
     * @return meter id
     */
    public int getMeterId() {
        return meterId;
    }

    /**
     * Sets meter id.
     *
     * @param meterId meter id
     */
    public void setMeterId(int meterId) {
        this.meterId = meterId;
    }

    /**
     * Gets priority.
     *
     * @return priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets priority.
     *
     * @param priority priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        FlowInstall that = (FlowInstall) object;
        return Objects.equals(getFlowId(), that.getFlowId())
                && getCookie() == that.getCookie()
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && getMatchPort() == that.getMatchPort()
                && getMatchVlan() == that.getMatchVlan()
                && getOutputPort() == that.getOutputPort()
                && getOutputVlan() == that.getOutputVlan()
                && getOutputVlanType() == that.getOutputVlanType()
                && getMeterId() == that.getMeterId()
                && getPriority() == that.getPriority();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getCookie(), getSwitchId(), getMatchPort(), getMatchVlan(),
                getOutputPort(), getOutputVlan(), getOutputVlanType(), getMeterId(), getPriority());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add(RuleConstants.FLOW_ID, flowId)
                .add(RuleConstants.COOKIE, cookie)
                .add(RuleConstants.SWITCH_ID, switchId)
                .add(RuleConstants.MATCH_PORT, matchPort)
                .add(RuleConstants.MATCH_VLAN, matchVlan)
                .add(RuleConstants.OUTPUT_PORT, outputPort)
                .add(RuleConstants.OUTPUT_VLAN, outputVlan)
                .add(RuleConstants.OUTPUT_VLAN_TYPE, outputVlanType)
                .add(RuleConstants.METER_ID, meterId)
                .add(RuleConstants.PRIORITY, priority)
                .toString();
    }
}

package org.bitbucket.openkilda.pce.model;

import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents rule entity.
 */
@JsonSerialize
public class Rule implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty("flow_id")
    private String flowId;

    /**
     * Rule switch id.
     */
    @JsonProperty("switch_id")
    private String switchId;

    /**
     * Rule cookie.
     */
    @JsonProperty("cookie")
    private int cookie;

    /**
     * Rule math port.
     */
    @JsonProperty("match_port")
    private int matchPort;

    /**
     * Rule math vlan id, 0 if no vlan id match.
     */
    @JsonProperty("match_vlan")
    private int matchVlan;

    /**
     * Rule output port.
     */
    @JsonProperty("output_port")
    private int outputPort;

    /**
     * Rule output vlan id, 0 if vlan pop.
     */
    @JsonProperty("output_vlan")
    private int outputVlan;

    /**
     * Rule output vlan operation.
     */
    @JsonProperty("output_vlan_type")
    private OutputVlanType outputVlanType;

    /**
     * Rule meter id, 0 if not ingress rule.
     */
    @JsonProperty("meter_id")
    private int meterId;

    /**
     * Rule priority.
     */
    @JsonProperty("priority")
    private int priority;

    /**
     * Default constructor.
     */
    public Rule() {
    }

    /**
     * Instance constructor.
     *
     * @param switchId       rule switch id
     * @param cookie         rule cookie
     * @param priority       rule priority
     * @param matchPort      rule match port
     * @param matchVlan      rule match vlan id
     * @param outputPort     rule output port
     * @param outputVlan     rule output vlan id
     * @param meterId        rule meter id
     * @param outputVlanType rule output vlan operation
     */
    public Rule(@JsonProperty("flow_id") String flowId,
                @JsonProperty("switch_id") String switchId,
                @JsonProperty("cookie") int cookie,
                @JsonProperty("priority") int priority,
                @JsonProperty("match_port") int matchPort,
                @JsonProperty("match_vlan") int matchVlan,
                @JsonProperty("output_port") int outputPort,
                @JsonProperty("output_vlan") int outputVlan,
                @JsonProperty("meter_id") int meterId,
                @JsonProperty("output_vlan_type") OutputVlanType outputVlanType) {
        this.switchId = switchId;
        this.cookie = cookie;
        this.priority = priority;
        this.matchPort = matchPort;
        this.matchVlan = matchVlan;
        this.outputPort = outputPort;
        this.outputVlan = outputVlan;
        this.meterId = meterId;
        this.outputVlanType = outputVlanType;
        this.flowId = flowId;
    }

    /**
     * Gets flow id.
     *
     * @return flow id
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Sets flow id.
     *
     * @return flow id
     */
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    /**
     * Gets switch id.
     *
     * @return switch id
     */
    public String getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id
     */
    public void setSwitchId(String switchId) {
        this.switchId = switchId;
    }

    /**
     * Gets cookie.
     *
     * @return cookie
     */
    public int getCookie() {
        return cookie;
    }

    /**
     * Sets cooke.
     *
     * @param cookie cookie
     */
    public void setCookie(int cookie) {
        this.cookie = cookie;
    }

    /**
     * Gets match port
     *
     * @return match port
     */
    public int getMatchPort() {
        return matchPort;
    }

    /**
     * Sets match port.
     *
     * @param matchPort match port
     */
    public void setMatchPort(int matchPort) {
        this.matchPort = matchPort;
    }

    /**
     * Gets match vlan.
     *
     * @return match vlan
     */
    public int getMatchVlan() {
        return matchVlan;
    }

    /**
     * Sets match vlan.
     *
     * @param matchVlan match vlan
     */
    public void setMatchVlan(int matchVlan) {
        this.matchVlan = matchVlan;
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

        Rule that = (Rule) object;
        return Objects.equals(getFlowId(), that.getFlowId())
                && getCookie() == that.getCookie()
                && getMatchPort() == that.getMatchPort()
                && getMatchVlan() == that.getMatchVlan()
                && getOutputPort() == that.getOutputPort()
                && getOutputVlan() == that.getOutputVlan()
                && getMeterId() == that.getMeterId()
                && getPriority() == that.getPriority()
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && getOutputVlanType() == that.getOutputVlanType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getSwitchId(), getCookie(), getMatchPort(), getMatchVlan(),
                getOutputPort(), getOutputVlan(), getOutputVlanType(), getMeterId(), getPriority());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("flow_id", flowId)
                .add("switch_id", switchId)
                .add("cookie", cookie)
                .add("match_port", matchPort)
                .add("match_vlan", matchVlan)
                .add("output_port", outputPort)
                .add("output_vlan", outputVlan)
                .add("output_vlan_type", outputVlanType)
                .add("meter_id", meterId)
                .add("priority", priority)
                .toString();
    }
}

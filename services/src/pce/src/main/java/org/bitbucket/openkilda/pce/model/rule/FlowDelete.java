package org.bitbucket.openkilda.pce.model.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents rule entity for FLOW_MOD/DELETE OpenFlow command.
 */
@JsonSerialize
public class FlowDelete extends Rule implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Rule math port.
     */
    @JsonProperty(RuleConstants.MATCH_PORT)
    protected int matchPort;

    /**
     * Rule math vlan id, 0 if no vlan id match.
     */
    @JsonProperty(RuleConstants.MATCH_VLAN)
    protected int matchVlan;

    /**
     * Default constructor.
     */
    public FlowDelete() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId    rule flow id
     * @param cookie    rule cookie
     * @param switchId  rule switch id
     * @param matchPort rule match port
     * @param matchVlan rule match vlan id
     */
    public FlowDelete(@JsonProperty(RuleConstants.FLOW_ID) String flowId,
                      @JsonProperty(RuleConstants.COOKIE) int cookie,
                      @JsonProperty(RuleConstants.SWITCH_ID) String switchId,
                      @JsonProperty(RuleConstants.MATCH_PORT) int matchPort,
                      @JsonProperty(RuleConstants.MATCH_VLAN) int matchVlan) {
        super(flowId, cookie, switchId);
        this.matchPort = matchPort;
        this.matchVlan = matchVlan;
    }

    /**
     * Gets match port.
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

        FlowDelete that = (FlowDelete) object;
        return Objects.equals(getFlowId(), that.getFlowId())
                && getCookie() == that.getCookie()
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && getMatchPort() == that.getMatchPort()
                && getMatchVlan() == that.getMatchVlan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getCookie(), getSwitchId(), getMatchPort(), getMatchVlan());
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
                .toString();
    }
}

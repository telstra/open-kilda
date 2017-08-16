package org.bitbucket.openkilda.pce.model.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents rule entity METER_MOD/DELETE OpenFlow command.
 */
@JsonSerialize
public class MeterDelete extends Rule implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Rule meter id.
     */
    @JsonProperty(RuleConstants.METER_ID)
    protected int meterId;

    /**
     * Default constructor.
     */
    public MeterDelete() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId   rule flow id
     * @param switchId rule switch id
     * @param cookie   rule cookie
     * @param meterId  rule meter id
     */
    public MeterDelete(@JsonProperty(RuleConstants.FLOW_ID) String flowId,
                       @JsonProperty(RuleConstants.COOKIE) int cookie,
                       @JsonProperty(RuleConstants.SWITCH_ID) String switchId,
                       @JsonProperty(RuleConstants.METER_ID) int meterId) {
        super(flowId, cookie, switchId);
        this.meterId = meterId;
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

        MeterDelete that = (MeterDelete) object;
        return Objects.equals(getFlowId(), that.getFlowId())
                && getCookie() == that.getCookie()
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && getMeterId() == that.getMeterId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getCookie(), getSwitchId(), getMeterId());
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
                .add(RuleConstants.METER_ID, meterId)
                .toString();
    }
}

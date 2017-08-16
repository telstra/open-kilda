package org.bitbucket.openkilda.pce.model.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents rule entity for METER_MOD/INSTALL OpenFlow command.
 */
@JsonSerialize
public class MeterInstall extends MeterDelete implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Meter bandwidth.
     */
    @JsonProperty(RuleConstants.BANDWIDTH)
    private int bandwidth;

    /**
     * Default constructor.
     */
    public MeterInstall() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId    flow id
     * @param switchId  switch id
     * @param cookie    cookie
     * @param meterId   meter id
     * @param bandwidth meter bandwidth
     */
    public MeterInstall(@JsonProperty(RuleConstants.FLOW_ID) String flowId,
                        @JsonProperty(RuleConstants.COOKIE) int cookie,
                        @JsonProperty(RuleConstants.SWITCH_ID) String switchId,
                        @JsonProperty(RuleConstants.METER_ID) int meterId,
                        @JsonProperty(RuleConstants.BANDWIDTH) int bandwidth) {
        super(flowId, cookie, switchId, meterId);
        this.bandwidth = bandwidth;
    }

    /**
     * Gets bandwidth.
     *
     * @return bandwidth
     */
    public int getBandwidth() {
        return bandwidth;
    }

    /**
     * Sets bandwidth.
     *
     * @param bandwidth bandwidth
     */
    public void setBandwidth(int bandwidth) {
        this.bandwidth = bandwidth;
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

        MeterInstall that = (MeterInstall) object;
        return Objects.equals(getFlowId(), that.getFlowId())
                && getCookie() == that.getCookie()
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && getMeterId() == that.getMeterId()
                && getBandwidth() == that.getBandwidth();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getCookie(), getSwitchId(), getMeterId(), getBandwidth());
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
                .add(RuleConstants.BANDWIDTH, bandwidth)
                .toString();
    }
}

package org.bitbucket.openkilda.pce.model.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents base rule entity.
 */
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = RuleConstants.OF_COMMAND)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FlowInstall.class, name = "flow_install"),
        @JsonSubTypes.Type(value = FlowUpdate.class, name = "flow_update"),
        @JsonSubTypes.Type(value = FlowDelete.class, name = "flow_delete"),
        @JsonSubTypes.Type(value = MeterInstall.class, name = "meter_install"),
        @JsonSubTypes.Type(value = MeterUpdate.class, name = "meter_update"),
        @JsonSubTypes.Type(value = MeterDelete.class, name = "meter_delete")})
public class Rule implements Serializable {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Flow id.
     */
    @JsonProperty(RuleConstants.FLOW_ID)
    protected String flowId;

    /**
     * Flow cookie.
     */
    @JsonProperty(RuleConstants.COOKIE)
    protected int cookie;

    /**
     * Rule switch id.
     */
    @JsonProperty(RuleConstants.SWITCH_ID)
    protected String switchId;

    /**
     * Default constructor.
     */
    public Rule() {
    }

    /**
     * Instance constructor.
     *
     * @param flowId   rule flow id
     * @param switchId rule switch id
     * @param cookie   rule cookie
     */
    public Rule(@JsonProperty(RuleConstants.FLOW_ID) String flowId,
                @JsonProperty(RuleConstants.COOKIE) int cookie,
                @JsonProperty(RuleConstants.SWITCH_ID) String switchId) {
        this.switchId = switchId;
        this.cookie = cookie;
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
                && Objects.equals(getSwitchId(), that.getSwitchId())
                && getCookie() == that.getCookie();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getFlowId(), getCookie(), getSwitchId());
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
                .toString();
    }
}

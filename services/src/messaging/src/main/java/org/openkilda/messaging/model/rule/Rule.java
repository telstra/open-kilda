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

import org.openkilda.messaging.BaseMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents base rule entity.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rule extends BaseMessage {
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
        super();
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
        super();
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
     *
     * @param flowId flow id
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

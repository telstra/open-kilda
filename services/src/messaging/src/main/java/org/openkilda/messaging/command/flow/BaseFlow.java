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

package org.openkilda.messaging.command.flow;

import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents general flow info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id"})
public class BaseFlow extends CommandData {
    /**
     * Transaction id.
     */
    @JsonProperty(TRANSACTION_ID)
    protected Long transactionId;

    /**
     * The flow id.
     */
    @JsonProperty(FLOW_ID)
    protected String id;

    /**
     * Cookie allocated for flow.
     */
    @JsonProperty("cookie")
    protected Long cookie;

    /**
     * The switch id to manage flow on. It is a mandatory parameter.
     */
    @JsonProperty("switch_id")
    protected SwitchId switchId;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id            flow id
     * @param cookie        cookie of the flow
     * @param switchId      switch id for flow management
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public BaseFlow(@JsonProperty(TRANSACTION_ID) final Long transactionId,
                    @JsonProperty(FLOW_ID) final String id,
                    @JsonProperty("cookie") final Long cookie,
                    @JsonProperty("switch_id") final SwitchId switchId) {
        setTransactionId(transactionId);
        setId(id);
        setCookie(cookie);
        setSwitchId(switchId);
    }

    /**
     * Returns transaction id of the request.
     *
     * @return transaction id of the request
     */
    public Long getTransactionId() {
        return transactionId;
    }

    /**
     * Sets transaction id of the request.
     *
     * @param transactionId transaction id of the request
     */
    public void setTransactionId(final Long transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Returns the flow id.
     *
     * @return the flow id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the flow id.
     *
     * @param id flow id
     */
    public void setId(final String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("flow id must be set");
        }
        this.id = id;
    }

    /**
     * Returns cookie of the flow.
     *
     * @return cookie of the flow
     */
    public Long getCookie() {
        return cookie;
    }

    /**
     * Sets cookie for the flow.
     *
     * @param cookie for the flow
     */
    public void setCookie(final Long cookie) {
        this.cookie = cookie;
    }

    /**
     * Returns id of the switch.
     *
     * @return ID of the switch
     */
    public SwitchId getSwitchId() {
        return switchId;
    }

    /**
     * Sets id of the switch.
     *
     * @param switchId of the switch
     */
    public void setSwitchId(SwitchId switchId) {
        if (switchId == null) {
            throw new IllegalArgumentException("need to set a switch_id");
        } else if (!Utils.validateSwitchId(switchId)) {
            throw new IllegalArgumentException("need to set valid value for switch_id");
        }
        this.switchId = switchId;
    }
}

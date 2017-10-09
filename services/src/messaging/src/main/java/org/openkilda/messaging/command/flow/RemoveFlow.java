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

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.FLOW_ID;
import static org.openkilda.messaging.Utils.TRANSACTION_ID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Class represents flow deletion info.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        TRANSACTION_ID,
        FLOW_ID,
        "cookie",
        "switch_id",
        "meter_id"})
public class RemoveFlow extends BaseFlow {
    /**
     * Meter id.
     */
    @JsonProperty("meter_id")
    private Long meterId;

    /**
     * Instance constructor.
     *
     * @param transactionId transaction id
     * @param id            flow id
     * @param cookie        cookie of the flow
     * @param switchId      switch ID for flow installation
     * @param meterId       meter id
     * @throws IllegalArgumentException if any of parameters parameters is null
     */
    @JsonCreator
    public RemoveFlow(@JsonProperty(TRANSACTION_ID) final Long transactionId,
                      @JsonProperty(FLOW_ID) final String id,
                      @JsonProperty("cookie") final Long cookie,
                      @JsonProperty("switch_id") final String switchId,
                      @JsonProperty("meter_id") Long meterId) {
        super(transactionId, id, cookie, switchId);
        setMeterId(meterId);
    }

    /**
     * Returns meter id for the flow.
     *
     * @return meter id for the flow
     */
    public Long getMeterId() {
        return meterId;
    }

    /**
     * Sets meter id for the flow.
     *
     * @param meterId id for the flow
     */
    public void setMeterId(final Long meterId) {
        if (meterId != null && meterId < 0L) {
            throw new IllegalArgumentException("need to set non negative meter_id");
        }
        this.meterId = meterId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TRANSACTION_ID, transactionId)
                .add(FLOW_ID, id)
                .add("cookie", cookie)
                .add("switch_id", switchId)
                .add("meter_id", meterId)
                .toString();
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

        RemoveFlow that = (RemoveFlow) object;
        return Objects.equals(getTransactionId(), that.getTransactionId()) &&
                Objects.equals(getId(), that.getId()) &&
                Objects.equals(getCookie(), that.getCookie()) &&
                Objects.equals(getSwitchId(), that.getSwitchId()) &&
                Objects.equals(getMeterId(), that.getMeterId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId, id, cookie, switchId, meterId);
    }
}

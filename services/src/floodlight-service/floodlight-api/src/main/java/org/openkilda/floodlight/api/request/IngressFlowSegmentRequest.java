/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.api.request;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.UUID;

@JsonIgnoreProperties({"switch_id"})
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class IngressFlowSegmentRequest extends IngressFlowSegmentBase {
    @JsonProperty("isl_port")
    protected final int islPort;

    @JsonProperty("encapsulation")
    protected final FlowTransitEncapsulation encapsulation;

    @SuppressWarnings("squid:S00107")
    protected IngressFlowSegmentRequest(
            MessageContext context, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, SwitchId egressSwitchId, int islPort,
            @NonNull FlowTransitEncapsulation encapsulation, boolean removeCustomerPortSharedCatchRule) {
        super(context, commandId, metadata, endpoint, meterConfig, egressSwitchId, removeCustomerPortSharedCatchRule);

        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    protected IngressFlowSegmentRequest(@NonNull IngressFlowSegmentRequest other, @NonNull UUID commandId) {
        this(
                other.messageContext, commandId, other.metadata, other.endpoint, other.meterConfig,
                other.egressSwitchId, other.islPort, other.encapsulation, other.removeCustomerPortSharedCatchRule);
    }
}

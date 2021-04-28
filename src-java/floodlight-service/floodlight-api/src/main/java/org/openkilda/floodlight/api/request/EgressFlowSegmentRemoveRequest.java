/* Copyright 2021 Telstra Open Source
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
import org.openkilda.model.MirrorConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EgressFlowSegmentRemoveRequest extends EgressFlowSegmentRequest {
    @JsonCreator
    @Builder(toBuilder = true)
    public EgressFlowSegmentRemoveRequest(
            @JsonProperty("message_context") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("ingress_endpoint") FlowEndpoint ingressEndpoint,
            @JsonProperty("isl_port") int islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation,
            @JsonProperty("mirror_config") MirrorConfig mirrorConfig) {
        super(messageContext, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation, mirrorConfig);
    }

    public EgressFlowSegmentRemoveRequest(EgressFlowSegmentRequest other, UUID commandId) {
        super(other, commandId);
    }

    @JsonIgnore
    @Override
    public boolean isRemoveRequest() {
        return true;
    }
}

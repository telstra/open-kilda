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
public class SharedIngressFlowSegmentOuterVlanMatchRequest extends FlowSegmentRequest {
    @JsonProperty("endpoint")
    protected final FlowEndpoint endpoint;

    protected SharedIngressFlowSegmentOuterVlanMatchRequest(
            MessageContext messageContext,  UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint) {
        super(messageContext, endpoint.getSwitchId(), commandId, metadata);
        this.endpoint = endpoint;
    }

    protected SharedIngressFlowSegmentOuterVlanMatchRequest(
            SharedIngressFlowSegmentOuterVlanMatchRequest other, UUID commandId) {
        this(other.messageContext, commandId, other.metadata, other.endpoint);
    }
}

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.UUID;

public class SharedIngressFlowSegmentOuterVlanMatchInstallRequest
        extends SharedIngressFlowSegmentOuterVlanMatchRequest {
    @JsonCreator
    @Builder(toBuilder = true)
    public SharedIngressFlowSegmentOuterVlanMatchInstallRequest(
            @JsonProperty("metadata") MessageContext messageContext,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint) {
        super(messageContext, commandId, metadata, endpoint);
    }

    public SharedIngressFlowSegmentOuterVlanMatchInstallRequest(
            SharedIngressFlowSegmentOuterVlanMatchRequest other, UUID commandId) {
        super(other, commandId);
    }

    @JsonIgnore
    @Override
    public boolean isInstallRequest() {
        return true;
    }
}

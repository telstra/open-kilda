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

package org.openkilda.floodlight.api.request.factory;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SharedIngressFlowSegmentOuterVlanMatchInstallRequest;
import org.openkilda.floodlight.api.request.SharedIngressFlowSegmentOuterVlanMatchRemoveRequest;
import org.openkilda.floodlight.api.request.SharedIngressFlowSegmentOuterVlanMatchRequest;
import org.openkilda.floodlight.api.request.SharedIngressFlowSegmentOuterVlanMatchVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;

import lombok.Builder;

import java.util.Optional;
import java.util.UUID;

public class SharedIngressFlowSegmentOuterVlanMatchRequestFactory extends AbstractFlowSegmentRequestFactory {
    private final SharedIngressFlowSegmentOuterVlanMatchRequest requestBlank;

    @Builder
    public SharedIngressFlowSegmentOuterVlanMatchRequestFactory(
            MessageContext messageContext, FlowSegmentMetadata metadata, FlowEndpoint endpoint) {
        this(new RequestBlank(messageContext, metadata, endpoint));
    }

    private SharedIngressFlowSegmentOuterVlanMatchRequestFactory(
            SharedIngressFlowSegmentOuterVlanMatchRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public Optional<? extends FlowSegmentRequest> makeInstallRequest(UUID commandId) {
        return Optional.of(new SharedIngressFlowSegmentOuterVlanMatchInstallRequest(requestBlank, commandId));
    }

    @Override
    public Optional<? extends FlowSegmentRequest> makeRemoveRequest(UUID commandId) {
        return Optional.of(new SharedIngressFlowSegmentOuterVlanMatchRemoveRequest(requestBlank, commandId));
    }

    @Override
    public Optional<? extends FlowSegmentRequest> makeVerifyRequest(UUID commandId) {
        return Optional.of(new SharedIngressFlowSegmentOuterVlanMatchVerifyRequest(requestBlank, commandId));
    }

    private static class RequestBlank extends SharedIngressFlowSegmentOuterVlanMatchRequest {
        public RequestBlank(MessageContext messageContext, FlowSegmentMetadata metadata, FlowEndpoint endpoint) {
            super(messageContext, dummyCommandId, metadata, endpoint);
        }
    }
}

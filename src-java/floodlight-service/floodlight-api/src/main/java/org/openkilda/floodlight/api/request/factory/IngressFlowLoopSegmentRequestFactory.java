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

import org.openkilda.floodlight.api.request.IngressFlowLoopSegmentInstallRequest;
import org.openkilda.floodlight.api.request.IngressFlowLoopSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.IngressFlowLoopSegmentRequest;
import org.openkilda.floodlight.api.request.IngressFlowLoopSegmentVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;

import lombok.Builder;

import java.util.UUID;

public class IngressFlowLoopSegmentRequestFactory extends FlowSegmentRequestFactory {
    private final IngressFlowLoopSegmentRequest requestBlank;

    @Builder
    @SuppressWarnings("squid:S00107")
    public IngressFlowLoopSegmentRequestFactory(
            MessageContext messageContext, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint) {
        this(new RequestBlank(messageContext, metadata, endpoint));
    }

    private IngressFlowLoopSegmentRequestFactory(IngressFlowLoopSegmentRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public IngressFlowLoopSegmentRequest makeInstallRequest(UUID commandId) {
        return new IngressFlowLoopSegmentInstallRequest(requestBlank, commandId);
    }

    @Override
    public IngressFlowLoopSegmentRequest makeRemoveRequest(UUID commandId) {
        return new IngressFlowLoopSegmentRemoveRequest(requestBlank, commandId);
    }

    @Override
    public IngressFlowLoopSegmentRequest makeVerifyRequest(UUID commandId) {
        return new IngressFlowLoopSegmentVerifyRequest(requestBlank, commandId);
    }

    private static class RequestBlank extends IngressFlowLoopSegmentRequest {
        RequestBlank(
                MessageContext context, FlowSegmentMetadata metadata,
                FlowEndpoint endpoint) {
            super(context, dummyCommandId, metadata, endpoint);
        }
    }
}

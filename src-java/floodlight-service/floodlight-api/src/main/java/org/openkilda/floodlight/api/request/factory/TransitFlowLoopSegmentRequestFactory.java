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

import org.openkilda.floodlight.api.request.TransitFlowLoopSegmentInstallRequest;
import org.openkilda.floodlight.api.request.TransitFlowLoopSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.TransitFlowLoopSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.TransitFlowSegmentRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import lombok.Builder;

import java.util.UUID;

public class TransitFlowLoopSegmentRequestFactory extends FlowSegmentRequestFactory {
    private final TransitFlowSegmentRequest requestBlank;

    @Builder
    public TransitFlowLoopSegmentRequestFactory(
            MessageContext messageContext, SwitchId switchId, FlowSegmentMetadata metadata,
            int port, FlowTransitEncapsulation encapsulation) {
        this(new RequestBlank(messageContext, switchId, metadata, port, encapsulation));
    }

    private TransitFlowLoopSegmentRequestFactory(TransitFlowSegmentRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public TransitFlowLoopSegmentInstallRequest makeInstallRequest(UUID commandId) {
        return new TransitFlowLoopSegmentInstallRequest(requestBlank, commandId);
    }

    @Override
    public TransitFlowLoopSegmentRemoveRequest makeRemoveRequest(UUID commandId) {
        return new TransitFlowLoopSegmentRemoveRequest(requestBlank, commandId);
    }

    @Override
    public TransitFlowLoopSegmentVerifyRequest makeVerifyRequest(UUID commandId) {
        return new TransitFlowLoopSegmentVerifyRequest(requestBlank, commandId);
    }

    private static class RequestBlank extends TransitFlowSegmentRequest {
        RequestBlank(
                MessageContext context, SwitchId switchId, FlowSegmentMetadata metadata, int port,
                FlowTransitEncapsulation encapsulation) {
            super(context, switchId, dummyCommandId, metadata, port, port, encapsulation);
        }
    }
}

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

package org.openkilda.floodlight.api.request.factory;

import org.openkilda.floodlight.api.request.EgressFlowSegmentRequest;
import org.openkilda.floodlight.api.request.EgressMirrorFlowSegmentInstallRequest;
import org.openkilda.floodlight.api.request.EgressMirrorFlowSegmentRemoveRequest;
import org.openkilda.floodlight.api.request.EgressMirrorFlowSegmentVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MirrorConfig;

import lombok.Builder;

import java.util.UUID;

public class EgressMirrorFlowSegmentRequestFactory extends FlowSegmentRequestFactory {
    private final EgressFlowSegmentRequest requestBlank;

    @Builder
    public EgressMirrorFlowSegmentRequestFactory(
            MessageContext messageContext, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
            FlowEndpoint ingressEndpoint, int islPort, FlowTransitEncapsulation encapsulation,
            MirrorConfig mirrorConfig) {
        this(new RequestBlank(messageContext, metadata, endpoint, ingressEndpoint, islPort, encapsulation,
                mirrorConfig));
    }

    private EgressMirrorFlowSegmentRequestFactory(EgressFlowSegmentRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public EgressFlowSegmentRequest makeInstallRequest(UUID commandId) {
        return new EgressMirrorFlowSegmentInstallRequest(requestBlank, commandId);
    }

    @Override
    public EgressFlowSegmentRequest makeRemoveRequest(UUID commandId) {
        return new EgressMirrorFlowSegmentRemoveRequest(requestBlank, commandId);
    }

    @Override
    public EgressFlowSegmentRequest makeVerifyRequest(UUID commandId) {
        return new EgressMirrorFlowSegmentVerifyRequest(requestBlank, commandId);
    }

    private static class RequestBlank extends EgressFlowSegmentRequest {
        RequestBlank(
                MessageContext messageContext, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
                FlowEndpoint ingressEndpoint, int islPort, FlowTransitEncapsulation encapsulation,
                MirrorConfig mirrorConfig) {
            super(messageContext, dummyCommandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation,
                    mirrorConfig);
        }
    }
}

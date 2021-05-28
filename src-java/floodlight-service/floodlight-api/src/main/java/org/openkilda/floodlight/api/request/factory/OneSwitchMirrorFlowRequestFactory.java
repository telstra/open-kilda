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

import org.openkilda.floodlight.api.request.OneSwitchFlowRequest;
import org.openkilda.floodlight.api.request.OneSwitchMirrorFlowInstallRequest;
import org.openkilda.floodlight.api.request.OneSwitchMirrorFlowRemoveRequest;
import org.openkilda.floodlight.api.request.OneSwitchMirrorFlowVerifyRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MirrorConfig;

import lombok.Builder;

import java.util.UUID;

public class OneSwitchMirrorFlowRequestFactory extends FlowSegmentRequestFactory {
    private final OneSwitchFlowRequest requestBlank;

    @Builder
    public OneSwitchMirrorFlowRequestFactory(
            MessageContext messageContext, FlowSegmentMetadata metadata, FlowEndpoint endpoint, MeterConfig meterConfig,
            FlowEndpoint egressEndpoint, RulesContext rulesContext, MirrorConfig mirrorConfig) {
        this(new RequestBlank(messageContext, metadata, endpoint, meterConfig, egressEndpoint,
                rulesContext, mirrorConfig));
    }

    private OneSwitchMirrorFlowRequestFactory(OneSwitchFlowRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public OneSwitchFlowRequest makeInstallRequest(UUID commandId) {
        return new OneSwitchMirrorFlowInstallRequest(requestBlank, commandId);
    }

    @Override
    public OneSwitchFlowRequest makeRemoveRequest(UUID commandId) {
        return new OneSwitchMirrorFlowRemoveRequest(requestBlank, commandId);
    }

    @Override
    public OneSwitchFlowRequest makeVerifyRequest(UUID commandId) {
        return new OneSwitchMirrorFlowVerifyRequest(requestBlank, commandId);
    }

    private static class RequestBlank extends OneSwitchFlowRequest {
        RequestBlank(
                MessageContext context, FlowSegmentMetadata metadata, FlowEndpoint endpoint, MeterConfig meterConfig,
                FlowEndpoint egressEndpoint, RulesContext rulesContext, MirrorConfig mirrorConfig) {
            super(context, dummyCommandId, metadata, endpoint, meterConfig, egressEndpoint, rulesContext, mirrorConfig);
        }
    }
}

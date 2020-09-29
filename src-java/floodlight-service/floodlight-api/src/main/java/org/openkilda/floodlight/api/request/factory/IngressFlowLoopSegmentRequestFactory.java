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
import org.openkilda.floodlight.api.request.IngressFlowLoopSegmentVerifyRequest;
import org.openkilda.floodlight.api.request.IngressFlowSegmentRequest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.SwitchId;

import lombok.Builder;

import java.util.UUID;

public class IngressFlowLoopSegmentRequestFactory extends FlowSegmentRequestFactory {
    private final IngressFlowSegmentRequest requestBlank;

    @Builder
    @SuppressWarnings("squid:S00107")
    public IngressFlowLoopSegmentRequestFactory(
            MessageContext messageContext, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, SwitchId egressSwitchId, int islPort,
            FlowTransitEncapsulation encapsulation, RulesContext rulesContext) {
        this(new RequestBlank(messageContext, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation,
                rulesContext));
    }

    private IngressFlowLoopSegmentRequestFactory(IngressFlowSegmentRequest requestBlank) {
        super(requestBlank);
        this.requestBlank = requestBlank;
    }

    @Override
    public IngressFlowSegmentRequest makeInstallRequest(UUID commandId) {
        return new IngressFlowLoopSegmentInstallRequest(requestBlank, commandId);
    }

    @Override
    public IngressFlowSegmentRequest makeRemoveRequest(UUID commandId) {
        return new IngressFlowLoopSegmentRemoveRequest(requestBlank, commandId);
    }

    @Override
    public IngressFlowSegmentRequest makeVerifyRequest(UUID commandId) {
        return new IngressFlowLoopSegmentVerifyRequest(requestBlank, commandId);
    }

    private static class RequestBlank extends IngressFlowSegmentRequest {
        RequestBlank(
                MessageContext context, FlowSegmentMetadata metadata,
                FlowEndpoint endpoint, MeterConfig meterConfig, SwitchId egressSwitchId, int islPort,
                FlowTransitEncapsulation encapsulation, RulesContext rulesContext) {
            super(context, dummyCommandId, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation,
                    rulesContext);
        }
    }
}

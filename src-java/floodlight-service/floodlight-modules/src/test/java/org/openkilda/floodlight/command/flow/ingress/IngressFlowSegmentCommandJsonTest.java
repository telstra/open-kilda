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

package org.openkilda.floodlight.command.flow.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.floodlight.api.request.IngressFlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.IngressFlowSegmentRequestFactory;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import java.util.HashSet;

abstract class IngressFlowSegmentCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<IngressFlowSegmentRequest> {
    protected void verifyPayload(IngressFlowSegmentRequest request, IngressFlowSegmentCommand command) {
        assertEquals(request.getMessageContext(), command.getMessageContext());
        assertEquals(request.getSwitchId(), command.getSwitchId());
        assertEquals(request.getCommandId(), command.getCommandId());
        assertEquals(request.getMetadata(), command.getMetadata());
        assertEquals(request.getEndpoint(), command.getEndpoint());
        assertEquals(request.getMeterConfig(), command.getMeterConfig());
        assertEquals(request.getIslPort(), command.getIslPort());
        assertEquals(request.getEncapsulation(), command.getEncapsulation());
        assertEquals(request.getRulesContext(), command.getRulesContext());
    }

    @Override
    protected IngressFlowSegmentRequest makeRequest() {
        IngressFlowSegmentRequestFactory factory = new IngressFlowSegmentRequestFactory(
                new MessageContext(),
                new FlowSegmentMetadata(
                        "ingress-flow-segment-json-remove-request", new Cookie(1)),
                new FlowEndpoint(new SwitchId(2), 3, 4),
                new MeterConfig(new MeterId(6), 7000),
                new SwitchId(20),
                8,
                new FlowTransitEncapsulation(9, FlowEncapsulationType.TRANSIT_VLAN),
                RulesContext.builder().build(),
                MirrorConfig.builder().build(),
                new HashSet<>());
        return makeRequest(factory);
    }

    protected abstract IngressFlowSegmentRequest makeRequest(IngressFlowSegmentRequestFactory factory);
}

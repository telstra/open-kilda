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

package org.openkilda.floodlight.command.flow.egress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.floodlight.api.request.EgressFlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.EgressFlowSegmentRequestFactory;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

abstract class EgressFlowSegmentCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<EgressFlowSegmentRequest> {
    protected void verifyPayload(EgressFlowSegmentRequest request, EgressFlowSegmentCommand command) {
        assertEquals(request.getMessageContext(), command.getMessageContext());
        assertEquals(request.getSwitchId(), command.getSwitchId());
        assertEquals(request.getCommandId(), command.getCommandId());
        assertEquals(request.getMetadata(), command.getMetadata());
        assertEquals(request.getEndpoint(), command.getEndpoint());
        assertEquals(request.getIngressEndpoint(), command.getIngressEndpoint());
        assertEquals(request.getIslPort(), command.getIngressIslPort());
        assertEquals(request.getEncapsulation(), command.getEncapsulation());
    }

    @Override
    protected EgressFlowSegmentRequest makeRequest() {
        SwitchId swId = new SwitchId(1);
        EgressFlowSegmentRequestFactory factory = new EgressFlowSegmentRequestFactory(
                new MessageContext(),
                new FlowSegmentMetadata("egress-flow-segment-install-request", new Cookie(2)),
                new FlowEndpoint(swId, 3, 4),
                new FlowEndpoint(new SwitchId(swId.toLong() + 1), 6, 7),
                9,
                new FlowTransitEncapsulation(10, FlowEncapsulationType.TRANSIT_VLAN),
                MirrorConfig.builder().build());

        return makeRequest(factory);
    }

    protected abstract EgressFlowSegmentRequest makeRequest(EgressFlowSegmentRequestFactory blank);
}

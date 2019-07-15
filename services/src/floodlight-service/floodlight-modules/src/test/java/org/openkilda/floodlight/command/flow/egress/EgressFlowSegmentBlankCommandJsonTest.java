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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.api.request.EgressFlowSegmentBlankRequest;
import org.openkilda.floodlight.api.request.EgressFlowSegmentBlankRequest.BlankResolver;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

import java.util.UUID;

abstract class EgressFlowSegmentBlankCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<EgressFlowSegmentBlankRequest> {
    protected void verifyPayload(EgressFlowSegmentBlankRequest request, EgressFlowSegmentBlankCommand command) {
        Assert.assertEquals(request.getMessageContext(), command.getMessageContext());
        Assert.assertEquals(request.getSwitchId(), command.getSwitchId());
        Assert.assertEquals(request.getCommandId(), command.getCommandId());
        Assert.assertEquals(request.getMetadata(), command.getMetadata());
        Assert.assertEquals(request.getEndpoint(), command.getEndpoint());
        Assert.assertEquals(request.getIngressEndpoint(), command.getIngressEndpoint());
        Assert.assertEquals(request.getIslPort(), command.getIngressIslPort());
        Assert.assertEquals(request.getEncapsulation(), command.getEncapsulation());
    }

    @Override
    protected EgressFlowSegmentBlankRequest makeRequest() {
        SwitchId swId = new SwitchId(1);
        BlankResolver blank = EgressFlowSegmentBlankRequest.makeResolver(
                new MessageContext(),
                UUID.randomUUID(),
                new FlowSegmentMetadata("egress-flow-segment-install-request", new Cookie(2), false),
                new FlowEndpoint(swId, 3, 4),
                new FlowEndpoint(new SwitchId(swId.toLong() + 1), 6, 7),
                9,
                new FlowTransitEncapsulation(10, FlowEncapsulationType.TRANSIT_VLAN));

        return makeRequest(blank);
    }

    protected abstract EgressFlowSegmentBlankRequest makeRequest(BlankResolver blank);
}

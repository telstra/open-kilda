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

package org.openkilda.floodlight.command.flow.transit;

import org.openkilda.floodlight.api.request.TransitFlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.TransitFlowSegmentRequestFactory;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

abstract class TransitFlowSegmentCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<TransitFlowSegmentRequest> {
    protected void verifyPayload(TransitFlowSegmentRequest request, TransitFlowSegmentCommand command) {
        Assert.assertEquals(request.getMessageContext(), command.getMessageContext());
        Assert.assertEquals(request.getSwitchId(), command.getSwitchId());
        Assert.assertEquals(request.getCommandId(), command.getCommandId());
        Assert.assertEquals(request.getMetadata(), command.getMetadata());
        Assert.assertEquals(request.getIngressIslPort(), command.getIngressIslPort());
        Assert.assertEquals(request.getEgressIslPort(), command.getEgressIslPort());
        Assert.assertEquals(request.getEncapsulation(), command.getEncapsulation());
    }

    @Override
    protected TransitFlowSegmentRequest makeRequest() {
        TransitFlowSegmentRequestFactory factory = new TransitFlowSegmentRequestFactory(
                new MessageContext(),
                new SwitchId(1),
                new FlowSegmentMetadata("transit-flow-segment-install-request", new Cookie(2), false),
                3, 4,
                new FlowTransitEncapsulation(5, FlowEncapsulationType.TRANSIT_VLAN));
        return makeRequest(factory);
    }

    protected abstract TransitFlowSegmentRequest makeRequest(TransitFlowSegmentRequestFactory factory);
}

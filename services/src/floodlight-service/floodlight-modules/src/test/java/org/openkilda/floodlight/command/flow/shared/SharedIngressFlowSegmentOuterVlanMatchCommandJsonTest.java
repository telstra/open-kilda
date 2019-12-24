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

package org.openkilda.floodlight.command.flow.shared;

import org.openkilda.floodlight.api.request.SharedIngressFlowSegmentOuterVlanMatchRequest;
import org.openkilda.floodlight.api.request.factory.SharedIngressFlowSegmentOuterVlanMatchRequestFactory;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;

import org.junit.Assert;

abstract class SharedIngressFlowSegmentOuterVlanMatchCommandJsonTest extends
        AbstractSpeakerCommandJsonTest<SharedIngressFlowSegmentOuterVlanMatchRequest> {

    @Override
    protected void verify(SharedIngressFlowSegmentOuterVlanMatchRequest request,
                          SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof SharedIngressFlowSegmentOuterVlanMatchCommand);
        verifyPayload(request, (SharedIngressFlowSegmentOuterVlanMatchCommand) rawCommand);
    }

    protected void verifyPayload(
            SharedIngressFlowSegmentOuterVlanMatchRequest request,
            SharedIngressFlowSegmentOuterVlanMatchCommand command) {
        Assert.assertEquals(request.getMessageContext(), command.getMessageContext());
        Assert.assertEquals(request.getMetadata(), command.getMetadata());
        Assert.assertEquals(request.getEndpoint(), command.getEndpoint());
    }

    @Override
    protected SharedIngressFlowSegmentOuterVlanMatchRequest makeRequest() {
        SharedIngressFlowSegmentOuterVlanMatchRequestFactory factory;
        factory = new SharedIngressFlowSegmentOuterVlanMatchRequestFactory(
                new MessageContext(),
                new FlowSegmentMetadata("json-test-dummy-flow-id", new Cookie(1), true),
                new FlowEndpoint(new SwitchId(2), 3, 32));
        return makeRequest(factory);
    }

    protected abstract SharedIngressFlowSegmentOuterVlanMatchRequest makeRequest(
            SharedIngressFlowSegmentOuterVlanMatchRequestFactory factory);
}

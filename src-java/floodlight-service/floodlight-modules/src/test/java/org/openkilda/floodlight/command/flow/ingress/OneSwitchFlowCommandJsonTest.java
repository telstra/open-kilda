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

package org.openkilda.floodlight.command.flow.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.floodlight.api.request.OneSwitchFlowRequest;
import org.openkilda.floodlight.api.request.factory.OneSwitchFlowRequestFactory;
import org.openkilda.floodlight.command.AbstractSpeakerCommandJsonTest;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import java.util.HashSet;

abstract class OneSwitchFlowCommandJsonTest
        extends AbstractSpeakerCommandJsonTest<OneSwitchFlowRequest> {
    protected void verifyPayload(OneSwitchFlowRequest request, OneSwitchFlowCommand command) {
        assertEquals(request.getMessageContext(), command.getMessageContext());
        assertEquals(request.getSwitchId(), command.getSwitchId());
        assertEquals(request.getCommandId(), command.getCommandId());
        assertEquals(request.getEndpoint(), command.getEndpoint());
        assertEquals(request.getEndpoint(), command.getEndpoint());
        assertEquals(request.getMeterConfig(), command.getMeterConfig());
        assertEquals(request.getEgressEndpoint(), command.getEgressEndpoint());
        assertEquals(request.getRulesContext(), command.getRulesContext());
    }

    @Override
    protected OneSwitchFlowRequest makeRequest() {
        SwitchId swId = new SwitchId(1);
        OneSwitchFlowRequestFactory factory = new OneSwitchFlowRequestFactory(
                new MessageContext(),
                new FlowSegmentMetadata("single-switch-flow-install-request", new Cookie(2), false),
                new FlowEndpoint(swId, 3, 4),
                new MeterConfig(new MeterId(6), 7000),
                new FlowEndpoint(swId, 8, 9),
                RulesContext.builder().build(),
                MirrorConfig.builder().build(),
                new HashSet<>());
        return makeRequest(factory);
    }

    protected abstract OneSwitchFlowRequest makeRequest(OneSwitchFlowRequestFactory factory);
}

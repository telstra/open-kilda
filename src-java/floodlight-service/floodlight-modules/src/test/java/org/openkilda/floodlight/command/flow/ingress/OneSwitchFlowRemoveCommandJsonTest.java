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

import org.openkilda.floodlight.api.request.OneSwitchFlowRequest;
import org.openkilda.floodlight.api.request.factory.OneSwitchFlowRequestFactory;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;

import org.junit.Assert;

public class OneSwitchFlowRemoveCommandJsonTest extends OneSwitchFlowCommandJsonTest {

    @Override
    protected void verify(OneSwitchFlowRequest request, SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof OneSwitchFlowRemoveCommand);
        verifyPayload(request, (OneSwitchFlowRemoveCommand) rawCommand);
    }

    @Override
    protected OneSwitchFlowRequest makeRequest(OneSwitchFlowRequestFactory factory) {
        return factory.makeRemoveRequest(commandIdGenerator.generate());
    }
}

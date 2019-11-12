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

import org.openkilda.floodlight.api.request.EgressFlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.EgressFlowSegmentRequestFactory;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandReport;

import org.junit.Assert;

public class EgressFlowSegmentInstallCommandJsonTest extends EgressFlowSegmentCommandJsonTest {
    @Override
    protected void verify(EgressFlowSegmentRequest request, SpeakerCommand<? extends SpeakerCommandReport> rawCommand) {
        Assert.assertTrue(rawCommand instanceof EgressFlowSegmentInstallCommand);
        verifyPayload(request, (EgressFlowSegmentInstallCommand) rawCommand);
    }

    @Override
    protected EgressFlowSegmentRequest makeRequest(EgressFlowSegmentRequestFactory blank) {
        return blank.makeInstallRequest(commandIdGenerator.generate());
    }
}

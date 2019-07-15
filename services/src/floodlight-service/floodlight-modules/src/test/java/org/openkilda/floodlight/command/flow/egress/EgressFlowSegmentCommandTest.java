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

import org.openkilda.floodlight.command.AbstractSpeakerCommandTest;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;

import java.util.concurrent.CompletableFuture;

abstract class EgressFlowSegmentCommandTest extends AbstractSpeakerCommandTest {
    @Test
    public void errorOnFlowMod() {
        switchFeaturesSetup(sw, true);
        replayAll();

        FlowEndpoint ingressEndpoint = new FlowEndpoint(mapSwitchId(dpId), 1, 0);
        EgressFlowSegmentCommand command = makeCommand(
                endpointEgressZeroVlan, ingressEndpoint, encapsulationVlan);
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        getWriteRecord(0).getFuture()
                .completeExceptionally(new SwitchErrorResponseException(
                        dpIdNext, of.errorMsgs().buildBadRequestErrorMsg().setCode(OFBadRequestCode.BAD_LEN).build()));
        verifyErrorCompletion(result, SwitchOperationException.class);
    }

    protected void executeCommand(EgressFlowSegmentCommand command, int writeCount) throws Exception {
        replayAll();

        final CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyWriteCount(writeCount);
        verifySuccessCompletion(result);
    }

    protected abstract EgressFlowSegmentCommand makeCommand(
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, FlowTransitEncapsulation encapsulation);
}

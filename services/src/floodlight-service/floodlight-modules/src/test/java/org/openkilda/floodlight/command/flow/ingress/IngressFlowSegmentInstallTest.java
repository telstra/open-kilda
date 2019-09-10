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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.reset;

import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.meter.MeterInstallCommand;
import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;

import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;

import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class IngressFlowSegmentInstallTest extends IngressCommandTest {
    @Test
    public void noMeterRequested() throws Exception {
        switchFeaturesSetup(sw, true);
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().meterConfig(null).build();
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifySuccessCompletion(result);
        verifyWriteCount(1);
        verifyNoMeterCall((OFFlowAdd) getWriteRecord(0).getRequest());
    }

    @Test
    public void errorNoMeterSupport() throws Exception {
        switchFeaturesSetup(sw, false);
        expectMeter(new UnsupportedSwitchOperationException(dpIdNext, "Switch doesn't support meters (unit-test)"));
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().build();
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyWriteCount(1);
        verifySuccessCompletion(result);
        verifyNoMeterCall((OFFlowAdd) getWriteRecord(0).getRequest());
    }

    @Test
    public void errorOnMeterManipulation() {
        switchFeaturesSetup(sw, true);
        expectMeter(new SwitchErrorResponseException(dpIdNext, "fake fail to install meter error"));
        reset(sessionService);
        reset(session);
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().build();
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyErrorCompletion(result, SwitchErrorResponseException.class);
    }

    @Test
    public void errorOnFlowMod() {
        switchFeaturesSetup(sw, true);
        expectMeter();
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().build();
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        getWriteRecord(0).getFuture()
                .completeExceptionally(new SwitchErrorResponseException(
                        dpIdNext, of.errorMsgs().buildBadRequestErrorMsg().setCode(OFBadRequestCode.BAD_LEN).build()));
        verifyErrorCompletion(result, SwitchOperationException.class);
    }

    private void verifyNoMeterCall(OFFlowAdd request) {
        for (OFInstruction instructiuon : request.getInstructions()) {
            if (instructiuon instanceof OFInstructionMeter) {
                Assert.fail("Found unexpected meter call");
            } else if (instructiuon instanceof OFInstructionApplyActions) {
                verifyNoMeterCall(((OFInstructionApplyActions) instructiuon).getActions());
            } else if (instructiuon instanceof OFInstructionWriteActions) {
                verifyNoMeterCall(((OFInstructionWriteActions) instructiuon).getActions());
            }
        }
    }

    private void verifyNoMeterCall(List<OFAction> actions) {
        for (OFAction entry : actions) {
            if (entry instanceof OFActionMeter) {
                Assert.fail("Found unexpected meter call");
            }
        }
    }

    @Override
    protected void expectMeter(MeterReport report) {
        expect(commandProcessor.chain(anyObject(MeterInstallCommand.class)))
                .andReturn(CompletableFuture.completedFuture(report));
    }
}

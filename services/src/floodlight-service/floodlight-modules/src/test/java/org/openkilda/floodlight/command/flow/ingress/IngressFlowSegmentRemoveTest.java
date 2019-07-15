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

import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.utils.OfAdapter;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.concurrent.CompletableFuture;

abstract class IngressFlowSegmentRemoveTest extends IngressCommandTest {
    @Test
    public void noMeterRequested() throws Exception {
        switchFeaturesSetup(sw, false);
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().meterConfig(null).build();
        verifySuccessCompletion(command.execute(commandProcessor));
    }

    @Test
    public void errorNoMeterSupport() throws Exception {
        switchFeaturesSetup(sw, false);
        expectMeterRemove(
                new UnsupportedSwitchOperationException(dpIdNext, "Switch doesn't support meters (unit-test)"));
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().build();
        verifySuccessCompletion(command.execute(commandProcessor));
    }

    @Test
    public void errorOnMeterManipulation() {
        switchFeaturesSetup(sw, true);
        expectMeterRemove(new SwitchErrorResponseException(dpIdNext, "fake fail to install meter error"));
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().build();
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);
        verifyErrorCompletion(result, SwitchErrorResponseException.class);
    }

    @Test
    public void errorOnFlowMod() {
        switchFeaturesSetup(sw, true);
        replayAll();

        IngressFlowSegmentCommand command = getCommandBuilder().build();
        CompletableFuture<FlowSegmentReport> result = command.execute(commandProcessor);

        getWriteRecord(0).getFuture()
                .completeExceptionally(new SwitchErrorResponseException(
                        dpIdNext, of.errorMsgs().buildBadRequestErrorMsg().setCode(OFBadRequestCode.BAD_LEN).build()));
        verifyErrorCompletion(result, SwitchOperationException.class);
    }

    protected void verifyOuterVlanMatchRemove(IngressFlowSegmentCommand command, OFMessage actual) {
        OFFlowMod expect = of.buildFlowDeleteStrict()
                .setCookie(U64.of(command.getCookie().getValue()))
                .setPriority(IngressFlowSegmentRemoveCommand.FLOW_PRIORITY)
                .setMatch(OfAdapter.INSTANCE.matchVlanId(of, of.buildMatch(), command.getEndpoint().getVlanId())
                                  .setExact(MatchField.IN_PORT, OFPort.of(command.getEndpoint().getPortNumber()))
                                  .build())
                .build();
        verifyOfMessageEquals(expect, actual);
    }

    @Override
    protected void expectMeter() {
        expectMeterRemove();
    }
}

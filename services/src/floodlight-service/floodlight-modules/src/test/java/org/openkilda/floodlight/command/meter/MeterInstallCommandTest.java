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

package org.openkilda.floodlight.command.meter;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.reset;

import org.openkilda.floodlight.command.AbstractSpeakerCommandTest;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchErrorResponseException;
import org.openkilda.floodlight.error.SwitchMeterConflictException;
import org.openkilda.floodlight.error.SwitchMissingMeterException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.of.MeterSchema;

import net.floodlightcontroller.core.SwitchDisconnectedException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFMeterModCommand;
import org.projectfloodlight.openflow.protocol.OFMeterModFailedCode;

import java.util.concurrent.CompletableFuture;

public class MeterInstallCommandTest extends AbstractSpeakerCommandTest {
    private MessageContext messageContext = new MessageContext();
    private MeterInstallCommand command = new MeterInstallCommand(
            messageContext, mapSwitchId(dpId), meterConfig);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        expectSwitchDescription();
    }

    @Test
    public void happyPath() throws Throwable {
        switchFeaturesSetup(sw, true);
        replayAll();

        CompletableFuture<MeterInstallReport> result = command.execute(commandProcessor);
        verifySuccessCompletion(result);

        SessionWriteRecord write0 = getWriteRecord(0);
        Assert.assertTrue(write0.getRequest() instanceof OFMeterMod);
        OFMeterMod request = (OFMeterMod) write0.getRequest();
        Assert.assertEquals(OFMeterModCommand.ADD, request.getCommand());
    }

    @Test
    public void switchDoNotSupportMeters() throws Throwable {
        switchFeaturesSetup(sw, false);
        // command fail before interaction with session/sessionService so we should cancel their expectations
        reset(sessionService);
        reset(session);
        replayAll();

        CompletableFuture<MeterInstallReport> result = command.execute(commandProcessor);
        verifyErrorCompletion(result, UnsupportedSwitchOperationException.class);
    }

    @Test
    public void notConflictError() throws Throwable {
        switchFeaturesSetup(sw, true);
        replayAll();

        CompletableFuture<MeterInstallReport> result = command.execute(commandProcessor);

        SessionWriteRecord write0 = getWriteRecord(0);
        OFErrorMsg error = sw.getOFFactory().errorMsgs().buildBadRequestErrorMsg()
                .setCode(OFBadRequestCode.BAD_LEN)
                .build();
        write0.getFuture().completeExceptionally(new SessionErrorResponseException(sw.getId(), error));
        verifyErrorCompletion(result, SwitchErrorResponseException.class);
    }

    @Test
    public void conflictError() throws Throwable {
        switchFeaturesSetup(sw, true);
        expectVerifyMeter();
        replayAll();

        CompletableFuture<MeterInstallReport> result = processConflictError();
        verifySuccessCompletion(result);
    }

    @Test
    public void missingConflictError() throws Throwable {
        switchFeaturesSetup(sw, true);
        expectVerifyMeter(new SwitchMissingMeterException(dpIdNext, meterConfig.getId()));
        replayAll();

        CompletableFuture<MeterInstallReport> result = processConflictError();
        verifyErrorCompletion(result, SwitchMeterConflictException.class);
    }

    @Test
    public void conflictAndDisconnectError() throws Throwable {
        switchFeaturesSetup(sw, true);
        expectVerifyMeter(new SwitchDisconnectedException(dpIdNext));
        replayAll();

        CompletableFuture<MeterInstallReport> result = processConflictError();
        verifyErrorCompletion(result, SwitchDisconnectedException.class);
    }

    private CompletableFuture<MeterInstallReport> processConflictError() {
        CompletableFuture<MeterInstallReport> result = command.execute(commandProcessor);

        SessionWriteRecord write0 = getWriteRecord(0);
        OFErrorMsg error = sw.getOFFactory().errorMsgs().buildMeterModFailedErrorMsg()
                .setCode(OFMeterModFailedCode.METER_EXISTS)
                .build();
        write0.getFuture().completeExceptionally(new SessionErrorResponseException(sw.getId(), error));

        return result;
    }

    private void expectVerifyMeter() {
        MeterSchema schema = MeterSchema.builder()
                .datapath(mapSwitchId(dpId))
                .meterId(meterConfig.getId())
                .build();
        expectVerifyMeter(new MeterVerifyReport(command, schema));
    }

    private void expectVerifyMeter(Exception error) {
        MeterVerifyReport report = new MeterVerifyReport(command, error);
        expectVerifyMeter(report);
    }

    private void expectVerifyMeter(MeterVerifyReport report) {
        expect(commandProcessor.chain(anyObject(MeterVerifyCommand.class)))
                .andReturn(CompletableFuture.completedFuture(report));
    }
}

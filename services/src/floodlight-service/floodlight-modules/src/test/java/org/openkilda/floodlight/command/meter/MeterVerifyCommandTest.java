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

import org.openkilda.floodlight.command.AbstractSpeakerCommandTest;
import org.openkilda.floodlight.error.SwitchIncorrectMeterException;
import org.openkilda.floodlight.error.SwitchMissingMeterException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFMeterConfig;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MeterVerifyCommandTest extends AbstractSpeakerCommandTest {
    private final MeterConfig meterConfig = new MeterConfig(new MeterId(1), 2048);
    private final MeterVerifyCommand command = new MeterVerifyCommand(
            new MessageContext(), mapSwitchId(dpId), meterConfig);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        expectSwitchDescription();
    }

    @Override
    protected void prepareSessionService() {
        // MeterVerifyCommand do not use SessionService, it use generic IOFSwitch.writeStatsRequest
    }

    @Test
    public void validMeter() throws Exception {
        switchFeaturesSetup(sw, true);
        SettableFuture<List<OFMeterConfigStatsReply>> statsReply = setupMeterConfigStatsReply();
        replayAll();

        CompletableFuture<MeterVerifyReport> result = command.execute(commandProcessor);

        OFMeterConfig reply = sw.getOFFactory().buildMeterConfig()
                .setMeterId(meterConfig.getId().getValue())
                .setFlags(command.makeMeterFlags())
                .setEntries(command.makeMeterBands())
                .build();
        statsReply.set(wrapMeterStatsReply(reply));

        verifySuccessCompletion(result);
    }

    @Test
    public void invalidBands0() {
        switchFeaturesSetup(sw, true);
        SettableFuture<List<OFMeterConfigStatsReply>> statsReplyProxy = setupMeterConfigStatsReply();
        setupMeterConfigStatsReply();  // for command2
        replayAll();

        CompletableFuture<MeterVerifyReport> result = command.execute(commandProcessor);

        // make one more command with altered config, to produce meter config flags/bands
        MeterConfig invalidConfig = new MeterConfig(meterConfig.getId(), meterConfig.getBandwidth() + 1);
        MeterVerifyCommand command2 = new MeterVerifyCommand(
                command.getMessageContext(), command.getSwitchId(), invalidConfig);
        command2.execute(commandProcessor);  // must be executed, for let .setup() method to initialize all dependencies

        OFMeterConfig reply = sw.getOFFactory().buildMeterConfig()
                .setMeterId(meterConfig.getId().getValue())
                .setFlags(command2.makeMeterFlags())
                .setEntries(command2.makeMeterBands())
                .build();
        statsReplyProxy.set(wrapMeterStatsReply(reply));

        verifyErrorCompletion(result, SwitchIncorrectMeterException.class);
    }

    @Test
    public void meterNotFound() throws Exception {
        switchFeaturesSetup(sw, true);
        SettableFuture<List<OFMeterConfigStatsReply>> statsReplyProxy = setupMeterConfigStatsReply();
        replayAll();

        CompletableFuture<MeterVerifyReport> result = command.execute(commandProcessor);
        statsReplyProxy.set(ImmutableList.of(
                sw.getOFFactory().buildMeterConfigStatsReply()
                        .build()));

        verifyErrorCompletion(result, SwitchMissingMeterException.class);
    }

    @Test
    public void switchDoNotSupportMeters() throws Exception {
        switchFeaturesSetup(sw, false);
        replayAll();

        CompletableFuture<MeterVerifyReport> result = command.execute(commandProcessor);
        verifyErrorCompletion(result, UnsupportedSwitchOperationException.class);
    }

    private SettableFuture<List<OFMeterConfigStatsReply>> setupMeterConfigStatsReply() {
        SettableFuture<List<OFMeterConfigStatsReply>> meterStatsReply = SettableFuture.create();
        expect(sw.writeStatsRequest(anyObject(OFMeterConfigStatsRequest.class)))
                .andAnswer(new IAnswer<ListenableFuture<List<OFMeterConfigStatsReply>>>() {
                    @Override
                    public ListenableFuture<List<OFMeterConfigStatsReply>> answer() throws Throwable {
                        return meterStatsReply;
                    }
                });
        return meterStatsReply;
    }

    private List<OFMeterConfigStatsReply> wrapMeterStatsReply(OFMeterConfig replyEntry) {
        return ImmutableList.of(
                sw.getOFFactory().buildMeterConfigStatsReply()
                        .setEntries(ImmutableList.of(replyEntry))
                        .build());
    }
}

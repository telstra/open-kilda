/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.command.ping;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.service.ping.PingInputTranslator;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;

import net.floodlightcontroller.core.IOFSwitch;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;

public class PingRequestCommandWriteOkTest extends PingRequestCommandAbstractTest {
    private final PingService realPingService = new PingService();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        InputService inputService = createMock(InputService.class);
        inputService.addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));
        moduleContext.addService(InputService.class, inputService);

        expect(pingService.getSignature()).andDelegateTo(realPingService);
        expect(pingService.wrapData(anyObject(Ping.class), anyObject())).andDelegateTo(realPingService);

        expect(switchAlpha.getLatency()).andReturn(U64.of(1L)).anyTimes();
        expect(switchBeta.getLatency()).andReturn(U64.of(2L)).anyTimes();
        expect(switchNotCapable.getLatency()).andReturn(U64.of(3L)).anyTimes();
    }

    @Test
    public void normalWorkflow() throws Exception {
        expect(switchAlpha.write(anyObject(OFMessage.class))).andReturn(true);
        switchIntoTestMode();

        expectSuccess(makePing(switchAlpha, switchBeta));
    }

    @Test
    public void writeError() throws Exception {
        final DatapathId dpIdWriteError = DatapathId.of(0xfffe000000000005L);
        IOFSwitch switchWriteError = createMock(IOFSwitch.class);
        expect(switchService.getActiveSwitch(dpIdWriteError)).andReturn(switchWriteError).anyTimes();
        expect(switchWriteError.getId()).andReturn(dpIdWriteError).anyTimes();
        expect(switchWriteError.getOFFactory()).andReturn(new OFFactoryVer13()).anyTimes();
        expect(switchWriteError.getLatency()).andReturn(U64.of(5)).anyTimes();
        expect(switchWriteError.write(anyObject(OFMessage.class))).andReturn(false);

        switchIntoTestMode();

        Ping ping = makePing(switchWriteError, switchBeta);
        PingRequestCommand command = makeCommand(ping);
        command.call();

        verifySentErrorResponse(ping, Errors.WRITE_FAILURE);
    }

    private void switchIntoTestMode() throws Exception {
        replayAll();
        moduleContext.addConfigParam(new PathVerificationService(), "hmac256-secret", "secret");
        realPingService.init(moduleContext);
    }

    private void expectSuccess(Ping ping) throws Exception {
        PingRequestCommand command = makeCommand(ping);
        command.call();

        List<Message> replies = kafkaMessageCatcher.getValues();
        Assert.assertEquals(0, replies.size());
    }

    private PingRequestCommand makeCommand(Ping ping) {
        CommandContext context = commandContextFactory.produce();
        return new PingRequestCommand(context, ping);
    }
}

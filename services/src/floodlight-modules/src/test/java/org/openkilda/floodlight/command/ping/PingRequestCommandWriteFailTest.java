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

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;

import org.junit.Before;
import org.junit.Test;

public class PingRequestCommandWriteFailTest extends PingRequestCommandAbstractTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        replayAll();
    }

    @Test
    public void missingSourceSwitch() throws Exception {
        expectFail(makePing(switchMissing, switchBeta), Errors.SOURCE_NOT_AVAILABLE);
    }

    @Test
    public void missingDestSwitch() throws Exception {
        expectFail(makePing(switchAlpha, switchMissing), Errors.DEST_NOT_AVAILABLE);
    }

    @Test
    public void sourceSwitchIsNotCapable() throws Exception {
        expectFail(makePing(switchNotCapable, switchBeta), Errors.NOT_CAPABLE);
    }

    @Test
    public void destSwitchIsNotCapable() throws Exception {
        expectFail(makePing(switchAlpha, switchNotCapable), Errors.NOT_CAPABLE);
    }

    private void expectFail(Ping ping, Ping.Errors errorCode) throws Exception {
        final CommandContext context = commandContextFactory.produce();
        final PingRequestCommand command = new PingRequestCommand(context, ping);

        command.call();

        verifySentErrorResponse(ping, errorCode);
    }
}

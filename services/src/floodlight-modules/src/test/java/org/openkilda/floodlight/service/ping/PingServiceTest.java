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

package org.openkilda.floodlight.service.ping;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import org.easymock.EasyMockSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;

public class PingServiceTest extends EasyMockSupport {
    private PingService pingService = new PingService();
    private ISwitchManager switchManager = new SwitchManager();
    private FloodlightModuleContext moduleContext = new FloodlightModuleContext();
    private PathVerificationService pathVerificationService = new PathVerificationService();

    @Before
    public void setUp() {
        injectMocks(this);

        moduleContext.addService(IOFSwitchService.class, createMock(IOFSwitchService.class));
        moduleContext.addService(InputService.class, createMock(InputService.class));
        moduleContext.addService(ISwitchManager.class, createMock(ISwitchManager.class));
        moduleContext.addConfigParam(pathVerificationService, "hmac256-secret", "secret");
    }

    @Test
    public void wrapUnwrapCycle() throws Exception {
        DatapathId dpIdAlpha = DatapathId.of(0xfffe000000000001L);
        DatapathId dpIdBeta = DatapathId.of(0xfffe000000000002L);
        MacAddress macBeta = MacAddress.of(0xfffe000000000002L);

        moduleContext.getServiceImpl(ISwitchManager.class)
                .dpIdToMac(dpIdBeta);
        expect(switchManager.dpIdToMac(dpIdBeta)).andReturn(macBeta);

        moduleContext.getServiceImpl(InputService.class)
                .addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));

        replayAll();

        pingService.setup(moduleContext);

        Ping ping = new Ping(
                (short) 0x100,
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9));

        byte[] payload = new byte[]{0, 1, 2, 3, 4};
        Ethernet wrapped = pingService.wrapData(ping, payload);
        byte[] unwrapped = pingService.unwrapData(dpIdBeta, wrapped);

        Assert.assertNotNull(unwrapped);
        Assert.assertEquals(payload, unwrapped);
    }
}

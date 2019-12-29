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

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.model.PingWiredView;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PingServiceTest extends EasyMockSupport {
    private static final DatapathId dpIdAlpha = DatapathId.of(0x0000fffe00000001L);
    private static final DatapathId dpIdBeta = DatapathId.of(0x0000fffe00000002L);

    private PingService pingService = new PingService();
    private ISwitchManager switchManager = new SwitchManager();
    private FloodlightModuleContext moduleContext = new FloodlightModuleContext();
    private PathVerificationService pathVerificationService = new PathVerificationService();

    @Before
    public void setUp() {
        injectMocks(this);

        KildaCore kildaCore = EasyMock.createMock(KildaCore.class);
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(
                moduleContext, KildaCore.class);
        KildaCoreConfig coreConfig = provider.getConfiguration(KildaCoreConfig.class);
        expect(kildaCore.getConfig()).andStubReturn(coreConfig);
        EasyMock.replay(kildaCore);

        moduleContext.addService(KildaCore.class, kildaCore);
        moduleContext.addService(IOFSwitchService.class, createMock(IOFSwitchService.class));
        moduleContext.addService(InputService.class, createMock(InputService.class));
        moduleContext.addService(ISwitchManager.class, createMock(ISwitchManager.class));
        moduleContext.addConfigParam(pathVerificationService, "hmac256-secret", "secret");
    }

    @Test
    public void wrapUnwrapCycleZeroVlanTags() throws Exception {
        testWrapUnwrapCycle(new Ping(
                0, 0,
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9)));
    }

    @Test
    public void wrapUnwrapCycleSingleVlanTags() throws Exception {
        testWrapUnwrapCycle(new Ping(
                0x100, 0,
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9)));
    }

    @Test
    public void wrapUnwrapCycleDoubleVlanTags() throws Exception {
        testWrapUnwrapCycle(new Ping(
                0x100, 0x200,
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9)));
    }

    private void testWrapUnwrapCycle(Ping ping) throws Exception {
        moduleContext.getServiceImpl(InputService.class)
                .addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));

        replayAll();

        pingService.setup(moduleContext);

        byte[] payload = new byte[]{0x31, 0x32, 0x33, 0x34, 0x35};
        byte[] wrapped = pingService.wrapData(ping, payload).serialize();
        IPacket decoded = new Ethernet().deserialize(wrapped, 0, wrapped.length);
        Assert.assertTrue(decoded instanceof Ethernet);
        PingWiredView parsed = pingService.unwrapData(dpIdBeta, (Ethernet) decoded);

        Assert.assertNotNull(parsed);
        Assert.assertArrayEquals(payload, parsed.getPayload());

        List<Integer> expectedVlanStack = normalizeVlanStack(ping.getIngressVlanId(), ping.getIngressInnerVlanId());
        Assert.assertEquals(expectedVlanStack, parsed.getVlanStack());
    }

    private List<Integer> normalizeVlanStack(Integer... rawStack) {
        return Arrays.stream(rawStack)
                .filter(FlowEndpoint::isVlanIdSet)
                .collect(Collectors.toList());
    }
}

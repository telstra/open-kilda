/* Copyright 2017 Telstra Open Source
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

/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package org.openkilda.floodlight;

import static org.easymock.EasyMock.expect;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.packet.Ethernet;
import org.easymock.EasyMock;
import org.junit.Before;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.net.InetSocketAddress;

/**
 * This class gets a input on the application context which is used to
 * retrieve Spring beans from during tests.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class FloodlightTestCase {
    protected MockFloodlightProvider mockFloodlightProvider;
    protected MockSwitchManager mockSwitchManager;
    protected OFFeaturesReply swFeatures;
    protected OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

    public MockFloodlightProvider getMockFloodlightProvider() {
        return mockFloodlightProvider;
    }

    public MockSwitchManager getMockSwitchService() {
        return mockSwitchManager;
    }

    public void setMockFloodlightProvider(MockFloodlightProvider mockFloodlightProvider) {
        this.mockFloodlightProvider = mockFloodlightProvider;
    }

    public FloodlightContext parseAndAnnotate(OFMessage m) {
        FloodlightContext bc = new FloodlightContext();
        return parseAndAnnotate(bc, m);
    }

    public static FloodlightContext parseAndAnnotate(FloodlightContext bc, OFMessage m) {
        if (OFType.PACKET_IN.equals(m.getType())) {
            OFPacketIn pi = (OFPacketIn) m;
            Ethernet eth = new Ethernet();
            eth.deserialize(pi.getData(), 0, pi.getData().length);
            IFloodlightProviderService.bcStore.put(bc,
                    IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                    eth);
        }
        return bc;
    }

    @Before
    public void setUp() throws Exception {
        mockFloodlightProvider = new MockFloodlightProvider();
        mockSwitchManager = new MockSwitchManager();
        swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
    }

    public static OFPortDesc createOfPortDesc(IOFSwitch sw, String name, int number) {
        OFPortDesc portDesc = sw.getOFFactory().buildPortDesc()
                .setHwAddr(MacAddress.NONE)
                .setPortNo(OFPort.of(number))
                .setName(name)
                .build();
        return portDesc;
    }

    public IOFSwitch buildMockIoFSwitch(Long id, OFPortDesc portDesc, OFFactory factory,
                                        OFDescStatsReply swDesc, InetSocketAddress inetAddr) {
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(DatapathId.of(id)).anyTimes();
        expect(sw.getPort(OFPort.of(1))).andReturn(portDesc).anyTimes();
        expect(sw.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();
        expect(sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw.getSwitchDescription()).andReturn(new SwitchDescription(swDesc)).anyTimes();
        expect(sw.isActive()).andReturn(true).anyTimes();
        expect(sw.getLatency()).andReturn(U64.of(10L)).anyTimes();
        expect(sw.getInetAddress()).andReturn(inetAddr).anyTimes();
        return sw;
    }
}

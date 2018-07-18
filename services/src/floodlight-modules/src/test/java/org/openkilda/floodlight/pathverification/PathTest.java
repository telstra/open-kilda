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

package org.openkilda.floodlight.pathverification;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import org.openkilda.floodlight.FloodlightTestCase;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.apache.commons.codec.binary.Hex;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.net.InetSocketAddress;

public class PathTest extends FloodlightTestCase {
    private FloodlightContext cntx;
    private OFDescStatsReply swDescription;
    private OFFeaturesReply swFeatures;
    private IOFSwitch sw1, sw2;
    private OFPortDesc sw1Port1, sw2Port1;
    private String sw1HwAddrTarget = "11:22:33:44:55:66";
    private String sw2HwAddrTarget = "AA:BB:CC:DD:EE:FF";
    private DatapathId sw1Id = DatapathId.of(sw1HwAddrTarget);
    private DatapathId sw2Id = DatapathId.of(sw2HwAddrTarget);
    private InetSocketAddress sw1InetAddr = new InetSocketAddress("192.168.10.1", 200);
    private InetSocketAddress sw2InetAddr = new InetSocketAddress("192.168.10.101", 100);

    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
    private PathVerificationService pvs = new PathVerificationService();
    private OFPacketIn packetIn;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        cntx = new FloodlightContext();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IOFSwitchService.class, getMockSwitchService());

        swDescription = factory.buildDescStatsReply().build();
        swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
        sw1Port1 = EasyMock.createMock(OFPortDesc.class);
        sw2Port1 = EasyMock.createMock(OFPortDesc.class);
        expect(sw1Port1.getHwAddr()).andReturn(MacAddress.of(sw1HwAddrTarget)).anyTimes();
        expect(sw1Port1.getPortNo()).andReturn(OFPort.of(1));
        expect(sw2Port1.getHwAddr()).andReturn(MacAddress.of(sw2HwAddrTarget)).anyTimes();
        expect(sw2Port1.getPortNo()).andReturn(OFPort.of(1));

        sw1 = EasyMock.createMock(IOFSwitch.class);
        expect(sw1.getId()).andReturn(sw1Id).anyTimes();
        expect(sw1.getPort(OFPort.of(1))).andReturn(sw1Port1).anyTimes();
        expect(sw1.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw1.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();
        expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw1.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
        expect(sw1.isActive()).andReturn(true).anyTimes();
        expect(sw1.getLatency()).andReturn(U64.of(10L)).anyTimes();
        expect(sw1.getInetAddress()).andReturn(sw1InetAddr).anyTimes();

        sw2 = EasyMock.createMock(IOFSwitch.class);
        expect(sw2.getId()).andReturn(sw2Id).anyTimes();
        expect(sw2.getPort(OFPort.of(1))).andReturn(sw1Port1).anyTimes();
        expect(sw2.getOFFactory()).andReturn(factory).anyTimes();
        expect(sw2.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();
        expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw2.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
        expect(sw2.isActive()).andReturn(true).anyTimes();
        expect(sw2.getLatency()).andReturn(U64.of(10L)).anyTimes();
        expect(sw2.getInetAddress()).andReturn(sw2InetAddr).anyTimes();

        replay(sw1Port1);
        replay(sw2Port1);
        replay(sw1);
        replay(sw2);

        packetIn = EasyMock.createMock(OFPacketIn.class);

        pvs.initAlgorithm("secret");
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testCreate() {
        OFPacketOut packetOut = pvs.generateVerificationPacket(sw1, sw1Port1.getPortNo());

        expect(packetIn.getData()).andReturn(packetOut.getData());
        expect(packetIn.getInPort()).andReturn(sw2Port1.getPortNo());
        replay(packetIn);

        byte[] d = packetIn.getData();
        System.out.println(packetOut.toString());
        System.out.println(Hex.encodeHexString(packetOut.getData()));
        System.out.println(Hex.encodeHexString(d));

//    Path path = new Path(sw2, sw2Port1.getPortNumber(), verPacket);
//    assertTrue(path.getSource().equals(sw1Port1));
//    assertTrue(path.getDestination().equals(sw2Port1));
//    assertEquals(path.getLatency(), 100);
    }

}

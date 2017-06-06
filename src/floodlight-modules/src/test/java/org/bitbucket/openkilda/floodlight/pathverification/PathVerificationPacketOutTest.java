package org.bitbucket.openkilda.floodlight.pathverification;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;

import org.bitbucket.openkilda.floodlightcontroller.test.FloodlightTestCase;

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
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class PathVerificationPacketOutTest extends FloodlightTestCase {
    protected FloodlightContext cntx;
    protected OFFeaturesReply swFeatures;
    protected OFDescStatsReply swDescription;
    protected PathVerificationService pvs;
    protected InetSocketAddress srcIpTarget, dstIpTarget;
    protected String sw1HwAddrTarget, sw2HwAddrTarget;
    protected IOFSwitch sw1, sw2;

    private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

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
        pvs = new PathVerificationService();

        srcIpTarget = new InetSocketAddress("192.168.10.1", 200);
        dstIpTarget = new InetSocketAddress("192.168.10.101", 100);
        sw1HwAddrTarget = "11:22:33:44:55:66";
        sw2HwAddrTarget = "AA:BB:CC:DD:EE:FF";

        OFPortDesc sw1Port1 = EasyMock.createMock(OFPortDesc.class);
        expect(sw1Port1.getHwAddr()).andReturn(MacAddress.of(sw1HwAddrTarget)).anyTimes();
        OFPortDesc sw2Port1 = EasyMock.createMock(OFPortDesc.class);
        expect(sw2Port1.getHwAddr()).andReturn(MacAddress.of(sw2HwAddrTarget)).anyTimes();
        replay(sw1Port1);
        replay(sw2Port1);

        sw1 = buildMockIOFSwitch(1L, sw1Port1, factory, swDescription, srcIpTarget);
        sw2 = buildMockIOFSwitch(2L, sw2Port1, factory, swDescription, dstIpTarget);
        replay(sw1);
        replay(sw2);
    }

    @After
    public void tearDown() throws Exception {
    }

    @SuppressWarnings("static-access")
    @Test
    public void testBcastPacket() {
        // This is Broadcast so set dstIpTarget to the broadcast IP
        InetSocketAddress dstIpTarget = new InetSocketAddress(pvs.VERIFICATION_PACKET_IP_DST, 200);

        // Generate the VerificationPacket
        OFPacketOut packet = pvs.generateVerificationPacket(sw1, OFPort.of(1));
        System.out.println(Hex.encodeHexString(packet.getData()));

        // Source MAC will always be that of sw1 for both Unicast and Broadcast
        byte[] srcMac = Arrays.copyOfRange(packet.getData(), 6, 12);
        assertArrayEquals(MacAddress.of(sw1HwAddrTarget).getBytes(), srcMac);

        // Destination MAC should be that of BROADCAST for Broadcast Packet
        byte[] dstMac = Arrays.copyOfRange(packet.getData(), 0, 6);
        assertArrayEquals(MacAddress.of(pvs.VERIFICATION_BCAST_PACKET_DST).getBytes(), dstMac);

        // Source IP is actual switch1 IP
        byte[] srcIpActual = Arrays.copyOfRange(packet.getData(), 26, 30);
        assertArrayEquals(srcIpTarget.getAddress().getAddress(), srcIpActual);

        // Destination IP is that of DESTINATION BROADCAST IP
        byte[] dstIpActual = Arrays.copyOfRange(packet.getData(), 30, 34);
        assertArrayEquals(dstIpTarget.getAddress().getAddress(), dstIpActual);
    }

    @Test
    public void testUncastPacket() {
        // Generate the VerificationPacket
        OFPacketOut packet = pvs.generateVerificationPacket(sw1, OFPort.of(1), sw2);

        // Source MAC will always be that of sw1 for both Unicast and Broadcast
        byte[] srcMacActual = Arrays.copyOfRange(packet.getData(), 6, 12);
        assertArrayEquals(MacAddress.of(sw1HwAddrTarget).getBytes(), srcMacActual);

        // Destination MAC should be that of sw2 for Unicast Packet
        byte[] dstMacActual = Arrays.copyOfRange(packet.getData(), 0, 6);
        assertArrayEquals(MacAddress.of(sw2HwAddrTarget).getBytes(), dstMacActual);

        // Source and Destination IP's are the respective switch IP's
        byte[] srcIpActual = Arrays.copyOfRange(packet.getData(), 26, 30);
        assertArrayEquals(srcIpTarget.getAddress().getAddress(), srcIpActual);
        byte[] dstIpActual = Arrays.copyOfRange(packet.getData(), 30, 34);
        assertArrayEquals(dstIpTarget.getAddress().getAddress(), dstIpActual);
    }

    /**
     * Utils
     */
    public IOFSwitch buildMockIOFSwitch(Long id, OFPortDesc portDesc, OFFactory factory, OFDescStatsReply swDesc, InetSocketAddress inetAddr) {
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

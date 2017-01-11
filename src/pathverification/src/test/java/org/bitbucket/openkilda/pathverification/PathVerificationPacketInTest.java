package org.bitbucket.openkilda.pathverification;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;

import org.apache.commons.codec.binary.Hex;
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
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.test.FloodlightTestCase;

public class PathVerificationPacketInTest extends FloodlightTestCase {
  
  protected FloodlightContext cntx;
  protected OFFeaturesReply swFeatures;
  protected OFDescStatsReply swDescription;
  protected PathVerificationService pvs;
  protected String sw1HwAddrTarget = "aa:bb:cc:dd:ee:ff";
  protected IOFSwitch sw1;
  protected OFPacketIn pktIn;
  protected InetSocketAddress swIp = new InetSocketAddress("192.168.10.1", 200);

  private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
  private byte[] pkt = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF, // src mac
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66,                                           // dst mac
                        0x08, 0x00,                                                                   // ether-type
                        // IP
                        0x45, 0x00,                                                                   // ver,ihl, dscp, ecn
                        0x00, 0x30,                                                                   // total length
                        0x00, 0x00,                                                                   // tcp ident
                        0x00, 0x00,                                                                   // flags, frag offset
                        0x00,                                                                         // ttl
                        0x11,                                                                         // protocol
                        0x38, (byte) 0x6d,                                                            // header checksum
                        (byte) 0xC0, (byte) 0xA8, 0x00, 0x01,                                         // src ip
                        (byte) 0xC0, (byte) 0xA8, 0x00, (byte) 0xFF,                                  // dst ip
                        // UDP
                        (byte) 0xEF, 0x2F,                                                            // src port
                        (byte) 0xEF, 0x2F,                                                            // dst port
                        0x00, 0x1c,                                                                   // length
                        (byte) 0x8e, 0x7D,                                                            // checksum
                        // LLDP TLVs
                        0x02, 0x07,                                                                   // type, len
                        0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,                                     // chassisid
                        0x04, 0x03,                                                                   // type, len
                        0x02, 0x00, 0x01,                                                             // port id
                        0x06, 0x02,                                                                   // type, len
                        0x00, 0x78,                                                                   // ttl
                        0x00, 0x00
                        };
  
  protected IPacket getPacket() {
    UDP udp = new UDP()
        .setDestinationPort(TransportPort.of(PathVerificationService.VERIFICATION_PACKET_UDP_PORT))
        .setSourcePort(TransportPort.of(PathVerificationService.VERIFICATION_PACKET_UDP_PORT));
    
    udp.setPayload(
        new VerificationPacket()
        .setChassisId(new LLDPTLV().setType((byte) 1).setLength((short) 7).setValue(new byte[] {0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}))
        .setPortId(new LLDPTLV().setType((byte) 2).setLength((short) 3).setValue(new byte[] {0x02, 0x00, 0x01}))
        .setTtl(new LLDPTLV().setType((byte) 3).setLength((short) 2).setValue(new byte[] {0x00, 0x78})));
    
    IPv4 ip = new IPv4()
        .setSourceAddress("192.168.0.1")
        .setDestinationAddress(PathVerificationService.VERIFICATION_PACKET_IP_DST)
        .setProtocol(IpProtocol.UDP);

    Ethernet eth = new Ethernet()
        .setDestinationMACAddress("AA:BB:CC:DD:EE:FF")
        .setSourceMACAddress("11:22:33:44:55:66")
        .setEtherType(EthType.IPv4);
    
    eth.setPayload(ip);
    ip.setPayload(udp);
  
    return eth;
  }
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
    cntx = new FloodlightContext();
    mockFloodlightProvider = getMockFloodlightProvider();
    FloodlightModuleContext fmc = new FloodlightModuleContext();
    fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
    fmc.addService(IOFSwitchService.class, getMockSwitchService());
    
    OFPacketIn.Builder packetInBuilder = factory.buildPacketIn();
    packetInBuilder
      .setMatch(factory.buildMatch().setExact(MatchField.IN_PORT, OFPort.of(1)).build())
      .setData(pkt)
      .setReason(OFPacketInReason.NO_MATCH);
    pktIn = packetInBuilder.build();
    System.out.print(Hex.encodeHexString(pktIn.getData()));
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testDeserialize() {
    Ethernet ethernet = (Ethernet) new Ethernet().deserialize(pkt, 0, pkt.length);
    assertArrayEquals(pkt, ethernet.serialize());
    
    IPacket expected = getPacket();
    assertArrayEquals(expected.serialize(), ethernet.serialize());
  }
}

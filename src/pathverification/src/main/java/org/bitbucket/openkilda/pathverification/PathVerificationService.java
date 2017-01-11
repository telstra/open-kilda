package org.bitbucket.openkilda.pathverification;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.OFMessageUtils;

public class PathVerificationService
    implements IFloodlightModule, IOFMessageListener, IPathVerificationService {
  public static final String VERIFICATION_BCAST_PACKET_DST = "00:26:e1:ff:ff:ff";
  public static final int VERIFICATION_PACKET_UDP_PORT = 61231;
  public static final String VERIFICATION_PACKET_IP_DST = "192.168.0.255";

  protected IFloodlightProviderService floodlightProvider;
  protected IOFSwitchService switchService;
  protected IStaticEntryPusherService sfpService;
  protected static Logger logger;

  /**
   * IFloodlightModule Methods.
   */
  public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
    l.add(IFloodlightProviderService.class);
    l.add(IStaticEntryPusherService.class);
    l.add(IOFSwitchService.class);
    return l;
  }

  public Collection<Class<? extends IFloodlightService>> getModuleServices() {
    Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
    return l;
  }

  public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    return null;
  }

  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    sfpService = context.getServiceImpl(IStaticEntryPusherService.class);
    switchService = context.getServiceImpl(IOFSwitchService.class);
    logger = LoggerFactory.getLogger(PathVerificationService.class);
  }

  public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
    logger.info("Stating " + PathVerificationService.class.getCanonicalName());
    floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
  }

  /**
   * IOFMessageListener Methods.
   */
  public String getName() {
    return PathVerificationService.class.getSimpleName();
  }

  public boolean isCallbackOrderingPostreq(OFType arg0, String arg1) {
    return false;
  }

  public boolean isCallbackOrderingPrereq(OFType arg0, String arg1) {
    return false;
  }

  public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
      FloodlightContext context) {
    switch (msg.getType()) {
    case PACKET_IN:
     return handlePacketIn(sw, (OFPacketIn) msg, context);
    default:
      break;
    }
    return Command.CONTINUE;
  }

  /**
   * IPathVerificationService Methods.
   */
  public Match buildVerificationMatch(IOFSwitch sw, boolean isBroadcast) {
    MacAddress dstMac = MacAddress.of(VERIFICATION_BCAST_PACKET_DST);
    if (!isBroadcast) {
      dstMac = dpidToMac(sw);
    }
    Match.Builder mb = sw.getOFFactory().buildMatch();
    mb.setExact(MatchField.ETH_DST, dstMac)
        .setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IP_PROTO, IpProtocol.UDP)
        .setExact(MatchField.UDP_DST, TransportPort.of(VERIFICATION_PACKET_UDP_PORT))
        .setExact(MatchField.UDP_SRC, TransportPort.of(VERIFICATION_PACKET_UDP_PORT));
    return mb.build();
  }

  public List<OFAction> buildSendToControllerAction(IOFSwitch sw) {
    ArrayList<OFAction> actionList = new ArrayList<OFAction>();
    OFActions actions = sw.getOFFactory().actions();
    OFActionOutput output = actions.buildOutput()
        .setMaxLen(0xFFffFFff)
        .setPort(OFPort.CONTROLLER)
        .build();
    actionList.add(output);
    
    // Set Destination MAC to own DPID
    OFOxms oxms = sw.getOFFactory().oxms();
    OFActionSetField dstMac = actions.buildSetField()
        .setField(
            oxms.buildEthDst()
            .setValue(dpidToMac(sw))
            .build()
            )
        .build();
    actionList.add(dstMac);
    return actionList;
  }

  public OFFlowMod buildFlowMod(IOFSwitch sw, Match match, List<OFAction> actionList) {
    OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
    fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
    fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
    fmb.setBufferId(OFBufferId.NO_BUFFER);
    fmb.setCookie(U64.of(0));
    fmb.setPriority(FlowModUtils.PRIORITY_VERY_HIGH);
    fmb.setActions(actionList);
    fmb.setMatch(match);
    return fmb.build();
  }

  public void installVerificationRule(DatapathId switchId, boolean isBroadcast) {
    IOFSwitch sw = switchService.getSwitch(switchId);

    Match match = buildVerificationMatch(sw, isBroadcast);
    ArrayList<OFAction> actionList = (ArrayList<OFAction>) buildSendToControllerAction(sw);
    OFFlowMod flowMod = buildFlowMod(sw, match, actionList);

    logger.debug("Adding verification flow to {}.", switchId);
    String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
    flowname += "VerificationFlow";
    sfpService.addFlow(flowname, flowMod, switchId);
  }

  public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort srcPort) {
    return generateVerificationPacket(srcSw, srcPort, null);
  }
  
  protected List<OFAction> getDiscoveryActions(IOFSwitch sw, OFPort port) {
    // set actions
    List<OFAction> actions = new ArrayList<OFAction>();
    actions.add(sw.getOFFactory().actions().buildOutput().setPort(port).build());
    return actions;
  }
  
  public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort port, IOFSwitch dstSw) {
//    logger.debug("Sending LLDP packet out of swich: {}, port: {}", new Object[] {srcSw.getId().getLong(),
//        port.toString()});

    OFPortDesc ofPortDesc = srcSw.getPort(port);

    byte[] chassisId = new byte[] { 4, 0, 0, 0, 0, 0, 0 };
    byte[] portId = new byte[] { 2, 0, 0 };
    byte[] ttlValue = new byte[] { 0, 0x78 };
    byte[] dpidTLVValue = new byte[] { 0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    LLDPTLV dpidTLV = new LLDPTLV().setType((byte) 127).setLength((short) dpidTLVValue.length)
        .setValue(dpidTLVValue);

    byte[] dpidArray = new byte[8];
    ByteBuffer dpidBB = ByteBuffer.wrap(dpidArray);
    ByteBuffer portBB = ByteBuffer.wrap(portId, 1, 2);

    DatapathId dpid = srcSw.getId();
    dpidBB.putLong(dpid.getLong());
    System.arraycopy(dpidArray, 2, chassisId, 1, 6);
    // Set the optionalTLV to the full SwitchID
    System.arraycopy(dpidArray, 0, dpidTLVValue, 4, 8);

    byte[] srcMac = ofPortDesc.getHwAddr().getBytes();
    byte[] zeroMac = { 0, 0, 0, 0, 0, 0 };
    if (Arrays.equals(srcMac, zeroMac)) {
      logger.warn("Port {}/{} has zero hardware address" + "overwrite with lower 6 bytes of dpid",
          dpid.toString(), ofPortDesc.getPortNo().getPortNumber());
      System.arraycopy(dpidArray, 2, srcMac, 0, 6);
    }

    portBB.putShort(port.getShortPortNumber());

    VerificationPacket vp = new VerificationPacket();
    vp.setChassisId(
        new LLDPTLV().setType((byte) 1).setLength((short) chassisId.length).setValue(chassisId));

    vp.setPortId(new LLDPTLV().setType((byte) 2).setLength((short) portId.length).setValue(portId));

    vp.setTtl(
        new LLDPTLV().setType((byte) 3).setLength((short) ttlValue.length).setValue(ttlValue));

    vp.getOptionalTLVList().add(dpidTLV);
    // Add the controller identifier to the TLV value.
    // vp.getOptionalTLVList().add(controllerTLV);

    // Add T0 based on format from Floodlight LLDP
    long time = System.currentTimeMillis();
    long swLatency = srcSw.getLatency().getValue();

//    logger.debug("SETTING LLDP LATENCY TLV: Current Time {}; {} control plane latency {}; sum {}",
//        new Object[] { time, srcSw.getId(), swLatency, time + swLatency });

    byte[] timestampTLVValue = ByteBuffer.allocate(Long.SIZE / 8 + 4).put((byte) 0x00)
        .put((byte) 0x26).put((byte) 0xe1)
        .put((byte) 0x01) /*
                           * 0x01 is what we'll use to differentiate DPID (0x00)
                           * from time (0x01)
                           */
        .putLong(time + swLatency /* account for our switch's one-way latency */).array();

    LLDPTLV timestampTLV = new LLDPTLV().setType((byte) 127)
        .setLength((short) timestampTLVValue.length).setValue(timestampTLVValue);

    vp.getOptionalTLVList().add(timestampTLV);
    
    MacAddress dstMac = MacAddress.of(VERIFICATION_BCAST_PACKET_DST);
    if (dstSw != null) {
      OFPortDesc sw2OfPortDesc = dstSw.getPort(port);
      dstMac = sw2OfPortDesc.getHwAddr();
    }
    Ethernet l2 = new Ethernet().setSourceMACAddress(MacAddress.of(srcMac))
        .setDestinationMACAddress(dstMac).setEtherType(EthType.IPv4);

    IPv4Address dstIp = IPv4Address.of(VERIFICATION_PACKET_IP_DST);
    if (dstSw != null) {
      dstIp = IPv4Address.of(((InetSocketAddress) dstSw.getInetAddress()).getAddress().getAddress());
    }
    IPv4 l3 = new IPv4().setSourceAddress(IPv4Address.of(((InetSocketAddress) srcSw.getInetAddress()).getAddress().getAddress()))
        .setDestinationAddress(dstIp).setTtl((byte) 64)
        .setProtocol(IpProtocol.UDP);

    UDP l4 = new UDP();
    l4.setSourcePort(TransportPort.of(VERIFICATION_PACKET_UDP_PORT));
    l4.setDestinationPort(TransportPort.of(VERIFICATION_PACKET_UDP_PORT));

    l2.setPayload(l3);
    l3.setPayload(l4);
    l4.setPayload(vp);

    byte[] data = l2.serialize();
    OFPacketOut.Builder pob = srcSw.getOFFactory().buildPacketOut().setBufferId(OFBufferId.NO_BUFFER)
        .setActions(getDiscoveryActions(srcSw, port)).setData(data);
    OFMessageUtils.setInPort(pob, OFPort.CONTROLLER);

//    logger.debug("{}", pob.build());
    return pob.build();
  }
  
  public MacAddress dpidToMac(IOFSwitch sw) {
    return MacAddress.of(Arrays.copyOfRange(sw.getId().getBytes(), 2, 8));
  }
  
  public net.floodlightcontroller.core.IListener.Command handlePacketIn(IOFSwitch sw, OFPacketIn pkt, FloodlightContext context) {
    Ethernet eth = IFloodlightProviderService.bcStore.get(context,
        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
    OFPort inPort = pkt.getVersion().compareTo(OFVersion.OF_12) < 0 ? pkt.getInPort() : pkt.getMatch().get(MatchField.IN_PORT);
    if (eth.getPayload() instanceof IPv4) {
      IPv4 ip = (IPv4) eth.getPayload();
      if (ip.getPayload() instanceof UDP) {
        UDP udp = (UDP) ip.getPayload();
        if ((udp.getSourcePort().getPort() == PathVerificationService.VERIFICATION_PACKET_UDP_PORT) 
          && (udp.getDestinationPort().getPort() == PathVerificationService.VERIFICATION_PACKET_UDP_PORT)) {
          // We can finally get to the VerificationPacket payload
          VerificationPacket verPkt = (VerificationPacket) udp.getPayload();
          logger.debug("Received VerificationPacket from {}:{}", sw.getId(), inPort.getPortNumber());
          logger.info(verPkt.toString());
          }
      }
      return Command.STOP;
    }
    return Command.CONTINUE;
  }
}

package org.bitbucket.openkilda.floodlight.pathverification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.OFMessageUtils;

import org.bitbucket.openkilda.floodlight.kafka.IKafkaService;
import org.bitbucket.openkilda.floodlight.message.InfoMessage;
import org.bitbucket.openkilda.floodlight.message.Message;
import org.bitbucket.openkilda.floodlight.message.info.IslInfoData;
import org.bitbucket.openkilda.floodlight.message.info.PathNode;
import org.bitbucket.openkilda.floodlight.pathverification.type.PathType;
import org.bitbucket.openkilda.floodlight.pathverification.web.PathVerificationServiceWebRoutable;
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

public class PathVerificationService
implements IFloodlightModule, IOFMessageListener, IPathVerificationService {
  public static final String VERIFICATION_BCAST_PACKET_DST = "00:26:e1:ff:ff:ff";
  public static final int VERIFICATION_PACKET_UDP_PORT = 61231;
  public static final String VERIFICATION_PACKET_IP_DST = "192.168.0.255";

  protected IFloodlightProviderService floodlightProvider;
  protected IOFSwitchService switchService;
  protected IStaticEntryPusherService sfpService;
  protected IRestApiService restApiService;
  protected IKafkaService kafkaService;
  protected Logger logger;
  protected ObjectMapper mapper = new ObjectMapper();
  protected String topic;

  /**
   * IFloodlightModule Methods.
   */
  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
    services.add(IFloodlightProviderService.class);
    services.add(IStaticEntryPusherService.class);
    services.add(IOFSwitchService.class);
    services.add(IRestApiService.class);
    services.add(IKafkaService.class);
    return services;
  }

  @Override
  public Collection<Class<? extends IFloodlightService>> getModuleServices() {
    Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
    services.add(IPathVerificationService.class);
    return services;
  }

  @Override
  public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
    Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
    map.put(IPathVerificationService.class, this);
    return map;
  }

  @Override
  public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    sfpService = context.getServiceImpl(IStaticEntryPusherService.class);
    switchService = context.getServiceImpl(IOFSwitchService.class);
    restApiService = context.getServiceImpl(IRestApiService.class);
    kafkaService = context.getServiceImpl(IKafkaService.class);
    logger = LoggerFactory.getLogger(PathVerificationService.class);
    Map<String, String> configParameters = context.getConfigParams(this);
    topic = configParameters.get("topic");
  }

  @Override
  public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
    logger.info("Stating " + PathVerificationService.class.getCanonicalName());
    floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    restApiService.addRestletRoutable(new PathVerificationServiceWebRoutable());
  }

  /**
   * IOFMessageListener Methods.
   */
  @Override
  public String getName() {
    return PathVerificationService.class.getSimpleName();
  }

  @Override
  public boolean isCallbackOrderingPostreq(OFType arg0, String arg1) {
    return false;
  }

  @Override
  public boolean isCallbackOrderingPrereq(OFType arg0, String arg1) {
    return false;
  }

  @Override
  public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext context) {
    logger.info("received a " + msg.getType());
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

  protected Match buildVerificationMatch(IOFSwitch sw, boolean isBroadcast) {
    MacAddress dstMac = MacAddress.of(VERIFICATION_BCAST_PACKET_DST);
    if (!isBroadcast) {
      dstMac = dpidToMac(sw);
    }
    Match.Builder mb = sw.getOFFactory().buildMatch();
    mb.setExact(MatchField.ETH_DST, dstMac).setExact(MatchField.ETH_TYPE, EthType.IPv4)
    .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
    .setExact(MatchField.UDP_DST, TransportPort.of(VERIFICATION_PACKET_UDP_PORT))
    .setExact(MatchField.UDP_SRC, TransportPort.of(VERIFICATION_PACKET_UDP_PORT));
    return mb.build();
  }

  protected List<OFAction> buildSendToControllerAction(IOFSwitch sw) {
    ArrayList<OFAction> actionList = new ArrayList<>();
    OFActions actions = sw.getOFFactory().actions();
    OFActionOutput output = actions.buildOutput().setMaxLen(0xFFffFFff).setPort(OFPort.CONTROLLER)
        .build();
    actionList.add(output);

    // Set Destination MAC to own DPID
    OFOxms oxms = sw.getOFFactory().oxms();
    OFActionSetField dstMac = actions.buildSetField()
        .setField(oxms.buildEthDst().setValue(dpidToMac(sw)).build()).build();
    actionList.add(dstMac);
    return actionList;
  }

  protected OFFlowMod buildFlowMod(IOFSwitch sw, Match match, List<OFAction> actionList) {
    OFFlowMod.Builder fmb = sw.getOFFactory().buildFlowAdd();
    fmb.setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT);
    fmb.setHardTimeout(FlowModUtils.INFINITE_TIMEOUT);
    fmb.setBufferId(OFBufferId.NO_BUFFER);
    fmb.setCookie(U64.of(123L));
    fmb.setPriority(FlowModUtils.PRIORITY_VERY_HIGH);
    fmb.setActions(actionList);
    fmb.setMatch(match);
    return fmb.build();
  }

  @Override
  public void installVerificationRule(DatapathId switchId, boolean isBroadcast) {
    IOFSwitch sw = switchService.getSwitch(switchId);

    Match match = buildVerificationMatch(sw, isBroadcast);
    ArrayList<OFAction> actionList = (ArrayList<OFAction>) buildSendToControllerAction(sw);
    OFFlowMod flowMod = buildFlowMod(sw, match, actionList);

    logger.debug("Adding verification flow to {}.", switchId);
    String flowname = (isBroadcast) ? "Broadcast" : "Unicast";
    flowname += "--VerificationFlow--" + switchId.toString();
    logger.debug("adding: " + flowname + " " + flowMod.toString() + "--" + switchId.toString());
    sfpService.addFlow(flowname, flowMod, switchId);
  }

  protected List<OFAction> getDiscoveryActions(IOFSwitch sw, OFPort port) {
    // set actions
    List<OFAction> actions = new ArrayList<OFAction>();
    actions.add(sw.getOFFactory().actions().buildOutput().setPort(port).build());
    return actions;
  }

  @Override
  public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port) {
    IOFSwitch srcSwitch = switchService.getSwitch(srcSwId);
    return srcSwitch.write(generateVerificationPacket(srcSwitch, port));
  }

  @Override
  public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port, DatapathId dstSwId) {
    IOFSwitch srcSwitch = switchService.getSwitch(srcSwId);

    if (srcSwitch == null) { // fix dereference violations in case race conditions
      return false;
    }

    if (dstSwId == null) {
      return srcSwitch.write(generateVerificationPacket(srcSwitch, port));
    }
    IOFSwitch dstSwitch = switchService.getSwitch(dstSwId);
    return srcSwitch.write(generateVerificationPacket(srcSwitch, port, dstSwitch));
  }

  public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort srcPort) {
    return generateVerificationPacket(srcSw, srcPort, null);
  }

  public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort port, IOFSwitch dstSw) {
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
    //    vp.getOptionalTLVList().add(controllerTLV);

    // Add T0 based on format from Floodlight LLDP
    long time = System.currentTimeMillis();
    long swLatency = srcSw.getLatency().getValue();
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

    // Type
    byte[] typeTLVValue = ByteBuffer.allocate(Integer.SIZE / 8 + 4).put((byte) 0x00)
        .put((byte) 0x26).put((byte) 0xe1)
        .put((byte) 0x02)
        .putInt(PathType.ISL.ordinal()).array();
    LLDPTLV typeTLV = new LLDPTLV().setType((byte) 127)
        .setLength((short) typeTLVValue.length).setValue(typeTLVValue);
    vp.getOptionalTLVList().add(typeTLV);

    MacAddress dstMac = MacAddress.of(VERIFICATION_BCAST_PACKET_DST);
    if (dstSw != null) {
      OFPortDesc sw2OfPortDesc = dstSw.getPort(port);
      dstMac = sw2OfPortDesc.getHwAddr();
    }
    Ethernet l2 = new Ethernet().setSourceMACAddress(MacAddress.of(srcMac))
        .setDestinationMACAddress(dstMac).setEtherType(EthType.IPv4);

    IPv4Address dstIp = IPv4Address.of(VERIFICATION_PACKET_IP_DST);
    if (dstSw != null) {
      dstIp = IPv4Address
          .of(((InetSocketAddress) dstSw.getInetAddress()).getAddress().getAddress());
    }
    IPv4 l3 = new IPv4()
        .setSourceAddress(
            IPv4Address.of(((InetSocketAddress) srcSw.getInetAddress()).getAddress().getAddress()))
        .setDestinationAddress(dstIp).setTtl((byte) 64).setProtocol(IpProtocol.UDP);

    UDP l4 = new UDP();
    l4.setSourcePort(TransportPort.of(VERIFICATION_PACKET_UDP_PORT));
    l4.setDestinationPort(TransportPort.of(VERIFICATION_PACKET_UDP_PORT));

    l2.setPayload(l3);
    l3.setPayload(l4);
    l4.setPayload(vp);

    byte[] data = l2.serialize();
    OFPacketOut.Builder pob = srcSw.getOFFactory().buildPacketOut()
        .setBufferId(OFBufferId.NO_BUFFER).setActions(getDiscoveryActions(srcSw, port))
        .setData(data);
    OFMessageUtils.setInPort(pob, OFPort.CONTROLLER);

    return pob.build();
  }

  public MacAddress dpidToMac(IOFSwitch sw) {
    return MacAddress.of(Arrays.copyOfRange(sw.getId().getBytes(), 2, 8));
  }

  public VerificationPacket deserialize(Ethernet eth) throws Exception {
    if (eth.getPayload() instanceof IPv4) {
      IPv4 ip = (IPv4) eth.getPayload();

      if (ip.getPayload() instanceof UDP) {
        UDP udp = (UDP) ip.getPayload();

        if ((udp.getSourcePort().getPort() == PathVerificationService.VERIFICATION_PACKET_UDP_PORT)
            && (udp.getDestinationPort()
                .getPort() == PathVerificationService.VERIFICATION_PACKET_UDP_PORT)) {

          return new VerificationPacket((Data) udp.getPayload());
        }
      }
    }
    throw new Exception("Ethernet packet was not a verificaiton packet");
  }

  public net.floodlightcontroller.core.IListener.Command handlePacketIn(IOFSwitch sw,
      OFPacketIn pkt, FloodlightContext context) {
    long time = System.currentTimeMillis();
    VerificationPacket verificationPacket = null;
    Command command = Command.CONTINUE;

    Ethernet eth = IFloodlightProviderService.bcStore.get(context,
        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
    try {
      verificationPacket = deserialize(eth);
      command = Command.STOP;
    } catch (Exception exception) {
      logger.debug("was not a verificaiton packet");
    }

    OFPort inPort = pkt.getVersion().compareTo(OFVersion.OF_12) < 0 ? pkt.getInPort()
        : pkt.getMatch().get(MatchField.IN_PORT);
    ByteBuffer portBB = ByteBuffer.wrap(verificationPacket.getPortId().getValue());
    portBB.position(1);
    OFPort remotePort = OFPort.of(portBB.getShort());

    long timestamp = 0;
    int pathOrdinal = 10;
    IOFSwitch remoteSwitch = null;
    for (LLDPTLV lldptlv : verificationPacket.getOptionalTLVList()) {
      if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
          && lldptlv.getValue()[0] == 0x0 
          && lldptlv.getValue()[1] == 0x26
          && lldptlv.getValue()[2] == (byte) 0xe1 
          && lldptlv.getValue()[3] == 0x0) {
        ByteBuffer dpidBB = ByteBuffer.wrap(lldptlv.getValue());
        remoteSwitch = switchService.getSwitch(DatapathId.of(dpidBB.getLong(4)));
      } else if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
        && lldptlv.getValue()[0] == 0x0
        && lldptlv.getValue()[1] == 0x26
        && lldptlv.getValue()[2] == (byte) 0xe1
        && lldptlv.getValue()[3] == 0x01) {
          ByteBuffer tsBB = ByteBuffer.wrap(lldptlv.getValue()); /* skip OpenFlow OUI (4 bytes above) */
          long swLatency = sw.getLatency().getValue();
          timestamp = tsBB.getLong(4); /* include the RX switch latency to "subtract" it */
          timestamp = timestamp + swLatency;
      } else if (lldptlv.getType() == 127 && lldptlv.getLength() == 8
        && lldptlv.getValue()[0] == 0x0 
        && lldptlv.getValue()[1] == 0x26
        && lldptlv.getValue()[2] == (byte) 0xe1
        && lldptlv.getValue()[3] == 0x02) {
          ByteBuffer typeBB = ByteBuffer.wrap(lldptlv.getValue());
          pathOrdinal = typeBB.getInt(4);
      }
    }
    U64 latency = (timestamp != 0 && (time - timestamp) > 0) ? U64.of(time - timestamp) : U64.ZERO;

    List<PathNode> nodes = Arrays.asList(
        new PathNode()
          .withSwitchId(sw.getId().toString())
          .withPortNo(inPort.getPortNumber())
          .withSegLatency(latency.getValue())
          .withSeqId(0),
        new PathNode()
          .withSwitchId(remoteSwitch.getId().toString())
          .withPortNo(remotePort.getPortNumber())
          .withSeqId(1)
        );

    IslInfoData path = new IslInfoData()
        .withLatency(latency.getValue())
        .withPath(nodes);
    
    Message message = new InfoMessage()
        .withData(path)
        .withTimestamp(System.currentTimeMillis());
    
    try {
      logger.debug(message.toJson());
      kafkaService.postMessage(topic, message);
    } catch (JsonProcessingException exception) {
      logger.error("could not create json for path");
    }
  return command;
  }
}

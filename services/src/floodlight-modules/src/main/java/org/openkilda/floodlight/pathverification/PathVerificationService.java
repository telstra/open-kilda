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

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.pathverification.type.PathType;
import org.openkilda.floodlight.pathverification.web.PathVerificationServiceWebRoutable;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.of.IInputTranslator;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import net.floodlightcontroller.util.OFMessageUtils;
import org.apache.commons.codec.binary.Hex;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescProp;
import org.projectfloodlight.openflow.protocol.OFPortDescPropEthernet;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
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

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PathVerificationService implements IFloodlightModule, IPathVerificationService, IInputTranslator {
    private static final Logger logger = LoggerFactory.getLogger(PathVerificationService.class);
    private static final Logger logIsl = LoggerFactory.getLogger(
            String.format("%s.ISL", PathVerificationService.class.getName()));

    public static final U64 OF_CATCH_RULE_COOKIE = U64.of(Cookie.VERIFICATION_BROADCAST_RULE_COOKIE);

    public static final String VERIFICATION_BCAST_PACKET_DST = "08:ED:02:E3:FF:FF";
    public static final int VERIFICATION_PACKET_UDP_PORT = 61231;
    public static final String VERIFICATION_PACKET_IP_DST = "192.168.0.255";

    private IKafkaProducerService producerService;
    private IOFSwitchService switchService;

    private String topoDiscoTopic;
    private double islBandwidthQuotient = 1.0;
    private Algorithm algorithm;
    private JWTVerifier verifier;

    /**
     * IFloodlightModule Methods.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(
                CommandProcessorService.class,
                InputService.class,
                IOFSwitchService.class,
                IRestApiService.class,
                KafkaUtilityService.class,
                IKafkaProducerService.class);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(
                IPathVerificationService.class,
                PingService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(
                IPathVerificationService.class, this,
                PingService.class, new PingService());
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.debug("main pathverification service: " + this);

        initConfiguration(context);

        switchService = context.getServiceImpl(IOFSwitchService.class);
        producerService = context.getServiceImpl(IKafkaProducerService.class);
    }

    @VisibleForTesting
    void initConfiguration(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(moduleContext, this);
        PathVerificationServiceConfig config = provider.getConfiguration(PathVerificationServiceConfig.class);

        islBandwidthQuotient = config.getIslBandwidthQuotient();

        initAlgorithm(config.getHmac256Secret());
    }

    @VisibleForTesting
    void initAlgorithm(String secret) throws FloodlightModuleException {
        try {
            algorithm = Algorithm.HMAC256(secret);
            verifier = JWT.require(algorithm).build();
        } catch (UnsupportedEncodingException e) {
            logger.error("Ivalid secret", e);
            throw new FloodlightModuleException("Invalid secret for HMAC256");
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Stating {}", PathVerificationService.class.getCanonicalName());

        topoDiscoTopic = context.getServiceImpl(KafkaUtilityService.class).getTopics().getTopoDiscoTopic();

        InputService inputService = context.getServiceImpl(InputService.class);
        inputService.addTranslator(OFType.PACKET_IN, this);

        context.getServiceImpl(PingService.class).setup(context);
        context.getServiceImpl(IRestApiService.class)
                .addRestletRoutable(new PathVerificationServiceWebRoutable());
    }

    @Override
    public Command makeCommand(CommandContext context, OfInput input) {
        return new Command(context) {
            @Override
            public Command call() {
                handlePacketIn(input);
                return null;
            }
        };
    }

    /**
     * IPathVerificationService Methods.
     */

    protected List<OFAction> getDiscoveryActions(IOFSwitch sw, OFPort port) {
        // set actions
        List<OFAction> actions = new ArrayList<>();
        actions.add(sw.getOFFactory().actions().buildOutput().setPort(port).build());
        return actions;
    }

    @Override
    public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port) {
        return sendDiscoveryMessage(srcSwId, port, null);
    }

    @Override
    public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port, DatapathId dstSwId) {
        boolean result = false;

        try {
            IOFSwitch srcSwitch = switchService.getSwitch(srcSwId);
            if (srcSwitch != null && srcSwitch.getPort(port) != null) {
                IOFSwitch dstSwitch = (dstSwId == null) ? null : switchService.getSwitch(dstSwId);
                OFPacketOut ofPacketOut = generateVerificationPacket(srcSwitch, port, dstSwitch, true);

                if (ofPacketOut != null) {
                    logger.debug("==> Sending verification packet out {}/{}: {}", srcSwitch.getId().toString(),
                            port.getPortNumber(),
                            Hex.encodeHexString(ofPacketOut.getData()));
                    result = srcSwitch.write(ofPacketOut);
                } else {
                    logger.error("<== Received null from generateVerificationPacket, inputs where: "
                            + "srcSwitch: {}, port: {}, dstSwitch: {}", srcSwitch, port, dstSwitch);
                }

                if (result) {
                    logIsl.info("push discovery package via: {}-{}", srcSwitch.getId(), port.getPortNumber());
                } else {
                    logger.error(
                            "Failed to send PACKET_OUT(ISL discovery packet) via {}-{}",
                            srcSwitch.getId(), port.getPortNumber());
                }
            }
        } catch (Exception exception) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), exception);
        }

        return result;
    }

    public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort port) {
        return generateVerificationPacket(srcSw, port, null, true);
    }

    /**
     * Return verification packet.
     *
     * @param srcSw source switch.
     * @param port port.
     * @param dstSw destination switch
     * @param sign sign.
     * @return verification packet.
     */
    public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort port, IOFSwitch dstSw,
                                                  boolean sign) {
        try {
            byte[] dpidArray = new byte[8];
            ByteBuffer dpidBb = ByteBuffer.wrap(dpidArray);

            DatapathId dpid = srcSw.getId();
            dpidBb.putLong(dpid.getLong());
            byte[] chassisId = new byte[]{4, 0, 0, 0, 0, 0, 0};
            System.arraycopy(dpidArray, 2, chassisId, 1, 6);
            // Set the optionalTLV to the full SwitchID
            byte[] dpidTlvValue = new byte[]{0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            System.arraycopy(dpidArray, 0, dpidTlvValue, 4, 8);

            // Set src mac to be able to detect the origin of the packet.
            // NB: previously we set port's address instead of switch (some switches declare unique address per port)
            byte[] srcMac = new byte[6];
            System.arraycopy(dpidArray, 2, srcMac, 0, 6);

            byte[] portId = new byte[]{2, 0, 0};
            ByteBuffer portBb = ByteBuffer.wrap(portId, 1, 2);
            portBb.putShort(port.getShortPortNumber());

            VerificationPacket vp = new VerificationPacket();
            vp.setChassisId(
                    new LLDPTLV().setType((byte) 1).setLength((short) chassisId.length).setValue(chassisId));

            vp.setPortId(new LLDPTLV().setType((byte) 2).setLength((short) portId.length).setValue(portId));

            byte[] ttlValue = new byte[]{0, 0x78};
            vp.setTtl(
                    new LLDPTLV().setType((byte) 3).setLength((short) ttlValue.length).setValue(ttlValue));

            LLDPTLV dpidTlv = new LLDPTLV().setType((byte) 127).setLength((short) dpidTlvValue.length)
                    .setValue(dpidTlvValue);
            vp.getOptionalTlvList().add(dpidTlv);
            // Add the controller identifier to the TLV value.
            //    vp.getOptionalTlvList().add(controllerTLV);

            // Add T0 based on format from Floodlight LLDP
            long time = System.currentTimeMillis();
            long swLatency = srcSw.getLatency().getValue();
            byte[] timestampTlvValue = ByteBuffer.allocate(Long.SIZE / 8 + 4).put((byte) 0x00)
                    .put((byte) 0x26).put((byte) 0xe1)
                    .put((byte) 0x01) // 0x01 is what we'll use to differentiate DPID (0x00) from time (0x01)
                    .putLong(time + swLatency /* account for our switch's one-way latency */)
                    .array();

            LLDPTLV timestampTlv = new LLDPTLV().setType((byte) 127)
                    .setLength((short) timestampTlvValue.length).setValue(timestampTlvValue);

            vp.getOptionalTlvList().add(timestampTlv);

            // Type
            byte[] typeTlvValue = ByteBuffer.allocate(Integer.SIZE / 8 + 4).put((byte) 0x00)
                    .put((byte) 0x26).put((byte) 0xe1)
                    .put((byte) 0x02)
                    .putInt(PathType.ISL.ordinal()).array();
            LLDPTLV typeTlv = new LLDPTLV().setType((byte) 127)
                    .setLength((short) typeTlvValue.length).setValue(typeTlvValue);
            vp.getOptionalTlvList().add(typeTlv);

            if (sign) {
                String token = JWT.create()
                        .withClaim("dpid", dpid.getLong())
                        .withClaim("ts", time + swLatency)
                        .sign(algorithm);

                byte[] tokenBytes = token.getBytes(Charset.forName("UTF-8"));

                byte[] tokenTlvValue = ByteBuffer.allocate(4 + tokenBytes.length).put((byte) 0x00)
                        .put((byte) 0x26).put((byte) 0xe1)
                        .put((byte) 0x03)
                        .put(tokenBytes).array();
                LLDPTLV tokenTlv = new LLDPTLV().setType((byte) 127)
                        .setLength((short) tokenTlvValue.length).setValue(tokenTlvValue);

                vp.getOptionalTlvList().add(tokenTlv);
            }

            MacAddress dstMac = MacAddress.of(VERIFICATION_BCAST_PACKET_DST);
            if (dstSw != null) {
                OFPortDesc sw2OfPortDesc = dstSw.getPort(port);
                dstMac = sw2OfPortDesc.getHwAddr();
            }

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


            Ethernet l2 = new Ethernet().setSourceMACAddress(MacAddress.of(srcMac))
                    .setDestinationMACAddress(dstMac).setEtherType(EthType.IPv4);
            l2.setPayload(l3);
            l3.setPayload(l4);
            l4.setPayload(vp);

            byte[] data = l2.serialize();
            OFPacketOut.Builder pob = srcSw.getOFFactory().buildPacketOut()
                    .setBufferId(OFBufferId.NO_BUFFER).setActions(getDiscoveryActions(srcSw, port))
                    .setData(data);
            OFMessageUtils.setInPort(pob, OFPort.CONTROLLER);

            return pob.build();
        } catch (Exception exception) {
            logger.error("error generating verification packet: {}", exception);
        }
        return null;
    }

    private VerificationPacket deserialize(Ethernet eth) throws Exception {
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
        throw new Exception("Ethernet packet was not a verification packet: " + eth);
    }

    void handlePacketIn(OfInput input) {
        logger.debug("{} - {}", getClass().getCanonicalName(), input);

        if (input.packetInCookieMismatch(OF_CATCH_RULE_COOKIE, logger)) {
            return;
        }

        VerificationPacket verificationPacket = null;
        try {
            verificationPacket = deserialize(input.getPacketInPayload());
        } catch (Exception exception) {
            logger.trace("Deserialization failure: {}, exception: {}", exception.getMessage(), exception);
            return;
        }

        try {
            ByteBuffer portBb = ByteBuffer.wrap(verificationPacket.getPortId().getValue());
            portBb.position(1);

            long timestamp = 0;
            int pathOrdinal = 10;
            IOFSwitch remoteSwitch = null;
            DatapathId remoteSwitchId = null;
            boolean signed = false;
            for (LLDPTLV lldptlv : verificationPacket.getOptionalTlvList()) {
                if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
                        && lldptlv.getValue()[0] == 0x0
                        && lldptlv.getValue()[1] == 0x26
                        && lldptlv.getValue()[2] == (byte) 0xe1
                        && lldptlv.getValue()[3] == 0x0) {
                    ByteBuffer dpidBb = ByteBuffer.wrap(lldptlv.getValue());
                    remoteSwitchId = DatapathId.of(dpidBb.getLong(4));
                    remoteSwitch = switchService.getSwitch(remoteSwitchId);
                } else if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
                        && lldptlv.getValue()[0] == 0x0
                        && lldptlv.getValue()[1] == 0x26
                        && lldptlv.getValue()[2] == (byte) 0xe1
                        && lldptlv.getValue()[3] == 0x01) {
                    ByteBuffer tsBb = ByteBuffer.wrap(lldptlv.getValue()); /* skip OpenFlow OUI (4 bytes above) */
                    timestamp = tsBb.getLong(4); /* include the RX switch latency to "subtract" it */
                    timestamp = timestamp + input.getLatency();
                } else if (lldptlv.getType() == 127 && lldptlv.getLength() == 8
                        && lldptlv.getValue()[0] == 0x0
                        && lldptlv.getValue()[1] == 0x26
                        && lldptlv.getValue()[2] == (byte) 0xe1
                        && lldptlv.getValue()[3] == 0x02) {
                    ByteBuffer typeBb = ByteBuffer.wrap(lldptlv.getValue());
                    pathOrdinal = typeBb.getInt(4);
                } else if (lldptlv.getType() == 127
                        && lldptlv.getValue()[0] == 0x0
                        && lldptlv.getValue()[1] == 0x26
                        && lldptlv.getValue()[2] == (byte) 0xe1
                        && lldptlv.getValue()[3] == 0x03) {
                    ByteBuffer bb = ByteBuffer.wrap(lldptlv.getValue());
                    bb.position(4);
                    byte[] tokenArray = new byte[lldptlv.getLength() - 4];
                    bb.get(tokenArray, 0, tokenArray.length);
                    String token = new String(tokenArray);

                    try {
                        DecodedJWT jwt = verifier.verify(token);
                        signed = true;
                    } catch (JWTVerificationException e) {
                        logger.error("Packet verification failed", e);
                        return;
                    }
                }
            }

            // Corner case where we receive a valid VerificationPacket but the remote switch is not known.  This is
            // going to be a bigger issue when we have multiple speakers with different switches on them.  For now
            // if we don't know the switch, then return.
            //
            // TODO:  fix the above

            if (remoteSwitch == null) {
                logger.warn("detected unknown remote switch {}", remoteSwitchId);
                return;
            }

            if (!signed) {
                logger.warn("verification packet without sign");
                return;
            }

            OFPort inPort = OFMessageUtils.getInPort((OFPacketIn) input.getMessage());
            OFPort remotePort = OFPort.of(portBb.getShort());
            long latency = measureLatency(input, timestamp);
            logIsl.info("link discovered: {}-{} ===( {} ms )===> {}-{}",
                    remoteSwitch.getId(), remotePort, latency, input.getDpId(), inPort);

            // this verification packet was sent from remote switch/port to received switch/port
            // so the link direction is from remote switch/port to received switch/port
            PathNode source = new PathNode(new SwitchId(remoteSwitch.getId().getLong()), remotePort.getPortNumber(), 0,
                            latency);
            PathNode destination = new PathNode(new SwitchId(input.getDpId().getLong()), inPort.getPortNumber(), 1);
            long speed = getSwitchPortSpeed(input.getDpId(), inPort);
            IslInfoData path = new IslInfoData(latency, source, destination, speed, IslChangeType.DISCOVERED,
                    getAvailableBandwidth(speed));

            Message message = new InfoMessage(path, System.currentTimeMillis(), CorrelationContext.getId(), null);

            producerService.sendMessageAndTrack(topoDiscoTopic, message);
            logger.debug("packet_in processed for {}-{}", input.getDpId(), inPort);

        } catch (UnsupportedOperationException exception) {
            logger.error("could not parse packet_in message: {}", exception.getMessage(),
                    exception);
        } catch (Exception exception) {
            logger.error(String.format("Unhandled exception %s", input), exception);
            throw exception;
        }
    }

    private long measureLatency(OfInput input, long sendTime) {
        long latency = -1;
        if (sendTime != 0) {
            latency = input.getReceiveTime() - sendTime;
            if (latency < 0L) {
                latency = -1;
            }
        }
        return latency;
    }

    private long getSwitchPortSpeed(DatapathId dpId, OFPort inPort) {
        IOFSwitch sw = switchService.getSwitch(dpId);
        OFPortDesc port = sw.getPort(inPort);
        long speed = -1;

        if (port.getVersion().compareTo(OFVersion.OF_13) > 0) {
            for (OFPortDescProp prop : port.getProperties()) {
                if (prop.getType() == 0) {
                    speed = ((OFPortDescPropEthernet) prop).getCurrSpeed();
                    break;
                }
            }
        }

        if (speed < 0) {
            speed = port.getCurrSpeed();
        }

        return speed;
    }

    private long getAvailableBandwidth(long speed) {
        return (long) (speed * islBandwidthQuotient);
    }
}

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

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.pathverification.type.PathType;
import org.openkilda.floodlight.pathverification.web.PathVerificationServiceWebRoutable;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IListener;
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
import net.floodlightcontroller.util.OFMessageUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescProp;
import org.projectfloodlight.openflow.protocol.OFPortDescPropEthernet;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathVerificationService implements IFloodlightModule, IOFMessageListener, IPathVerificationService {
    private static final Logger logger = LoggerFactory.getLogger(PathVerificationService.class);
    private static final Logger logIsl = LoggerFactory.getLogger(
            String.format("%s.ISL", PathVerificationService.class.getName()));

    public static final String VERIFICATION_BCAST_PACKET_DST = "08:ED:02:E3:FF:FF";
    public static final int VERIFICATION_PACKET_UDP_PORT = 61231;
    public static final String VERIFICATION_PACKET_IP_DST = "192.168.0.255";

    private String topoDiscoTopic;
    private IFloodlightProviderService floodlightProvider;
    private IOFSwitchService switchService;
    private IRestApiService restApiService;
    private boolean isAlive = false;
    private KafkaProducer<String, String> producer;
    private double islBandwidthQuotient = 1.0;
    private Algorithm algorithm;
    private JWTVerifier verifier;

    /**
     * IFloodlightModule Methods.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IFloodlightProviderService.class);
        services.add(IOFSwitchService.class);
        services.add(IRestApiService.class);
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
        logger.debug("main pathverification service: " + this);
        ConfigurationProvider provider = new ConfigurationProvider(context, this);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);
        PathVerificationServiceConfig serviceConfig = provider.getConfiguration(PathVerificationServiceConfig.class);

        initConfiguration(topicsConfig, serviceConfig);

        initServices(context);

        producer = new KafkaProducer<>(serviceConfig.createKafkaProducerProperties());
    }

    @VisibleForTesting
    void initConfiguration(KafkaTopicsConfig topicsConfig, PathVerificationServiceConfig serviceConfig)
            throws FloodlightModuleException {
        topoDiscoTopic = topicsConfig.getTopoDiscoTopic();
        islBandwidthQuotient = serviceConfig.getIslBandwidthQuotient();

        initAlgorithm(serviceConfig.getHmac256Secret());
    }

    @VisibleForTesting
    void initServices(FloodlightModuleContext context) {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);
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

    @VisibleForTesting
    void setKafkaProducer(KafkaProducer<String, String> mockProducer) {
        producer = mockProducer;
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Stating " + PathVerificationService.class.getCanonicalName());
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApiService.addRestletRoutable(new PathVerificationServiceWebRoutable());
        isAlive = true;
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
    @NewCorrelationContextRequired
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext context) {
        logger.debug("PathVerificationService received new message of type {}: {}", msg.getType(), msg.toString());
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

    public boolean isAlive() {
        return isAlive;
    }

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
                    logger.debug("==> Sending verification packet out {}/{}: {}", srcSwitch.getId().toString(), port.getPortNumber(),
                            Hex.encodeHexString(ofPacketOut.getData()));
                    result = srcSwitch.write(ofPacketOut);
                } else {
                    logger.error("<== Received null from generateVerificationPacket, inputs where: " +
                            "srcSwitch: {}, port: {}, dstSwitch: {}", srcSwitch, port, dstSwitch);
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
            logger.error("Error trying to sendDiscoveryMessage: {}", exception);
        }

        return result;
    }

    public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort port) {
        return generateVerificationPacket(srcSw,port,null,true);
    }

    public OFPacketOut generateVerificationPacket(IOFSwitch srcSw, OFPort port, IOFSwitch dstSw,
            boolean sign) {
        try {
            OFPortDesc ofPortDesc = srcSw.getPort(port);

            byte[] chassisId = new byte[]{4, 0, 0, 0, 0, 0, 0};
            byte[] portId = new byte[]{2, 0, 0};
            byte[] ttlValue = new byte[]{0, 0x78};
            byte[] dpidTLVValue = new byte[]{0x0, 0x26, (byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0};

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


            byte[] zeroMac = {0, 0, 0, 0, 0, 0};
            byte[] srcMac = ofPortDesc.getHwAddr().getBytes();
            if (Arrays.equals(srcMac, zeroMac)) {
                int portVal = ofPortDesc.getPortNo().getPortNumber();
                // this is a common scenario
                logger.debug("Port {}/{} has zero hardware address: overwrite with lower 6 bytes of dpid",
                dpid.toString(), portVal);
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
                    .put((byte) 0x01) // 0x01 is what we'll use to differentiate DPID (0x00) from time (0x01)
                    .putLong(time + swLatency /* account for our switch's one-way latency */)
                    .array();

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

            if (sign) {
                String token = JWT.create()
                        .withClaim("dpid", dpid.getLong())
                        .withClaim("ts", time + swLatency)
                        .sign(algorithm);

                byte[] tokenBytes = token.getBytes(Charset.forName("UTF-8"));

                byte[] tokenTLVValue = ByteBuffer.allocate(4 + tokenBytes.length).put((byte) 0x00)
                        .put((byte) 0x26).put((byte) 0xe1)
                        .put((byte) 0x03)
                        .put(tokenBytes).array();
                LLDPTLV tokenTLV = new LLDPTLV().setType((byte) 127)
                        .setLength((short) tokenTLVValue.length).setValue(tokenTLVValue);

                vp.getOptionalTLVList().add(tokenTLV);
            }

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

    private IListener.Command handlePacketIn(IOFSwitch sw, OFPacketIn pkt, FloodlightContext context) {
        long time = System.currentTimeMillis();
        final String packetIdentity = String.format("(dpId: %s, xId: %s, version: %s, type: %s)",
                sw.getId(), pkt.getXid(), pkt.getVersion(), pkt.getType());
        logger.debug("packet_in {}", packetIdentity);

        VerificationPacket verificationPacket = null;

        Ethernet eth = IFloodlightProviderService.bcStore.get(context, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        try {
            verificationPacket = deserialize(eth);
        } catch (Exception exception) {
            logger.trace("Deserialization failure: {}, exception: {}", exception.getMessage(), exception);
            return Command.CONTINUE;
        }

        try {
            OFPort inPort = pkt.getVersion().compareTo(OFVersion.OF_12) < 0 ? pkt.getInPort()
                    : pkt.getMatch().get(MatchField.IN_PORT);
            ByteBuffer portBB = ByteBuffer.wrap(verificationPacket.getPortId().getValue());
            portBB.position(1);
            OFPort remotePort = OFPort.of(portBB.getShort());

            long timestamp = 0;
            int pathOrdinal = 10;
            IOFSwitch remoteSwitch = null;
            boolean signed = false;
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
                    }
                    catch (JWTVerificationException e)
                    {
                        logger.error("Packet verification failed", e);
                        return Command.STOP;
                    }
                }
            }

            // Corner case where we receive a valid VerificationPacket but the remote switch is not known.  This is
            // going to be a bigger issue when we have multiple speakers with different switches on them.  For now
            // if we don't know the switch, then return.
            //
            // TODO:  fix the above

            if (remoteSwitch == null) {
                return Command.STOP;
            }

            if (!signed)
            {
                logger.warn("verification packet without sign");
                return Command.STOP;
            }

            U64 latency = (timestamp != 0 && (time - timestamp) > 0) ? U64.of(time - timestamp) : U64.ZERO;

            logIsl.info("link discovered: {}-{} ===( {} ms )===> {}-{}",
                    remoteSwitch.getId(), remotePort, latency.getValue(), sw.getId(), inPort);

            // this verification packet was sent from remote switch/port to received switch/port
            // so the link direction is from remote switch/port to received switch/port
            List<PathNode> nodes = Arrays.asList(
                    new PathNode(remoteSwitch.getId().toString(), remotePort.getPortNumber(), 0, latency.getValue()),
                    new PathNode(sw.getId().toString(), inPort.getPortNumber(), 1));

            OFPortDesc port = sw.getPort(inPort);
            long speed = Integer.MAX_VALUE;

            if (port.getVersion().compareTo(OFVersion.OF_13) > 0) {
                for (OFPortDescProp prop : port.getProperties()) {
                    if (prop.getType() == 0x0) {
                        speed = ((OFPortDescPropEthernet) prop).getCurrSpeed();
                    }
                }
            } else {
                speed = port.getCurrSpeed();
            }

            IslInfoData path = new IslInfoData(latency.getValue(), nodes, speed, IslChangeType.DISCOVERED,
                    getAvailableBandwidth(speed));

            Message message = new InfoMessage(path, System.currentTimeMillis(), CorrelationContext.getId(), null);

            final String json = MAPPER.writeValueAsString(message);
            logger.debug("about to send {}", json);
            producer.send(new ProducerRecord<>(topoDiscoTopic, json));
            logger.debug("packet_in processed for {}-{}", sw.getId(), inPort);

        } catch (JsonProcessingException exception) {
            logger.error("could not create json for path packet_in: {}", exception.getMessage(), exception);
        } catch (UnsupportedOperationException exception) {
            logger.error("could not parse packet_in message: {}", exception.getMessage(),
                    exception);
        } catch (Exception exception) {
            logger.error(String.format("Unhandled exception %s", packetIdentity), exception);
            throw exception;
        }

        return Command.STOP;
    }

    private long getAvailableBandwidth(long speed) {
        return (long) (speed * islBandwidthQuotient);
    }
}

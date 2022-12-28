/* Copyright 2022 Telstra Open Source
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

package org.openkilda.floodlight.service.lacp;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.of.IInputTranslator;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.shared.packet.Lacp;
import org.openkilda.floodlight.shared.packet.Lacp.ActorPartnerInfo;
import org.openkilda.floodlight.shared.packet.SlowProtocols;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.LacpInfoData;
import org.openkilda.messaging.info.event.LacpPartner;
import org.openkilda.messaging.info.event.LacpState;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LacpService implements IService, IInputTranslator {
    private static final Logger logger = LoggerFactory.getLogger(LacpService.class);

    static {
        try {
            logger.info("Force loading of {}", Class.forName(SlowProtocols.class.getName()));
        } catch (ClassNotFoundException e) {
            logger.error(String.format("Couldn't load class SlowProtocols %s", e.getMessage()), e);
        }
    }

    private IOFSwitchService switchService;
    private MacAddress systemId;
    private int systemPriority;
    private int portPriority;
    private IKafkaProducerService producerService;
    private String topic;
    private String region;

    private static OFPort getInPort(OFPacketIn packetIn) {
        if (packetIn.getVersion().compareTo(OFVersion.OF_12) < 0) {
            return packetIn.getInPort();
        }

        if (packetIn.getMatch().supports(MatchField.IN_PHY_PORT)) {
            OFPort inPort = packetIn.getMatch().get(MatchField.IN_PHY_PORT);
            if (inPort != null) {
                return inPort;
            }
        }
        return packetIn.getMatch().get(MatchField.IN_PORT);
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

    @VisibleForTesting
    Lacp deserializeLacp(Ethernet eth, SwitchId switchId, long cookie) {
        try {
            IPacket payload = eth.getPayload();

            if (payload instanceof SlowProtocols) {
                SlowProtocols slowProtocol = ((SlowProtocols) payload);
                if (slowProtocol.getPayload() instanceof Lacp) {
                    return (Lacp) slowProtocol.getPayload();
                } else {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Got unknown slow protocol packet {} on switch {}", slowProtocol.getSubtype(),
                                switchId);
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            logger.info(String.format("Could not deserialize lacp packet %s on switch %s. Deserialization failure: %s",
                    eth, switchId, e.getMessage()), e);
            return null;
        }
        logger.info("Got invalid lacp packet: {} on switch {}. Cookie {}", eth, switchId, cookie);
        return null;
    }

    private void handlePacketIn(OfInput input) {
        U64 rawCookie = input.packetInCookie();

        if (rawCookie == null) {
            return;
        }

        Cookie cookie = new Cookie(rawCookie.getValue());
        SwitchId switchId = new SwitchId(input.getDpId().getLong());

        if (cookie.getType() == CookieType.LACP_REPLY_INPUT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Receive LACP packet from {} OF-xid:{}, cookie: {}", input.getDpId(),
                        input.getMessage().getXid(), cookie);
            }
            handleLacp(input, switchId, cookie.getValue(), getInPort((OFPacketIn) input.getMessage()));
        }
    }

    private void handleLacp(OfInput input, SwitchId switchId, long cookie, OFPort inPort) {
        Ethernet ethernet = input.getPacketInPayload();
        Lacp lacpRequest = deserializeLacp(ethernet, switchId, cookie);
        if (lacpRequest == null) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Switch {} received LACP request from port {}. Request: {}", switchId, inPort, lacpRequest);
        }
        Lacp lacpReply = modifyLacpRequest(lacpRequest);
        sendLacpReply(DatapathId.of(switchId.getId()), inPort, lacpReply);

        LacpInfoData lacpInfoData = getLacpInfoData(lacpRequest, switchId, inPort.getPortNumber());

        InfoMessage message = new InfoMessage(lacpInfoData, System.currentTimeMillis(), CorrelationContext.getId(),
                region);

        producerService.sendMessageAndTrackWithZk(topic, switchId.toString(), message);
    }

    private LacpInfoData getLacpInfoData(final Lacp lacpRequest, final SwitchId switchId, final int logicalPortNumber) {

        ActorPartnerInfo actor = lacpRequest.getActor();
        Lacp.State state = actor.getState();

        LacpState actorState = LacpState.builder()
                .active(state.isActive()).shortTimeout(state.isShortTimeout()).aggregatable(state.isAggregatable())
                .synchronised(state.isSynchronised()).collecting(state.isCollecting())
                .distributing(state.isDistributing()).defaulted(state.isDefaulted()).expired(state.isExpired()).build();

        org.openkilda.model.MacAddress macAddressActor =
                new org.openkilda.model.MacAddress(actor.getSystemId().toString());

        LacpPartner lacpActor = LacpPartner.builder().systemPriority(actor.getSystemPriority())
                .systemId(macAddressActor).key(actor.getKey()).portPriority(actor.getPortPriority())
                .portNumber(actor.getPortNumber()).state(actorState).build();

        return LacpInfoData.builder().switchId(switchId).logicalPortNumber(logicalPortNumber).actor(lacpActor).build();
    }

    @VisibleForTesting
    Lacp modifyLacpRequest(Lacp lacpRequest) {
        lacpRequest.setPartner(new ActorPartnerInfo(lacpRequest.getActor()));

        lacpRequest.getActor().getState().setActive(false);
        lacpRequest.getActor().getState().setSynchronised(true);
        lacpRequest.getActor().getState().setAggregatable(true);
        lacpRequest.getActor().getState().setExpired(false);
        lacpRequest.getActor().getState().setDefaulted(false);
        lacpRequest.getActor().setPortPriority(portPriority);
        lacpRequest.getActor().setSystemPriority(systemPriority);
        lacpRequest.getActor().setSystemId(systemId);
        return lacpRequest;
    }

    OFPacketOut generateLacpPacket(IOFSwitch sw, Lacp lacp, OFPort outPort) {
        try {
            SlowProtocols slowProtocols = new SlowProtocols();
            slowProtocols.setSubtype(SlowProtocols.LACP_SUBTYPE);
            slowProtocols.setPayload(lacp);

            Ethernet l2 = new Ethernet().setSourceMACAddress(
                    new SwitchId(sw.getId().getLong()).toMacAddress()).setDestinationMACAddress(
                    org.openkilda.model.MacAddress.SLOW_PROTOCOLS.getAddress()).setEtherType(EthType.SLOW_PROTOCOLS);
            l2.setPayload(slowProtocols);

            byte[] data = l2.serialize();
            OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut().setBufferId(OFBufferId.NO_BUFFER).setActions(
                    Lists.newArrayList(sw.getOFFactory().actions().buildOutput().setPort(outPort).build())).setData(
                    data);
            OFMessageUtils.setInPort(pob, OFPort.CONTROLLER);

            return pob.build();
        } catch (Exception e) {
            logger.error(String.format("Error during generation of LACP packet: %s", e.getMessage()), e);
        }
        return null;
    }

    private void sendLacpReply(DatapathId dpId, OFPort port, Lacp lacpReply) {
        try {
            IOFSwitch sw = switchService.getSwitch(dpId);
            SwitchId switchId = new SwitchId(dpId.getLong());
            if (sw != null && sw.getPort(port) != null) {
                OFPacketOut ofPacketOut = generateLacpPacket(sw, lacpReply, port);
                boolean result = false;
                if (ofPacketOut != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Sending LACP reply out {}/{}: {}", switchId, port.getPortNumber(), lacpReply);
                    }
                    result = sw.write(ofPacketOut);
                } else {
                    logger.warn("Received null from generateLacpPacket. switch: {}, port: {}", switchId, port);
                }

                if (!result) {
                    logger.error("Failed to send PACKET_OUT(LACP reply packet) via {}-{}. Packet {}", sw.getId(),
                            port.getPortNumber(), lacpReply);
                }
            } else {
                logger.error("Couldn't find switch {} to send LACP reply", switchId);
            }
        } catch (Exception exception) {
            logger.error(String.format("Unhandled exception in %s", getClass().getName()), exception);
        }
    }

    @Override
    public void setup(FloodlightModuleContext context) {
        logger.info("Stating {}", LacpService.class.getCanonicalName());
        KafkaChannel kafkaChannel = context.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        logger.info("region: {}", kafkaChannel.getRegion());

        switchService = context.getServiceImpl(IOFSwitchService.class);

        KildaCoreConfig coreConfig = context.getServiceImpl(KildaCore.class).getConfig();
        systemId = MacAddress.of(coreConfig.getLacpSystemId());
        systemPriority = coreConfig.getLacpSystemPriority();
        portPriority = coreConfig.getLacpPortPriority();

        InputService inputService = context.getServiceImpl(InputService.class);
        inputService.addTranslator(OFType.PACKET_IN, this);


        region = kafkaChannel.getRegion();
        producerService = context.getServiceImpl(IKafkaProducerService.class);
        topic = kafkaChannel.getLacpTopic();
    }
}

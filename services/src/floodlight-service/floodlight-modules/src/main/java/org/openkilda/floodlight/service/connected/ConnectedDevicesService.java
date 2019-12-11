/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.service.connected;

import static org.openkilda.model.Cookie.LLDP_INPUT_PRE_DROP_COOKIE;
import static org.openkilda.model.Cookie.LLDP_TRANSIT_COOKIE;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.of.IInputTranslator;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.LldpInfoData;
import org.openkilda.messaging.info.event.SwitchLldpInfoData;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.LLDP;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ConnectedDevicesService implements IService, IInputTranslator {
    private static final Logger logger = LoggerFactory.getLogger(ConnectedDevicesService.class);

    private IKafkaProducerService producerService;
    private String topic;
    private String region;

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

    private LldpPacket deserializeLldp(Ethernet eth, SwitchId switchId, long cookie) {
        try {
            if (eth.getPayload() instanceof LLDP) {
                return new LldpPacket((LLDP) eth.getPayload());
            }
        }  catch (Exception exception) {
            logger.info("Could not deserialize LLDP packet {} on switch {}. Cookie {}. Deserialization failure: {}, "
                    + "exception: {}", eth, switchId, cookie, exception.getMessage(), exception);
            return null;
        }
        logger.info("Got invalid lldp packet: {} on switch {}. Cookie {}", eth, switchId, cookie);
        return null;
    }

    private boolean isLldpRelated(long value) {
        return value == LLDP_INPUT_PRE_DROP_COOKIE
                || value == LLDP_TRANSIT_COOKIE
                || Cookie.isFlowLldp(value);
    }

    private void handlePacketIn(OfInput input) {
        U64 rawCookie = input.packetInCookie();

        if (rawCookie == null || !isLldpRelated(rawCookie.getValue())) {
            return;
        }

        long cookie = rawCookie.getValue();
        SwitchId switchId = new SwitchId(input.getDpId().getLong());

        if (Cookie.isFlowLldp(cookie)) {
            handleFlowLldp(input, switchId, cookie);
        } else if (cookie == LLDP_TRANSIT_COOKIE || cookie == LLDP_INPUT_PRE_DROP_COOKIE) {
            handleSwitchLldpNonIngress(input, switchId, cookie);
        }
    }

    private void handleFlowLldp(OfInput input, SwitchId switchId, long cookie) {
        LldpPacket lldpPacket = deserializeLldp(input.getPacketInPayload(), switchId, cookie);
        if (lldpPacket == null) {
            return;
        }

        InfoMessage message = createFlowLldpMessage(input.getPacketInPayload().getSourceMACAddress().toString(),
                cookie, lldpPacket);
        producerService.sendMessageAndTrack(topic, switchId.toString(), message);
    }

    private void handleSwitchLldpNonIngress(OfInput input, SwitchId switchId, long cookie) {
        Ethernet ethernet = input.getPacketInPayload();
        LldpPacket lldpPacket = deserializeLldp(ethernet, switchId, cookie);
        if (lldpPacket == null) {
            return;
        }

        InfoMessage message = createSwitchLldpMessage(switchId, cookie, input, lldpPacket);
        producerService.sendMessageAndTrack(topic, switchId.toString(), message);
    }

    private InfoMessage createFlowLldpMessage(String macAddress, long cookie, LldpPacket lldpPacket) {
        LldpInfoData lldpInfoData = new LldpInfoData(
                cookie,
                macAddress,
                lldpPacket.getParsedChassisId(),
                lldpPacket.getParsedPortId(),
                lldpPacket.getParsedTtl(),
                lldpPacket.getParsedPortDescription(),
                lldpPacket.getParsedSystemName(),
                lldpPacket.getParsedSystemDescription(),
                lldpPacket.getParsedSystemCapabilities(),
                lldpPacket.getParsedManagementAddress());

        return new InfoMessage(lldpInfoData, System.currentTimeMillis(), CorrelationContext.getId(), region);
    }

    private InfoMessage createSwitchLldpMessage(SwitchId switchId, long cookie, OfInput input, LldpPacket lldpPacket) {
        SwitchLldpInfoData lldpInfoData = new SwitchLldpInfoData(
                switchId,
                input.getPort().getPortNumber(),
                input.getPacketInPayload().getVlanID(),
                cookie,
                input.getPacketInPayload().getSourceMACAddress().toString(),
                lldpPacket.getParsedChassisId(),
                lldpPacket.getParsedPortId(),
                lldpPacket.getParsedTtl(),
                lldpPacket.getParsedPortDescription(),
                lldpPacket.getParsedSystemName(),
                lldpPacket.getParsedSystemDescription(),
                lldpPacket.getParsedSystemCapabilities(),
                lldpPacket.getParsedManagementAddress());

        return new InfoMessage(lldpInfoData, System.currentTimeMillis(), CorrelationContext.getId(), region);
    }

    @Override
    public void setup(FloodlightModuleContext context) {
        logger.info("Stating {}", ConnectedDevicesService.class.getCanonicalName());
        KafkaChannel kafkaChannel = context.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        logger.info("region: {}", kafkaChannel.getRegion());

        region = kafkaChannel.getRegion();
        producerService = context.getServiceImpl(IKafkaProducerService.class);
        topic = kafkaChannel.getConnectedDevicesTopic();

        InputService inputService = context.getServiceImpl(InputService.class);
        inputService.addTranslator(OFType.PACKET_IN, this);
    }
}

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

    private LldpPacket deserialize(Ethernet eth) {
        if (eth.getPayload() instanceof LLDP) {
            return new LldpPacket(eth.getPayload().serialize());
        }
        return null;
    }

    private void handlePacketIn(OfInput input) {
        U64 rawCookie = input.packetInCookie();

        if (rawCookie == null || !Cookie.isMaskedAsLldp(rawCookie.getValue())) {
            return;
        }

        long cookie = rawCookie.getValue();
        SwitchId switchId = new SwitchId(input.getDpId().getLong());

        LldpPacket lldpPacket;
        try {
            lldpPacket = deserialize(input.getPacketInPayload());
            if (lldpPacket == null) {
                logger.info("Got invalid lldp packet: {} on switch {}. Cookie {}",
                        input.getPacketInPayload(), switchId, cookie);
                return;
            }
        } catch (Exception exception) {
            logger.info("Could not deserialize LLDP packet {} on switch {}. Cookie {}. Deserialization failure: {}, "
                    + "exception: {}", input.getPacketInPayload(), switchId, cookie, exception.getMessage(), exception);
            return;
        }

        InfoMessage message = createLldpMessage(input.getPacketInPayload().getSourceMACAddress().toString(),
                cookie, lldpPacket);
        producerService.sendMessageAndTrack(topic, switchId.toString(), message);
    }

    private InfoMessage createLldpMessage(String macAddress, long cookie, LldpPacket lldpPacket) {
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

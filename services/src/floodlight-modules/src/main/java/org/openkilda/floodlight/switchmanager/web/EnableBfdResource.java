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

package org.openkilda.floodlight.switchmanager.web;

import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.model.NoviBfdSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowBfdStart;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

// FIXME(tdurakov): this REST-endpoint is temporal solution and should be deleted once BFD will be introduced as a part
//  of a discovery process. This implementation copies part of BFD Command methods to make things simple
//  and not bound to Kafka.
public class EnableBfdResource extends ServerResource {
    private static final Logger logger = LoggerFactory.getLogger(EnableBfdResource.class);

    private static final int CONSTRAINT_INTERVAL_MIN = 1;

    /**
     * Setting up BFD session.
     *
     * @param json the json from request.
     * @return json response.
     * @throws JsonProcessingException if response can't be wrote to string.
     */
    @Post("json")
    @Put("json")
    public String enableBfd(String json) {
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());

        NoviBfdSession request;

        try {
            request = MAPPER.readValue(json, NoviBfdSession.class);
            if (request.getIntervalMs() < CONSTRAINT_INTERVAL_MIN) {
                throw new IllegalArgumentException(String.format(
                        "Invalid bfd session interval value: %d < %d",
                        request.getIntervalMs(), CONSTRAINT_INTERVAL_MIN));
            }
            DatapathId datapathIdtarget = DatapathId.of(request.getTarget().getDatapath().toLong());

            IOFSwitch iofSwitch = switchManager.lookupSwitch(datapathIdtarget);
            OFPacketOut outPacket = makeSessionConfigMessage(request, iofSwitch, switchManager);
            if (!iofSwitch.write(outPacket)) {
                throw new IllegalStateException("Failed to set up BFD session");
            }
        } catch (IOException e) {
            logger.error("Message received is not valid BFD Request: {}", json);
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, now(),
                    ErrorType.DATA_INVALID.toString(), "Message received is not valid BFD Request",
                    e.getMessage());
            return generateJson(responseMessage);
        } catch (SwitchNotFoundException e) {
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, now(),
                    ErrorType.DATA_INVALID.toString(), "Switch not found",
                    e.getMessage());
            return generateJson(responseMessage);
        }
        return generateJson("ok");
    }

    private OFPacketOut makeSessionConfigMessage(NoviBfdSession bfdSession, IOFSwitch sw,
                                                   ISwitchManager switchManager) {
        OFFactory ofFactory = sw.getOFFactory();

        OFActionNoviflowBfdStart bfdStartAction = ofFactory.actions().buildNoviflowBfdStart()
                .setPortNo(bfdSession.getLogicalPortNumber())
                .setMyDisc(bfdSession.getDiscriminator())
                .setInterval(bfdSession.getIntervalMs() * 1000)
                .setMultiplier(bfdSession.getMultiplier())
                .setKeepAliveTimeout(((short) (bfdSession.isKeepOverDisconnect() ? 1 : 0)))
                .build();

        return ofFactory.buildPacketOut()
                .setInPort(OFPort.CONTROLLER)
                .setData(makeSessionConfigPayload(sw, switchManager, bfdSession).serialize())
                .setActions(ImmutableList.of(bfdStartAction))
                .build();
    }

    private IPacket makeSessionConfigPayload(IOFSwitch sw, ISwitchManager switchManager, NoviBfdSession bfdSession) {
        final TransportPort udpPort = TransportPort.of(bfdSession.getUdpPortNumber());
        UDP l4 = new UDP()
                .setSourcePort(udpPort)
                .setDestinationPort(udpPort);

        InetAddress sourceIpAddress = switchManager.getSwitchIpAddress(sw);
        InetAddress destIpAddress = bfdSession.getRemote().getIpAddress();
        IPacket l3 = new IPv4()
                .setSourceAddress(IPv4Address.of(sourceIpAddress.getAddress()))
                .setDestinationAddress(IPv4Address.of(destIpAddress.getAddress()))
                .setProtocol(IpProtocol.UDP)
                .setPayload(l4);

        DatapathId remoteDatapath = DatapathId.of(bfdSession.getRemote().getDatapath().toLong());
        return new Ethernet()
                .setEtherType(EthType.IPv4)
                .setSourceMACAddress(switchManager.dpIdToMac(sw.getId()))
                .setDestinationMACAddress(switchManager.dpIdToMac(remoteDatapath))
                .setPayload(l3);
    }

    private String generateJson(Object input) {
        try {
            return MAPPER.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            logger.error("Error processing into JSON", e);
            return "Error occurred";
        }
    }

    private long now() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
}

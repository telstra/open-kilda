/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.command.bfd;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.messaging.model.NoviBfdSession.Errors;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowBfdStart;
import org.projectfloodlight.openflow.protocol.errormsg.OFNoviflowBaseError;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.net.InetAddress;

abstract class BfdSessionCommand extends BfdCommand {
    private final NoviBfdSession bfdSession;
    private final BfdSessionResponse.BfdSessionResponseBuilder responseBuilder;

    public BfdSessionCommand(CommandContext context, NoviBfdSession bfdSession) {
        super(context, DatapathId.of(bfdSession.getTarget().getDatapath().toLong()));
        this.bfdSession = bfdSession;

        this.responseBuilder = BfdSessionResponse.builder()
                .bfdSession(bfdSession);
    }

    @Override
    protected InfoData assembleResponse() {
        return responseBuilder.build();
    }

    @Override
    protected void errorDispatcher(Throwable error) throws Throwable {
        try {
            super.errorDispatcher(error);
        } catch (SessionErrorResponseException e) {
            responseBuilder.errorCode(decodeErrorResponse(e.getErrorResponse()));
            throw e;
        } catch (Throwable e) {
            responseBuilder.errorCode(Errors.SWITCH_RESPONSE_ERROR);
            throw e;
        }
    }

    protected OFPacketOut makeSessionConfigMessage(IOFSwitch sw) {
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
                .setData(makeSessionConfigPayload(sw).serialize())
                .setActions(ImmutableList.of(bfdStartAction))
                .build();
    }

    private Errors decodeErrorResponse(OFErrorMsg errorResponse) {
        if (!(errorResponse instanceof OFNoviflowBaseError)) {
            return Errors.SWITCH_RESPONSE_ERROR;
        }

        return decodeErrorResponse((OFNoviflowBaseError) errorResponse);
    }

    private Errors decodeErrorResponse(OFNoviflowBaseError errorResponse) {
        Errors errorCode;
        switch (errorResponse.getSubtype()) {
            case 0x300:
                errorCode = Errors.NOVI_BFD_BAD_PORT_ERROR;
                break;
            case 0x301:
                errorCode = Errors.NOVI_BFD_BAD_DISCRIMINATOR_ERROR;
                break;
            case 0x302:
                errorCode = Errors.NOVI_BFD_BAD_INTERVAL_ERROR;
                break;
            case 0x303:
                errorCode = Errors.NOVI_BFD_BAD_MULTIPLIER_ERROR;
                break;
            case 0x304:
                errorCode = Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR;
                break;
            case 0x305:
                errorCode = Errors.NOVI_BFD_INCOMPATIBLE_PKT_ERROR;
                break;
            case 0x306:
                errorCode = Errors.NOVI_BFD_TOO_MANY_ERROR;
                break;
            case 0x307:
                errorCode = Errors.NOVI_BFD_UNKNOWN_ERROR;
                break;
            default:
                errorCode = Errors.SWITCH_RESPONSE_ERROR;
        }

        return errorCode;
    }

    private IPacket makeSessionConfigPayload(IOFSwitch sw) {
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

    // getter & setters
    protected NoviBfdSession getBfdSession() {
        return bfdSession;
    }
}

/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.SwitchUtils;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.flow.VerificationData;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.FlowVerificationService;
import org.openkilda.floodlight.service.batch.OFBatchService;
import org.openkilda.floodlight.service.batch.OFPendingMessage;
import org.openkilda.floodlight.switchmanager.OFInstallException;
import org.openkilda.floodlight.utils.DataSignature;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;

import com.auth0.jwt.JWT;
import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.util.ArrayList;
import java.util.List;

public class VerificationSendCommand extends AbstractVerificationCommand {
    private static final String NET_L3_ADDRESS = "127.0.0.2";

    private final OFBatchService ioService;
    private final SwitchUtils switchUtils;
    private final DataSignature signature;

    public VerificationSendCommand(CommandContext context, UniFlowVerificationRequest verificationRequest) {
        super(context, verificationRequest);

        FloodlightModuleContext moduleContext = getContext().getModuleContext();
        this.ioService = moduleContext.getServiceImpl(OFBatchService.class);
        this.switchUtils = new SwitchUtils(moduleContext.getServiceImpl(IOFSwitchService.class));
        this.signature = moduleContext.getServiceImpl(FlowVerificationService.class).getSignature();
    }

    @Override
    public void run() {
        UniFlowVerificationRequest verificationRequest = getVerificationRequest();
        DatapathId sourceDpId = DatapathId.of(verificationRequest.getSourceSwitchId());
        IOFSwitch sw = switchUtils.lookupSwitch(sourceDpId);

        VerificationData data = VerificationData.of(verificationRequest);
        data.setSenderLatency(sw.getLatency().getValue());

        Ethernet netPacket = wrapData(data);
        OFMessage message = makePacketOut(sw, netPacket.serialize());

        try {
            ioService.push(this, ImmutableList.of(new OFPendingMessage(sourceDpId, message)));
        } catch (OFInstallException e) {
            sendErrorResponse(FlowVerificationErrorCode.WRITE_FAILURE);
        }
    }

    @Override
    public void ioComplete(List<OFPendingMessage> payload, boolean isError) {
        if (!isError) {
            return;
        }

        sendErrorResponse(FlowVerificationErrorCode.WRITE_FAILURE);
    }

    private Ethernet wrapData(VerificationData data) {
        Data l7 = new Data(signature.sign(data.toJWT(JWT.create())));

        UDP l4 = new UDP();
        l4.setPayload(l7);
        l4.setSourcePort(TransportPort.of(PathVerificationService.VERIFICATION_PACKET_UDP_PORT));
        l4.setDestinationPort(TransportPort.of(PathVerificationService.VERIFICATION_PACKET_UDP_PORT));

        IPv4 l3 = new IPv4();
        l3.setPayload(l4);
        l3.setSourceAddress(NET_L3_ADDRESS);
        l3.setDestinationAddress(NET_L3_ADDRESS);

        Ethernet l2 = new Ethernet();
        l2.setPayload(l3);
        l2.setEtherType(EthType.IPv4);

        UniFlowVerificationRequest verificationRequest = getVerificationRequest();
        l2.setSourceMACAddress(switchUtils.dpIdToMac(DatapathId.of(verificationRequest.getSourceSwitchId())));
        l2.setDestinationMACAddress(switchUtils.dpIdToMac(DatapathId.of(verificationRequest.getDestSwitchId())));
        if (0 != verificationRequest.getVlanId()) {
            l2.setVlanID((short) verificationRequest.getVlanId());
        }

        return l2;
    }

    public static byte[] unwrapData(MacAddress targetL2Address, Ethernet packet) {
        if (!packet.getDestinationMACAddress().equals(targetL2Address)) {
            return null;
        }

        if (!(packet.getPayload() instanceof IPv4)) {
            return null;
        }
        IPv4 ip = (IPv4) packet.getPayload();

        if (!NET_L3_ADDRESS.equals(ip.getSourceAddress().toString())) {
            return null;
        }
        if (!NET_L3_ADDRESS.equals(ip.getDestinationAddress().toString())) {
            return null;
        }

        if (!(ip.getPayload() instanceof UDP)) {
            return null;
        }
        UDP udp = (UDP) ip.getPayload();

        if (udp.getSourcePort().getPort() != PathVerificationService.VERIFICATION_PACKET_UDP_PORT) {
            return null;
        }
        if (udp.getDestinationPort().getPort() != PathVerificationService.VERIFICATION_PACKET_UDP_PORT) {
            return null;
        }

        return ((Data) udp.getPayload()).getData();
    }

    private OFMessage makePacketOut(IOFSwitch sw, byte[] data) {
        OFFactory ofFactory = sw.getOFFactory();
        OFPacketOut.Builder pktOut = ofFactory.buildPacketOut();

        pktOut.setData(data);

        List<OFAction> actions = new ArrayList<>(2);
        actions.add(ofFactory.actions().buildOutput().setPort(OFPort.TABLE).build());
        pktOut.setActions(actions);

        OFMessageUtils.setInPort(pktOut, OFPort.of(getVerificationRequest().getSourcePort()));

        return pktOut.build();
    }
}

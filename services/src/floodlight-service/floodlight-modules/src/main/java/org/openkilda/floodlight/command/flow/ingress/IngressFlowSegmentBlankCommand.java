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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;

import lombok.Getter;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
abstract class IngressFlowSegmentBlankCommand extends IngressFlowSegmentCommand {
    // payload
    protected final Integer islPort;
    protected final FlowTransitEncapsulation encapsulation;

    IngressFlowSegmentBlankCommand(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, @NonNull Integer islPort,
            @NonNull FlowTransitEncapsulation encapsulation) {
        super(messageContext, endpoint.getDatapath(), commandId, metadata, endpoint, meterConfig);
        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    @Override
    protected List<OFAction> makeTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                actions.addAll(makeVlanEncapsulationTransformActions(of));
                break;
            case VXLAN:
                actions.addAll(makeVxLanEncapsulationTransformActions(of));
                break;
            default:
                throw new NotImplementedEncapsulationException(
                        getClass(), encapsulation.getType(), switchId, metadata.getFlowId());
        }
        return actions;
    }

    private List<OFAction> makeVlanEncapsulationTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        if (! FlowEndpoint.isVlanIdSet(endpoint.getVlanId())) {
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
        }
        actions.add(OfAdapter.INSTANCE.setVlanIdAction(of, encapsulation.getId()));
        return actions;
    }

    private List<OFAction> makeVxLanEncapsulationTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();

        MacAddress l2src = MacAddress.of(getSw().getId());
        actions.add(of.actions().buildNoviflowPushVxlanTunnel()
                .setVni(encapsulation.getId())
                .setEthSrc(l2src)
                // must never appear on network, because replaced with original l2 dest address by next command
                .setEthDst(MacAddress.of(0xFFFFFFEDCBA2L))
                // nobody can explain why we use this constant
                .setUdpSrc(4500)
                .setIpv4Src(IPv4Address.of("127.0.0.1"))
                .setIpv4Dst(IPv4Address.of("127.0.0.2"))
                .setFlags((short) 0x01)  // TODO(surabujin): ??? discovery meaning
                .build());

        // copy original L2 destination address
        actions.add(of.actions().buildNoviflowCopyField()
                .setNBits(l2src.getLength() * 8)
                // 18 ethernet header
                // 20 IPV4 header
                //  8 UDP header
                //  8 VXLAN header
                //  0 offset into original ethernet header
                // 18 + 20 + 8 + 8 + 0 => 54 bytes total offset
                .setSrcOffset(54 * 8)  // TODO(surabujin) check existing code use offset 400 bits or 50 bytes ???
                .setDstOffset(0)
                .setOxmSrcHeader(of.oxms().buildNoviflowPacketOffset().getTypeLen())
                .setOxmDstHeader(of.oxms().buildNoviflowPacketOffset().getTypeLen())
                .build());

        return actions;
    }

    @Override
    protected OFAction makeOutputAction(OFFactory of) {
        return super.makeOutputAction(of,  OFPort.of(islPort));
    }
}

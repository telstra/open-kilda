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

package org.openkilda.floodlight.command.flow;

import static org.openkilda.floodlight.switchmanager.SwitchManager.VXLAN_UDP_DST;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;

import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U32;

import java.util.UUID;

@Getter
public abstract class NotIngressFlowSegmentCommand extends FlowSegmentCommand {
    protected final int ingressIslPort;
    protected final FlowTransitEncapsulation encapsulation;

    protected final OfFlowModBuilderFactory flowModBuilderFactory;

    public NotIngressFlowSegmentCommand(
            MessageContext messageContext, SwitchId switchId, UUID commandId, FlowSegmentMetadata metadata,
            int ingressIslPort, FlowTransitEncapsulation encapsulation, OfFlowModBuilderFactory flowModBuilderFactory,
            MirrorConfig mirrorConfig) {
        super(messageContext, switchId, commandId, metadata, mirrorConfig);
        this.ingressIslPort = ingressIslPort;
        this.encapsulation = encapsulation;

        this.flowModBuilderFactory = flowModBuilderFactory;
    }

    protected Match makeTransitMatch(OFFactory of) {
        Match.Builder match = of.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(ingressIslPort));
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                makeTransitVlanMatch(of, match);
                break;
            case VXLAN:
                makeTransitVxLanMatch(of, match);
                break;
            default:
                throw new NotImplementedEncapsulationException(
                        getClass(), encapsulation.getType(), switchId, metadata.getFlowId());
        }
        return match.build();
    }

    protected void makeTransitVlanMatch(OFFactory of, Match.Builder match) {
        OfAdapter.INSTANCE.matchVlanId(of, match, encapsulation.getId());
    }

    protected void makeTransitVxLanMatch(OFFactory of, Match.Builder match) {
        match.setExact(MatchField.ETH_TYPE, EthType.IPv4);
        match.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
        match.setExact(MatchField.UDP_DST, TransportPort.of(VXLAN_UDP_DST));
        if (getSwitchFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            OfAdapter.INSTANCE.matchVxLanVni(of, match, encapsulation.getId());
        } else if (getSwitchFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            match.setExact(MatchField.KILDA_VXLAN_VNI, U32.of(encapsulation.getId()));
        } else {
            throw new UnsupportedOperationException(String.format("Switch %s must support one of following features: "
                    + "[%s, %s] to match VXLAN packets", switchId,
                    NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
    }
}

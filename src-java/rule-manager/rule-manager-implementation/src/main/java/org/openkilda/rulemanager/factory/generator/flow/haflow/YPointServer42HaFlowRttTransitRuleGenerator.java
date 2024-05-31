/* Copyright 2023 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import static java.lang.String.format;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_FLOW_RTT_INPUT_TRANSIT_HA_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_PORT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
public class YPointServer42HaFlowRttTransitRuleGenerator extends NotIngressRuleGenerator implements RuleGenerator {

    private final List<FlowPath> subPaths;
    private final int inPort;
    private final Map<PathId, Integer> outPorts;
    private final FlowTransitEncapsulation encapsulation;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        return subPaths.stream()
                .map(subPath ->  FlowSpeakerData.builder()
                        .switchId(sw.getSwitchId())
                        .ofVersion(OfVersion.of(sw.getOfVersion()))
                        .cookie(subPath.getCookie().toBuilder().haSubFlowServer42(true).build())
                        .table(OfTable.INPUT)
                        .priority(SERVER_42_FLOW_RTT_INPUT_TRANSIT_HA_PRIORITY)
                        .match(buildMatch(sw, subPath.getDestSwitchId().toMacAddressAsLong()))
                        .instructions(buildInstructions(subPath))
                        .build())
                .collect(Collectors.toList());
    }


    private Instructions buildInstructions(FlowPath subPath) {
        return Instructions.builder()
                .applyActions(Collections.singletonList(
                        new PortOutAction(new PortNumber(outPorts.get(subPath.getPathId())))))
                .build();
    }


    private Set<FieldMatch> buildMatch(Switch sw, long dstMacAddress) {
        int udpSrc;
        int udpDst;
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                udpSrc = SERVER_42_FLOW_RTT_REVERSE_UDP_PORT;
                udpDst = SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
                break;
            case VXLAN:
                udpSrc = SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT;
                udpDst = VXLAN_UDP_DST;
                break;
            default:
                throw new IllegalArgumentException(format("Unknown encapsulation type %s", encapsulation.getType()));
        }

        Set<FieldMatch> match = makeTransitMatch(sw, inPort, encapsulation);
        match.addAll(Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(dstMacAddress).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(udpSrc).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(udpDst).build()
        ));
        return match;
    }
}

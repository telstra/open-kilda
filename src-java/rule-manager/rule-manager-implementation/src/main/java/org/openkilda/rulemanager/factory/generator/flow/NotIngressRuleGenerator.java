/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.flow;

import static java.lang.String.format;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;

import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@SuperBuilder
public abstract class NotIngressRuleGenerator implements RuleGenerator {
    protected Set<FieldMatch> makeTransitMatch(Switch sw, int port, FlowTransitEncapsulation encapsulation) {
        Set<FieldMatch> result = new HashSet<>();
        result.add(FieldMatch.builder().field(Field.IN_PORT).value(port).build());

        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                result.add(FieldMatch.builder().field(Field.VLAN_VID).value(encapsulation.getId()).build());
                break;
            case VXLAN:
                result.addAll(makeTransitVxLanMatch(sw, encapsulation.getId()));
                break;
            default:
                throw new IllegalArgumentException(format("Unknown encapsulation type %s", encapsulation.getType()));
        }
        return result;
    }

    private Set<FieldMatch> makeTransitVxLanMatch(Switch sw, long vni) {
        Set<FieldMatch> result = new HashSet<>();
        result.add(FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build());
        result.add(FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build());
        result.add(FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build());

        if (sw.getFeatures().contains(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN)) {
            result.add(FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(vni).build());
        } else if (sw.getFeatures().contains(SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            result.add(FieldMatch.builder().field(Field.OVS_VXLAN_VNI).value(vni).build());
        } else {
            throw new IllegalArgumentException(format("Switch %s must support one of following features to match "
                            + "VXLAN: [%s, %s]", sw.getSwitchId(), NOVIFLOW_PUSH_POP_VXLAN,
                    KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
        return result;
    }
}

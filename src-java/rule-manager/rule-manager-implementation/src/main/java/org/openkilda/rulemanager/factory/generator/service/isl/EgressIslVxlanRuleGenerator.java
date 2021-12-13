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

package org.openkilda.rulemanager.factory.generator.service.isl;

import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.rulemanager.Constants.Priority.ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE;
import static org.openkilda.rulemanager.Constants.STUB_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
public class EgressIslVxlanRuleGenerator implements RuleGenerator {

    private int islPort;

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        Set<SwitchFeature> features = sw.getFeatures();
        if (!(features.contains(NOVIFLOW_PUSH_POP_VXLAN) || features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN))) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = buildEgressIslVxlanRuleMatch(sw.getSwitchId(), features);
        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.EGRESS)
                .build();

        return Collections.singletonList(FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_EGRESS_RULES, islPort))
                .table(OfTable.INPUT)
                .priority(ISL_EGRESS_VXLAN_RULE_PRIORITY_MULTITABLE)
                .match(match)
                .instructions(instructions)
                .build());
    }

    private Set<FieldMatch> buildEgressIslVxlanRuleMatch(SwitchId switchId, Set<SwitchFeature> features) {
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(switchId.toLong()).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.IN_PORT).value(islPort).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(STUB_VXLAN_UDP_SRC).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build()
        );
        if (features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            match.add(FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build());
        }
        return match;
    }
}

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
import static org.openkilda.rulemanager.Constants.Priority.ISL_TRANSIT_VXLAN_RULE_PRIORITY;
import static org.openkilda.rulemanager.Constants.STUB_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
public class TransitIslVxlanRuleGenerator implements RuleGenerator {

    private int islPort;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        Set<SwitchFeature> features = sw.getFeatures();
        if (!(features.contains(NOVIFLOW_PUSH_POP_VXLAN) || features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN))) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = buildTransitIslVxlanRuleMatch();
        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.TRANSIT)
                .build();
        return Collections.singletonList(FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new PortColourCookie(CookieType.ISL_VXLAN_TRANSIT_RULES, islPort))
                .table(OfTable.INPUT)
                .priority(ISL_TRANSIT_VXLAN_RULE_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build());
    }

    private Set<FieldMatch> buildTransitIslVxlanRuleMatch() {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.IN_PORT).value(islPort).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(STUB_VXLAN_UDP_SRC).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build()
        );
    }

}

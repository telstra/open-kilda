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

import static org.openkilda.rulemanager.Constants.Priority.ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
public class EgressIslVlanRuleGenerator implements RuleGenerator {

    private int islPort;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        Set<FieldMatch> match = Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(islPort).build());

        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.EGRESS)
                .build();

        return Collections.singletonList(FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES, islPort))
                .table(OfTable.INPUT)
                .priority(ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE)
                .match(match)
                .instructions(instructions)
                .build());
    }
}

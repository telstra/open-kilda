/* Copyright 2022 Telstra Open Source
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

import static org.openkilda.rulemanager.utils.Utils.checkAndBuildIngressEndpoint;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@Slf4j
public class VlanStatsRuleGenerator implements RuleGenerator {

    private final Flow flow;
    private final FlowPath flowPath;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        FlowEndpoint ingressEndpoint = checkAndBuildIngressEndpoint(flow, flowPath, sw.getSwitchId());
        if (!ingressEndpoint.isFullPort() || flow.getVlanStatistics() == null || flow.getVlanStatistics().isEmpty()) {
            return result;
        }

        for (Integer vlan : flow.getVlanStatistics()) {
            result.add(buildVlanStatsCommand(sw, ingressEndpoint, vlan));
        }
        return result;
    }

    private FlowSpeakerData buildVlanStatsCommand(Switch sw, FlowEndpoint endpoint, int vlan) {
        FlowSegmentCookie cookie = new FlowSegmentCookie(flowPath.getCookie().getValue()).toBuilder()
                .type(CookieType.VLAN_STATS_PRE_INGRESS)
                .statsVlan(vlan)
                .build();

        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.INGRESS)
                .build();

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.PRE_INGRESS)
                .priority(Priority.DEFAULT_FLOW_VLAN_STATS_PRIORITY)
                .match(Sets.newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                        FieldMatch.builder().field(Field.VLAN_VID).value(vlan).build()))
                .instructions(instructions);

        return builder.build();
    }
}

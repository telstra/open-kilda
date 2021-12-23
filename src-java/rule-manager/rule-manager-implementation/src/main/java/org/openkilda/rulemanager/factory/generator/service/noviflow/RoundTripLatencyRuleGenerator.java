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

package org.openkilda.rulemanager.factory.generator.service.noviflow;

import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.cookie.Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.LATENCY_PACKET_UDP_PORT;
import static org.openkilda.rulemanager.Constants.Priority.ROUND_TRIP_LATENCY_RULE_PRIORITY;
import static org.openkilda.rulemanager.Constants.ROUND_TRIP_LATENCY_T1_OFFSET;
import static org.openkilda.rulemanager.Constants.ROUND_TRIP_LATENCY_TIMESTAMP_SIZE;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Builder
public class RoundTripLatencyRuleGenerator implements RuleGenerator {

    private RuleManagerConfig config;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (!sw.getFeatures().contains(NOVIFLOW_COPY_FIELD)) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = roundTripLatencyRuleMatch(sw);
        List<Action> actions = ImmutableList.of(
                actionAddRxTimestamp(),
                new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        return Collections.singletonList(FlowSpeakerData.builder()
                        .switchId(sw.getSwitchId())
                        .ofVersion(OfVersion.of(sw.getOfVersion()))
                        .cookie(new Cookie(ROUND_TRIP_LATENCY_RULE_COOKIE))
                        .table(OfTable.INPUT)
                        .priority(ROUND_TRIP_LATENCY_RULE_PRIORITY)
                        .match(match)
                        .instructions(instructions)
                .build());
    }

    private Set<FieldMatch> roundTripLatencyRuleMatch(Switch sw) {
        long ethDst = new SwitchId(config.getDiscoveryBcastPacketDst()).toLong();
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(sw.getSwitchId().toLong()).build(),
                FieldMatch.builder().field(Field.ETH_DST).value(ethDst).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(LATENCY_PACKET_UDP_PORT).build()
        );
    }

    private static Action actionAddRxTimestamp() {
        return CopyFieldAction.builder()
                .numberOfBits(ROUND_TRIP_LATENCY_TIMESTAMP_SIZE)
                .srcOffset(0)
                .dstOffset(ROUND_TRIP_LATENCY_T1_OFFSET)
                .oxmSrcHeader(OpenFlowOxms.NOVIFLOW_RX_TIMESTAMP)
                .oxmDstHeader(OpenFlowOxms.NOVIFLOW_PACKET_OFFSET)
                .build();
    }
}

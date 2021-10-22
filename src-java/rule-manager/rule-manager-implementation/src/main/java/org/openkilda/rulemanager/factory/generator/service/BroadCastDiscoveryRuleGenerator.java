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

package org.openkilda.rulemanager.factory.generator.service;

import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;
import static org.openkilda.model.SwitchFeature.MATCH_UDP_PORT;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.DISCOVERY_PACKET_UDP_PORT;
import static org.openkilda.rulemanager.Constants.LATENCY_PACKET_UDP_PORT;
import static org.openkilda.rulemanager.Constants.Priority.DISCOVERY_RULE_PRIORITY;
import static org.openkilda.rulemanager.OfTable.INPUT;

import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.GroupSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.Mask;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BroadCastDiscoveryRuleGenerator extends MeteredServiceRuleGenerator {

    @Builder
    public BroadCastDiscoveryRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        List<SpeakerCommandData> commands = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        FlowSpeakerCommandData flowCommand = buildRule(sw, instructions);
        commands.add(flowCommand);

        MeterId meterId = createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE);
        SpeakerCommandData meterCommand = generateMeterCommandForServiceRule(sw, meterId,
                config.getBroadcastRateLimit(), config.getSystemMeterBurstSizeInPackets(), config.getDiscoPacketSize());
        if (meterCommand != null) {
            commands.add(meterCommand);
            addMeterToInstructions(meterId, sw, instructions);
        }

        GroupSpeakerCommandData groupCommand = null;
        if (sw.getFeatures().contains(SwitchFeature.GROUP_PACKET_OUT_CONTROLLER)) {
            groupCommand = getRoundTripLatencyGroup(sw);
            actions.add(new GroupAction(groupCommand.getGroupId()));
            commands.add(groupCommand);
        } else {
            actions.add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        }

        if (meterCommand != null) {
            flowCommand.getDependsOn().add(meterCommand.getUuid());
        }
        if (groupCommand != null) {
            flowCommand.getDependsOn().add(groupCommand.getUuid());
        }

        return commands;
    }

    private FlowSpeakerCommandData buildRule(Switch sw, Instructions instructions) {
        return FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new Cookie(VERIFICATION_BROADCAST_RULE_COOKIE))
                .table(INPUT)
                .priority(DISCOVERY_RULE_PRIORITY)
                .match(buildMatch(sw))
                .instructions(instructions)
                .dependsOn(new ArrayList<>())
                .build();
    }

    private Set<FieldMatch> buildMatch(Switch sw) {
        long dstMac = new SwitchId(config.getDiscoveryBcastPacketDst()).toLong();
        Set<FieldMatch> match = new HashSet<>();

        match.add(FieldMatch.builder()
                .field(Field.ETH_DST)
                .value(dstMac)
                .mask(Mask.NO_MASK)
                .build());
        if (sw.getFeatures().contains(MATCH_UDP_PORT)) {
            match.add(FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build());
            match.add(FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build());
            match.add(FieldMatch.builder().field(Field.UDP_DST).value(DISCOVERY_PACKET_UDP_PORT).build());
        }

        return match;
    }

    private static GroupSpeakerCommandData getRoundTripLatencyGroup(Switch sw) {
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(Bucket.builder()
                .writeActions(Sets.newHashSet(
                        // todo: remove useless set ETH_DST action
                        actionSetDstMac(sw.getSwitchId().toLong()),
                        new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER))))
                .build());
        buckets.add(Bucket.builder()
                .writeActions(Sets.newHashSet(
                        SetFieldAction.builder().field(Field.UDP_DST).value(LATENCY_PACKET_UDP_PORT).build(),
                        new PortOutAction(new PortNumber(SpecialPortType.IN_PORT))))
                .build());

        return GroupSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .groupId(GroupId.ROUND_TRIP_LATENCY_GROUP_ID)
                .type(GroupType.ALL)
                .buckets(buckets)
                .build();
    }

    private static Action actionSetDstMac(long mac) {
        return SetFieldAction.builder()
                .field(Field.ETH_DST)
                .value(mac)
                .build();
    }
}

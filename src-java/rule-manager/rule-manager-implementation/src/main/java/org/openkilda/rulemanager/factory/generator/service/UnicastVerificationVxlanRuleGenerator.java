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
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.VERIFICATION_RULE_VXLAN_PRIORITY;
import static org.openkilda.rulemanager.Constants.STUB_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Field.ETH_DST;
import static org.openkilda.rulemanager.Field.ETH_SRC;
import static org.openkilda.rulemanager.Field.ETH_TYPE;
import static org.openkilda.rulemanager.Field.IP_PROTO;
import static org.openkilda.rulemanager.Field.UDP_SRC;
import static org.openkilda.rulemanager.ProtoConstants.Mask.NO_MASK;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UnicastVerificationVxlanRuleGenerator extends MeteredServiceRuleGenerator {

    @Builder
    public UnicastVerificationVxlanRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        // should be replaced with fair feature detection based on ActionId's during handshake
        if (!sw.getFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            return Collections.emptyList();
        }

        Cookie cookie = new Cookie(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
        FlowSpeakerCommandData flowCommand = buildUnicastVerificationRuleVxlan(sw, cookie);
        List<SpeakerCommandData> result = new ArrayList<>();
        result.add(flowCommand);

        MeterId meterId = createMeterIdForDefaultRule(cookie.getValue());
        long meterRate = config.getUnicastRateLimit();
        MeterSpeakerCommandData meterCommand = generateMeterCommandForServiceRule(sw, meterId, meterRate,
                config.getSystemMeterBurstSizeInPackets(), config.getDiscoPacketSize());
        if (meterCommand != null) {
            addMeterToInstructions(meterId, sw, flowCommand.getInstructions());
            flowCommand.getDependsOn().add(meterCommand.getUuid());
            result.add(meterCommand);
        }

        return result;
    }

    private FlowSpeakerCommandData buildUnicastVerificationRuleVxlan(Switch sw, Cookie cookie) {
        long ethSrc = new SwitchId(config.getFlowPingMagicSrcMacAddress()).toLong();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(ETH_SRC).value(ethSrc).mask(NO_MASK).build(),
                FieldMatch.builder().field(ETH_DST).value(sw.getSwitchId().toLong()).mask(NO_MASK).build(),
                FieldMatch.builder().field(ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(UDP_SRC).value(STUB_VXLAN_UDP_SRC).build()
        );

        List<Action> actions = new ArrayList<>();
        actions.add(new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW));
        actions.add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));

        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();

        return FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.INPUT)
                .priority(VERIFICATION_RULE_VXLAN_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build();
    }
}

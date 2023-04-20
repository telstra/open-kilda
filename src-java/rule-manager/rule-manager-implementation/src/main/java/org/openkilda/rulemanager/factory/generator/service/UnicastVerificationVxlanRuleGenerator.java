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
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
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
import static org.openkilda.rulemanager.utils.Utils.buildPopVxlan;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerData;
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
import org.openkilda.rulemanager.action.SetFieldAction;
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
    public List<SpeakerData> generateCommands(Switch sw) {
        // should be replaced with fair feature detection based on ActionId's during handshake
        if (!(sw.getFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)
                || sw.getFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN))) {
            return Collections.emptyList();
        }

        Cookie cookie = new Cookie(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE);
        FlowSpeakerData flowCommand = buildUnicastVerificationRuleVxlan(sw, cookie);
        List<SpeakerData> result = new ArrayList<>();
        result.add(flowCommand);

        MeterId meterId = createMeterIdForDefaultRule(cookie.getValue());
        long meterRate = config.getUnicastRateLimit();
        MeterSpeakerData meterCommand = generateMeterCommandForServiceRule(sw, meterId, meterRate,
                config.getSystemMeterBurstSizeInPackets(), config.getDiscoPacketSize());
        if (meterCommand != null) {
            addMeterToInstructions(meterId, sw, flowCommand.getInstructions());
            flowCommand.getDependsOn().add(meterCommand.getUuid());
            result.add(meterCommand);
        }

        return result;
    }

    private FlowSpeakerData buildUnicastVerificationRuleVxlan(Switch sw, Cookie cookie) {
        List<Action> actions = new ArrayList<>();
        actions.add(buildPopVxlan(sw.getSwitchId(), sw.getFeatures()));
        actions.add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        // todo remove unnecessary action
        actions.add(SetFieldAction.builder().field(ETH_DST).value(sw.getSwitchId().toLong()).build());

        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        long ethSrc = new SwitchId(config.getFlowPingMagicSrcMacAddress()).toLong();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(ETH_SRC).value(ethSrc).mask(NO_MASK).build(),
                FieldMatch.builder().field(ETH_DST).value(sw.getSwitchId().toLong()).mask(NO_MASK).build(),
                FieldMatch.builder().field(ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(UDP_SRC).value(STUB_VXLAN_UDP_SRC).build()
        );

        return FlowSpeakerData.builder()
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

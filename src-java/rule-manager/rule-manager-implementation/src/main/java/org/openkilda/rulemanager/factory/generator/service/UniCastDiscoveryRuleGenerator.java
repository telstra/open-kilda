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
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.DISCOVERY_RULE_PRIORITY;
import static org.openkilda.rulemanager.OfTable.INPUT;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.Mask;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UniCastDiscoveryRuleGenerator extends MeteredServiceRuleGenerator {

    @Builder
    public UniCastDiscoveryRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> commands = new ArrayList<>();
        Instructions instructions = Instructions.builder()
                .applyActions(new ArrayList<>())
                .build();
        FlowSpeakerData flowCommand = buildRule(sw, instructions);
        if (flowCommand == null) {
            return Collections.emptyList();
        } else {
            commands.add(flowCommand);
        }

        MeterId meterId = createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE);
        SpeakerData meterCommand = generateMeterCommandForServiceRule(sw, meterId, config.getUnicastRateLimit(),
                config.getSystemMeterBurstSizeInPackets(), config.getDiscoPacketSize());
        if (meterCommand != null) {
            commands.add(meterCommand);
            addMeterToInstructions(meterId, sw, instructions);
        }
        instructions.getApplyActions().add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));

        if (meterCommand != null) {
            flowCommand.getDependsOn().add(meterCommand.getUuid());
        }

        return commands;
    }

    private FlowSpeakerData buildRule(Switch sw, Instructions instructions) {
        OfVersion ofVersion = OfVersion.of(sw.getOfVersion());
        if (ofVersion == OfVersion.OF_12) {
            return null;
        }

        return FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(ofVersion)
                .cookie(new Cookie(VERIFICATION_UNICAST_RULE_COOKIE))
                .table(INPUT)
                .priority(DISCOVERY_RULE_PRIORITY)
                .match(buildMatch(sw))
                .instructions(instructions)
                .dependsOn(new ArrayList<>())
                .build();
    }

    private Set<FieldMatch> buildMatch(Switch sw) {
        Set<FieldMatch> match = new HashSet<>();
        long srcMac = new SwitchId(config.getFlowPingMagicSrcMacAddress()).toLong();
        long dstMac = sw.getSwitchId().toLong();
        match.add(FieldMatch.builder()
                .field(Field.ETH_SRC)
                .value(srcMac)
                .mask(Mask.NO_MASK).build());
        match.add(FieldMatch.builder()
                .field(Field.ETH_DST)
                .value(dstMac)
                .mask(Mask.NO_MASK).build());
        return match;
    }
}

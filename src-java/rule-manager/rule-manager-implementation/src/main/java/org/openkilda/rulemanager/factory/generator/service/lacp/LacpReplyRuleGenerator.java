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

package org.openkilda.rulemanager.factory.generator.service.lacp;

import static org.openkilda.model.MeterId.LACP_REPLY_METER_ID;
import static org.openkilda.rulemanager.Constants.Priority.LACP_RULE_PRIORITY;
import static org.openkilda.rulemanager.OfTable.INPUT;

import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.generator.service.MeteredServiceRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LacpReplyRuleGenerator extends MeteredServiceRuleGenerator {

    private final int inPort;
    private final boolean switchHasOtherLacpPorts;

    @Builder
    public LacpReplyRuleGenerator(RuleManagerConfig config, int inPort, boolean switchHasOtherLacpPorts) {
        super(config);
        this.inPort = inPort;
        this.switchHasOtherLacpPorts = switchHasOtherLacpPorts;
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

        MeterId meterId = LACP_REPLY_METER_ID;
        SpeakerData meterCommand = generateMeterCommandForServiceRule(sw, meterId, config.getLacpRateLimit(),
                config.getLacpMeterBurstSizeInPackets(), config.getLacpPacketSize());
        if (meterCommand != null) {
            if (!switchHasOtherLacpPorts) { // meter was already created by other rules
                commands.add(meterCommand);
            }
            addMeterToInstructions(meterId, sw, instructions);
        }
        instructions.getApplyActions().add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        if (meterCommand != null && !switchHasOtherLacpPorts) {
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
                .cookie(new PortColourCookie(CookieType.LACP_REPLY_INPUT, inPort))
                .table(INPUT)
                .priority(LACP_RULE_PRIORITY)
                .match(buildMatch())
                .instructions(instructions)
                .dependsOn(new ArrayList<>())
                .build();
    }

    private Set<FieldMatch> buildMatch() {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(inPort).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.SLOW_PROTOCOLS).build(),
                FieldMatch.builder().field(Field.ETH_DST).value(MacAddress.SLOW_PROTOCOLS.toLong()).build());
    }
}

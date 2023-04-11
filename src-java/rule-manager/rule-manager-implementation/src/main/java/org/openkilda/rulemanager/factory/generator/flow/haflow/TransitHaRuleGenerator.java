/* Copyright 2023 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuperBuilder
public class TransitHaRuleGenerator extends NotIngressRuleGenerator implements MeteredRuleGenerator {

    private final FlowPath subPath;
    private final int inPort;
    private final int outPort;
    private final FlowTransitEncapsulation encapsulation;
    private final boolean sharedSegment;
    private final MeterId sharedMeterId;
    private final UUID externalMeterCommandUuid;
    private final boolean generateCreateMeterCommand;
    private final RuleManagerConfig config;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (subPath.isOneSwitchPath()) {
            return new ArrayList<>();
        }

        SpeakerData transitCommand = buildTransitCommand(sw, inPort, outPort);
        List<SpeakerData> result = Lists.newArrayList(transitCommand);

        buildMeterCommandAndAddDependency(sharedMeterId, subPath.getBandwidth(), transitCommand,
                externalMeterCommandUuid, config, generateCreateMeterCommand, sw)
                .ifPresent(result::add);
        return result;
    }

    private SpeakerData buildTransitCommand(Switch sw, int inPort, int outPort) {
        return FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(getCookie())
                .table(OfTable.TRANSIT)
                .priority(Priority.FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS))
                .instructions(buildInstructions(outPort, sw))
                .build();
    }

    private Instructions buildInstructions(int outPort, Switch sw) {
        Instructions instructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PortOutAction(new PortNumber(outPort))))
                .build();
        addMeterToInstructions(sharedMeterId, sw, instructions);
        return instructions;
    }

    private FlowSegmentCookie getCookie() {
        if (sharedSegment) {
            return subPath.getCookie().toBuilder().subType(FlowSubType.SHARED).build();
        } else {
            return subPath.getCookie();
        }
    }
}

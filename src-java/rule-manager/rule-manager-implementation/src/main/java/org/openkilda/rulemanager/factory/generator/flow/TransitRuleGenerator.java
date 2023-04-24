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

package org.openkilda.rulemanager.factory.generator.flow;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;

import com.google.common.collect.Lists;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
public class TransitRuleGenerator extends NotIngressRuleGenerator {

    protected final FlowPath flowPath;
    protected final int inPort;
    protected final int outPort;
    protected final boolean multiTable;
    protected final FlowTransitEncapsulation encapsulation;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (flowPath.isOneSwitchPath()) {
            return new ArrayList<>();
        }

        return Lists.newArrayList(buildTransitCommand(sw, inPort, outPort));
    }

    private SpeakerData buildTransitCommand(Switch sw, int inPort, int outPort) {
        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(multiTable ? OfTable.TRANSIT : OfTable.INPUT)
                .priority(Priority.FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder()
                        .applyActions(Lists.newArrayList(new PortOutAction(new PortNumber(outPort))))
                        .build());

        // todo add RESET_COUNTER flag
        return builder.build();
    }
}

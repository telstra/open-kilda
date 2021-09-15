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

package org.openkilda.rulemanager.factory.generator.service.arp;

import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.factory.generator.service.MeteredServiceRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.Set;

public abstract class ArpRuleGenerator extends MeteredServiceRuleGenerator {

    public ArpRuleGenerator(RuleManagerConfig config) {
        super(config);
    }

    protected MeterSpeakerCommandData generateMeter(Switch sw, Cookie cookie) {
        MeterId meterId = createMeterIdForDefaultRule(cookie.getValue());
        return generateMeterCommandForServiceRule(sw, meterId, config.getArpRateLimit(),
                config.getArpMeterBurstSizeInPackets(), config.getArpPacketSize());
    }

    protected List<SpeakerCommandData> buildCommands(Switch sw, Cookie cookie, OfTable table, int priority,
                                                     Set<FieldMatch> match, Instructions instructions) {
        FlowSpeakerCommandData flowCommand = FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(table)
                .priority(priority)
                .match(match)
                .instructions(instructions)
                .build();

        List<SpeakerCommandData> result = Lists.newArrayList(flowCommand);

        MeterSpeakerCommandData meterCommand = generateMeter(sw, cookie);
        if (meterCommand != null) {
            result.add(meterCommand);
            addMeterToInstructions(meterCommand.getMeterId(), sw, instructions);
            flowCommand.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }
}

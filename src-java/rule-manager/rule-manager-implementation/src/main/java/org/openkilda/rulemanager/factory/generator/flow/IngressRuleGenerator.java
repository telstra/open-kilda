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

import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.rulemanager.MeterFlag.BURST;
import static org.openkilda.rulemanager.MeterFlag.KBPS;
import static org.openkilda.rulemanager.MeterFlag.STATS;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;

@SuperBuilder
@AllArgsConstructor
public abstract class IngressRuleGenerator extends MeteredRuleGenerator {

    public static final HashSet<MeterFlag> FLOW_METER_STATS = Sets.newHashSet(KBPS, BURST, STATS);

    protected RuleManagerConfig config;
    protected final FlowPath flowPath;
    protected final Flow flow;
    protected final FlowTransitEncapsulation encapsulation;

    protected SpeakerCommandData buildMeter(MeterId meterId, Switch sw) {
        if (meterId == null || !sw.getFeatures().contains(METERS)) {
            return null;
        }

        long burstSize = Meter.calculateBurstSize(
                flowPath.getBandwidth(), config.getFlowMeterMinBurstSizeInKbits(),
                config.getFlowMeterBurstCoefficient(),
                flowPath.getSrcSwitch().getOfDescriptionManufacturer(),
                flowPath.getSrcSwitch().getOfDescriptionSoftware());

        return MeterSpeakerCommandData.builder()
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .meterId(meterId)
                .switchId(flowPath.getSrcSwitchId())
                .rate(flowPath.getBandwidth())
                .burst(burstSize)
                .flags(FLOW_METER_STATS)
                .build();
    }

    @Override
    protected void addMeterToInstructions(MeterId meterId, Switch sw, Instructions instructions) {
        if (meterId != null && sw.getFeatures().contains(METERS)) {
            super.addMeterToInstructions(meterId, sw, instructions);
        }
    }
}

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

package org.openkilda.rulemanager.factory;

import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.rulemanager.factory.generator.flow.IngressRuleGenerator.FLOW_METER_STATS;

import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.MeterAction;

import java.util.UUID;

public interface MeteredRuleGenerator extends RuleGenerator {

    /**
     * Adds meter to the list of to be applied actions.
     * @param meterId meter id
     * @param sw targer switch
     * @param instructions instructions to be updated
     */
    default void addMeterToInstructions(MeterId meterId, Switch sw, Instructions instructions) {
        if (meterId != null && meterId.getValue() != 0L && sw.getFeatures().contains(METERS)) {
            OfVersion ofVersion = OfVersion.of(sw.getOfVersion());
            if (ofVersion == OfVersion.OF_15) {
                instructions.getApplyActions().add(new MeterAction(meterId));
            } else /* OF_14 and earlier */ {
                instructions.setGoToMeter(meterId);
            }
        }
    }

    /**
     * Build meter command data.
     * @param uuid command data uuid.
     * @param flowPath target flow path
     * @param config config to be used
     * @param meterId target meter id. NB: it might be different from flow paths reference
     * @param sw target switch
     * @return command data
     */
    default SpeakerData buildMeter(UUID uuid, FlowPath flowPath, RuleManagerConfig config, MeterId meterId,
                                   Switch sw) {
        if (meterId == null || !sw.getFeatures().contains(METERS)) {
            return null;
        }

        long burstSize = Meter.calculateBurstSize(
                flowPath.getBandwidth(), config.getFlowMeterMinBurstSizeInKbits(),
                config.getFlowMeterBurstCoefficient(),
                flowPath.getSrcSwitch().getOfDescriptionManufacturer(),
                flowPath.getSrcSwitch().getOfDescriptionSoftware());
        return MeterSpeakerData.builder()
                .uuid(uuid)
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .meterId(meterId)
                .switchId(sw.getSwitchId())
                .rate(flowPath.getBandwidth())
                .burst(burstSize)
                .flags(FLOW_METER_STATS)
                .build();
    }

    /**
     * Build meter command data.
     * @param flowPath target flow path
     * @param config config to be used
     * @param meterId target meter id. NB: it might be different from flow paths reference
     * @param sw target switch
     * @return command data
     */
    default SpeakerData buildMeter(FlowPath flowPath, RuleManagerConfig config, MeterId meterId, Switch sw) {
        return buildMeter(UUID.randomUUID(), flowPath, config, meterId, sw);
    }
}

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

import static org.openkilda.model.SwitchFeature.INACCURATE_METER;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.rulemanager.MeterFlag.BURST;
import static org.openkilda.rulemanager.MeterFlag.KBPS;
import static org.openkilda.rulemanager.MeterFlag.STATS;

import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.MeterAction;

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MeteredRuleGenerator extends RuleGenerator {
    HashSet<MeterFlag> FLOW_METER_STATS = Sets.newHashSet(KBPS, BURST, STATS);

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
     * @param bandwidth meter rate.
     * @param config config to be used
     * @param meterId target meter id. NB: it might be different from flow paths reference
     * @param sw target switch
     * @return command data
     */
    default SpeakerData buildMeter(UUID uuid, long bandwidth, RuleManagerConfig config, MeterId meterId, Switch sw) {
        Set<SwitchFeature> switchFeatures = sw.getFeatures();
        if (meterId == null || !switchFeatures.contains(METERS)) {
            return null;
        }

        long burstSize = Meter.calculateBurstSize(
                bandwidth, config.getFlowMeterMinBurstSizeInKbits(),
                config.getFlowMeterBurstCoefficient(),
                sw.getOfDescriptionManufacturer(),
                sw.getOfDescriptionSoftware());
        return MeterSpeakerData.builder()
                .uuid(uuid)
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .meterId(meterId)
                .switchId(sw.getSwitchId())
                .rate(bandwidth)
                .burst(burstSize)
                .flags(FLOW_METER_STATS)
                .inaccurate(switchFeatures.contains(INACCURATE_METER))
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

    /**
     * Builds meter command data.
     * @param uuid command data uuid.
     * @param flowPath target flow path
     * @param config config to be used
     * @param meterId target meter id. NB: it might be different from flow paths reference
     * @param sw target switch
     * @return command data
     */
    default SpeakerData buildMeter(UUID uuid, FlowPath flowPath, RuleManagerConfig config, MeterId meterId, Switch sw) {
        return buildMeter(uuid, flowPath.getBandwidth(), config, meterId, sw);
    }

    /**
     * Builds meter command and adds meter command dependency, if required.
     * @param meterId meter id to be created. Zero or null meterId means no meter will be created.
     * @param bandwidth meter rate.
     * @param ruleCommand command which will use goToMeter instruction to apply the meter.
     * @param externalMeterCommandUuid UUID of external command which will create the meter.
     *                                 If UUID is null random UUID will be generated for meter command
     * @param config rule manager config
     * @param generateCreateMeterCommand If true command for meter creation will be generated.
     *                                   If false - no meter command will be generated. Second case is used when shared
     *                                   meter was already created by some other generator. In this case method must
     *                                   just add dependency on that external command (externalMeterCommandUuid).
     * @param sw Switch on which meter will be installed.
     * @return Optional with Meter command or empty Optional if command is not required of the switch doesn't support
     *         meters.
     */
    default Optional<SpeakerData> buildMeterCommandAndAddDependency(
            MeterId meterId, long bandwidth, SpeakerData ruleCommand, UUID externalMeterCommandUuid,
            RuleManagerConfig config, boolean generateCreateMeterCommand, Switch sw) {
        if (meterId == null) {
            return Optional.empty();
        }
        if (externalMeterCommandUuid == null) {
            externalMeterCommandUuid = UUID.randomUUID();
        }

        if (generateCreateMeterCommand) {
            SpeakerData meterCommand = buildMeter(externalMeterCommandUuid, bandwidth, config, meterId, sw);
            if (meterCommand != null) {
                ruleCommand.getDependsOn().add(externalMeterCommandUuid);
                return Optional.of(meterCommand);
            }
        } else if (sw.getFeatures().contains(METERS)) {
            ruleCommand.getDependsOn().add(externalMeterCommandUuid);
        }
        return Optional.empty();
    }
}

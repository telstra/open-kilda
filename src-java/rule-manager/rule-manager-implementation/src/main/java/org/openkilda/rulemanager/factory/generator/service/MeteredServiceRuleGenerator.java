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

import static com.google.common.collect.Sets.newHashSet;
import static org.openkilda.model.SwitchFeature.METERS;

import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
public abstract class MeteredServiceRuleGenerator extends MeteredRuleGenerator {

    protected RuleManagerConfig config;

    protected MeterSpeakerCommandData generateMeterCommandForServiceRule(Switch sw,
                                                                         MeterId meterId,
                                                                         long rateInPackets,
                                                                         long burstSizeInPackets,
                                                                         long packetSizeInBytes) {
        if (!sw.getFeatures().contains(METERS)) {
            return null;
        }
        long rate;
        long burstSize;
        Set<MeterFlag> flags = newHashSet(MeterFlag.BURST, MeterFlag.STATS);
        Set<SwitchFeature> switchFeatures = sw.getFeatures();

        if (switchFeatures.contains(SwitchFeature.PKTPS_FLAG)) {
            flags.add(MeterFlag.PKTPS);
            // With PKTPS flag rate and burst size is in packets
            rate = rateInPackets;
            burstSize = Meter.calculateBurstSizeConsideringHardwareLimitations(rate, burstSizeInPackets,
                    switchFeatures);
        } else {
            flags.add(MeterFlag.KBPS);
            // With KBPS flag rate and burst size is in Kbits
            rate = Meter.convertRateToKiloBits(rateInPackets, packetSizeInBytes);
            long burstSizeInKiloBits = Meter.convertBurstSizeToKiloBits(burstSizeInPackets, packetSizeInBytes);
            burstSize = Meter.calculateBurstSizeConsideringHardwareLimitations(rate, burstSizeInKiloBits,
                    switchFeatures);
        }

        return MeterSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .meterId(meterId)
                .rate(rate)
                .burst(burstSize)
                .flags(flags)
                .build();
    }

    protected static Instructions buildSendToControllerInstructions() {
        List<Action> actions = new ArrayList<>();
        actions.add(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER)));
        return Instructions.builder()
                .applyActions(actions)
                .build();
    }

    protected static RoutingMetadata buildMetadata(RoutingMetadata.RoutingMetadataBuilder builder, Switch sw) {
        return builder.build(sw.getFeatures());
    }
}

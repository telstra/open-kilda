/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager.factory.generator;

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.buildMeterMod;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.isOvs;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_15;

import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.model.Meter;
import org.openkilda.model.SwitchFeature;

import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMeterFlags;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;

import java.util.List;
import java.util.Set;

@AllArgsConstructor
public abstract class MeteredFlowGenerator implements SwitchFlowGenerator {

    protected FeatureDetectorService featureDetectorService;
    protected SwitchManagerConfig config;

    protected OFMeterMod generateMeterForDefaultRule(IOFSwitch sw, long meterId, long rateInPackets,
                                                     long burstSizeInPackets, long packetSizeInBytes) {
        //FIXME: Since we can't read/validate meters from switches with OF 1.2 we should not install them
        if (sw.getOFFactory().getVersion().compareTo(OF_12) <= 0) {
            return null;
        }
        long rate;
        long burstSize;
        Set<OFMeterFlags> flags;
        Set<SwitchFeature> switchFeatures = featureDetectorService.detectSwitch(sw);

        if (switchFeatures.contains(SwitchFeature.PKTPS_FLAG)) {
            flags = ImmutableSet.of(OFMeterFlags.PKTPS, OFMeterFlags.STATS, OFMeterFlags.BURST);
            // With PKTPS flag rate and burst size is in packets
            rate = rateInPackets;
            burstSize = Meter.calculateBurstSizeConsideringHardwareLimitations(rate, burstSizeInPackets,
                    switchFeatures);
        } else {
            flags = ImmutableSet.of(OFMeterFlags.KBPS, OFMeterFlags.STATS, OFMeterFlags.BURST);
            // With KBPS flag rate and burst size is in Kbits
            rate = Meter.convertRateToKiloBits(rateInPackets, packetSizeInBytes);
            long burstSizeInKiloBits = Meter.convertBurstSizeToKiloBits(burstSizeInPackets, packetSizeInBytes);
            burstSize = Meter.calculateBurstSizeConsideringHardwareLimitations(rate, burstSizeInKiloBits,
                    switchFeatures);
        }

        return buildMeterMod(sw.getOFFactory(), rate, burstSize, meterId, flags);
    }

    protected OFInstructionMeter buildMeterInstruction(long meterId, IOFSwitch sw, List<OFAction> actionList) {
        OFFactory ofFactory = sw.getOFFactory();
        OFInstructionMeter meterInstruction = null;
        if (meterId != 0L && (config.isOvsMetersEnabled() || !isOvs(sw))) {
            if (ofFactory.getVersion().compareTo(OF_12) <= 0) {
                /* FIXME: Since we can't read/validate meters from switches with OF 1.2 we should not install them
                actionList.add(legacyMeterAction(ofFactory, meterId));
                */
            } else if (ofFactory.getVersion().compareTo(OF_15) == 0) {
                actionList.add(ofFactory.actions().buildMeter().setMeterId(meterId).build());
            } else /* OF_13, OF_14 */ {
                meterInstruction = ofFactory.instructions().buildMeter().setMeterId(meterId).build();
            }
        }

        return meterInstruction;
    }
}

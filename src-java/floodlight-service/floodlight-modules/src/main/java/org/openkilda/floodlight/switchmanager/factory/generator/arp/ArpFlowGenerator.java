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

package org.openkilda.floodlight.switchmanager.factory.generator.arp;

import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;

import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.floodlight.switchmanager.factory.SwitchFlowTuple;
import org.openkilda.floodlight.switchmanager.factory.generator.MeteredFlowGenerator;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;

import java.util.ArrayList;
import java.util.List;

public abstract class ArpFlowGenerator extends MeteredFlowGenerator {

    public ArpFlowGenerator(FeatureDetectorService featureDetectorService, SwitchManagerConfig config) {
        super(featureDetectorService, config);
    }

    abstract OFFlowMod getArpFlowMod(IOFSwitch sw, OFInstructionMeter meter,
                                     List<OFAction> actionList);

    abstract long getCookie();

    @Override
    public SwitchFlowTuple generateFlow(IOFSwitch sw) {
        ArrayList<OFAction> actionList = new ArrayList<>();
        long cookie = getCookie();
        long meterId = createMeterIdForDefaultRule(cookie).getValue();
        OFMeterMod meter = generateMeterForDefaultRule(sw, meterId, config.getArpRateLimit(),
                config.getArpMeterBurstSizeInPackets(), config.getArpPacketSize());
        OFInstructionMeter ofInstructionMeter = buildMeterInstruction(meter.getMeterId(), sw, actionList);

        OFFlowMod flowMod = getArpFlowMod(sw, ofInstructionMeter, actionList);
        if (flowMod == null) {
            return SwitchFlowTuple.EMPTY;
        }

        return SwitchFlowTuple.builder()
                .sw(sw)
                .flow(flowMod)
                .meter(meter)
                .build();
    }
}

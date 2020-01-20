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

package org.openkilda.floodlight.switchmanager.factory.generator.lldp;

import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.actionSendToController;
import static org.openkilda.floodlight.switchmanager.SwitchFlowUtils.prepareFlowModBuilder;
import static org.openkilda.floodlight.switchmanager.SwitchManager.INGRESS_TABLE_ID;
import static org.openkilda.floodlight.switchmanager.SwitchManager.LLDP_INGRESS_PRIORITY;
import static org.openkilda.model.Cookie.LLDP_INGRESS_COOKIE;
import static org.openkilda.model.Metadata.METADATA_LLDP_MASK;
import static org.openkilda.model.Metadata.METADATA_LLDP_VALUE;

import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFMetadata;

import java.util.List;

public class LldpIngressFlowGenerator extends LldpFlowGenerator {

    @Builder
    public LldpIngressFlowGenerator(FeatureDetectorService featureDetectorService, SwitchManagerConfig config) {
        super(featureDetectorService, config);
    }

    @Override
    OFFlowMod getLldpFlowMod(IOFSwitch sw, OFInstructionMeter meter, List<OFAction> actionList) {
        OFFactory ofFactory = sw.getOFFactory();
        Match match = ofFactory.buildMatch()
                .setMasked(MatchField.METADATA, OFMetadata.ofRaw(METADATA_LLDP_VALUE),
                        OFMetadata.ofRaw(METADATA_LLDP_MASK))
                .build();

        actionList.add(actionSendToController(sw.getOFFactory()));
        OFInstructionApplyActions actions = ofFactory.instructions().applyActions(actionList).createBuilder().build();

        return prepareFlowModBuilder(ofFactory, getCookie(), LLDP_INGRESS_PRIORITY, INGRESS_TABLE_ID)
                .setMatch(match)
                .setInstructions(meter != null ? ImmutableList.of(meter, actions) : ImmutableList.of(actions))
                .build();
    }

    @Override
    long getCookie() {
        return LLDP_INGRESS_COOKIE;
    }
}

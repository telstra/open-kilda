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

package org.openkilda.rulemanager.factory.generator.service.server42;

import static org.openkilda.model.SwitchFeature.KILDA_OVS_SWAP_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_SWAP_ETH_SRC_ETH_DST;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_FLOW_RTT_VXLAN_TURNING_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.match.FieldMatch;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Server42FlowRttVxlanTurningRuleGenerator extends Server42FlowRttTurningRuleGenerator {

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        Set<SwitchFeature> features = sw.getFeatures();
        if (!features.contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST) && !features.contains(KILDA_OVS_SWAP_FIELD)) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = buildMatch(sw.getSwitchId());
        match.add(FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build());
        Instructions instructions = buildInstructions(sw, SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT);

        return Collections.singletonList(FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new Cookie(SERVER_42_FLOW_RTT_VXLAN_TURNING_COOKIE))
                .table(OfTable.INPUT)
                .priority(SERVER_42_FLOW_RTT_VXLAN_TURNING_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build());
    }
}

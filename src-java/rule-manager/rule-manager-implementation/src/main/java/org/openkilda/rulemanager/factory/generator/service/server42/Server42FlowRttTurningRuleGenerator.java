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
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_TURNING_COOKIE;
import static org.openkilda.rulemanager.Constants.MAC_ADDRESS_SIZE_IN_BITS;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_FLOW_RTT_TURNING_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_PORT;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.SwapFieldAction;
import org.openkilda.rulemanager.action.SwapFieldAction.SwapFieldActionBuilder;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Server42FlowRttTurningRuleGenerator implements RuleGenerator {

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        Set<SwitchFeature> features = sw.getFeatures();
        if (!features.contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST) && !features.contains(KILDA_OVS_SWAP_FIELD)) {
            return Collections.emptyList();
        }

        Set<FieldMatch> match = buildMatch(sw.getSwitchId());
        Instructions instructions = buildInstructions(sw, SERVER_42_FLOW_RTT_REVERSE_UDP_PORT);

        return Collections.singletonList(FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new Cookie(SERVER_42_FLOW_RTT_TURNING_COOKIE))
                .table(OfTable.INPUT)
                .priority(SERVER_42_FLOW_RTT_TURNING_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build());
    }

    protected Set<FieldMatch> buildMatch(SwitchId switchId) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(switchId.getId()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build()
        );
    }

    protected Instructions buildInstructions(Switch sw, int setUpdSrcPort) {
        List<Action> actions = ImmutableList.of(
                SetFieldAction.builder().field(Field.UDP_SRC).value(setUpdSrcPort).build(),
                buildSwapAction(sw.getFeatures()),
                new PortOutAction(new PortNumber(SpecialPortType.IN_PORT)));
        return Instructions.builder()
                .applyActions(actions)
                .build();
    }

    private Action buildSwapAction(Set<SwitchFeature> switchFeatures) {
        SwapFieldActionBuilder builder = SwapFieldAction.builder()
                .numberOfBits(MAC_ADDRESS_SIZE_IN_BITS)
                .srcOffset(0)
                .dstOffset(0)
                .oxmSrcHeader(OpenFlowOxms.ETH_SRC)
                .oxmDstHeader(OpenFlowOxms.ETH_DST);
        if (switchFeatures.contains(NOVIFLOW_SWAP_ETH_SRC_ETH_DST)) {
            return builder.type(ActionType.NOVI_SWAP_FIELD)
                    .build();
        }
        return builder.type(ActionType.KILDA_SWAP_FIELD)
                .build();
    }
}

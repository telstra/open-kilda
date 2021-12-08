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

import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
import static org.openkilda.rulemanager.Constants.NOVIFLOW_TIMESTAMP_SIZE_IN_BITS;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_PORT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;

import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Server42FlowRttOutputVxlanRuleGenerator implements RuleGenerator {

    private final int server42Port;
    private final int server42Vlan;
    private final MacAddress server42MacAddress;

    @Builder
    public Server42FlowRttOutputVxlanRuleGenerator(int server42Port, int server42Vlan,
                                                   MacAddress server42MacAddress) {
        this.server42Port = server42Port;
        this.server42Vlan = server42Vlan;
        this.server42MacAddress = server42MacAddress;
    }

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        Set<SwitchFeature> features = sw.getFeatures();
        if (!features.contains(NOVIFLOW_PUSH_POP_VXLAN) && !features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            return Collections.emptyList();
        }

        List<Action> actions = new ArrayList<>();
        actions.add(buildPopVxlanAction(features));
        if (server42Vlan > 0) {
            actions.add(PushVlanAction.builder().vlanId((short) server42Vlan).build());
        }
        actions.add(SetFieldAction.builder().field(Field.ETH_SRC).value(sw.getSwitchId().toLong()).build());
        actions.add(SetFieldAction.builder().field(Field.ETH_DST).value(server42MacAddress.toLong()).build());

        actions.add(SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_REVERSE_UDP_PORT).build());

        if (sw.getFeatures().contains(NOVIFLOW_COPY_FIELD)) {
            // NOTE: We must call copy field action after all set field actions. All set actions called after copy field
            // actions will be ignored. It's Noviflow bug.
            actions.add(buildCopyTimestamp());
        }
        actions.add(new PortOutAction(new PortNumber(server42Port)));

        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        return Collections.singletonList(FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new Cookie(SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE))
                .table(OfTable.INPUT)
                .priority(SERVER_42_FLOW_RTT_OUTPUT_VXLAN_PRIORITY)
                .match(buildMatch(sw.getSwitchId()))
                .instructions(instructions)
                .build());
    }

    private static Set<FieldMatch> buildMatch(SwitchId switchId) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(switchId.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_REVERSE_UDP_VXLAN_PORT).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(VXLAN_UDP_DST).build()
        );
    }

    private static Action buildCopyTimestamp() {
        return CopyFieldAction.builder()
                .numberOfBits(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .srcOffset(0)
                .dstOffset(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .oxmSrcHeader(OpenFlowOxms.NOVIFLOW_TX_TIMESTAMP)
                .oxmDstHeader(OpenFlowOxms.NOVIFLOW_UDP_PAYLOAD_OFFSET)
                .build();
    }

    private static Action buildPopVxlanAction(Set<SwitchFeature> features) {
        if (features.contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            return new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW);
        } else {
            return new PopVxlanAction(ActionType.POP_VXLAN_OVS);
        }
    }
}

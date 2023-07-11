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

package org.openkilda.rulemanager.factory.generator.service.lldp;

import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.LLDP_POST_INGRESS_VXLAN_COOKIE;
import static org.openkilda.rulemanager.Constants.STUB_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class LldpPostIngressVxlanRuleGeneratorTest extends LldpRuleGeneratorTest {

    @BeforeEach
    public void setup() {
        config = prepareConfig();

        generator = LldpPostIngressVxlanRuleGenerator.builder()
                .config(config)
                .build();

        cookie = new Cookie(LLDP_POST_INGRESS_VXLAN_COOKIE);
        table = OfTable.POST_INGRESS;
        priority = Priority.LLDP_POST_INGRESS_VXLAN_PRIORITY;

        expectedFeatures = Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN, METERS, PKTPS_FLAG);
    }

    @Test
    public void shouldSkipRuleWithoutPopVxlanFeatureForOf13() {
        expectedFeatures.remove(NOVIFLOW_PUSH_POP_VXLAN);
        sw = buildSwitch("OF_13", expectedFeatures);
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertTrue(commands.isEmpty());
    }

    @Override
    protected void checkMatch(Set<FieldMatch> match) {
        Assertions.assertEquals(4, match.size());
        FieldMatch metadataMatch = getMatchByField(Field.METADATA, match);
        RoutingMetadata expectedMetadata = RoutingMetadata.builder().lldpFlag(true).build(sw.getFeatures());
        Assertions.assertEquals(expectedMetadata.getValue(), metadataMatch.getValue());
        Assertions.assertTrue(metadataMatch.isMasked());
        Assertions.assertEquals(expectedMetadata.getMask(), metadataMatch.getMask().longValue());

        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, match);
        Assertions.assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        Assertions.assertFalse(ipProtoMatch.isMasked());

        FieldMatch udpSrcMatch = getMatchByField(Field.UDP_SRC, match);
        Assertions.assertEquals(STUB_VXLAN_UDP_SRC, udpSrcMatch.getValue());
        Assertions.assertFalse(udpSrcMatch.isMasked());

        FieldMatch updDestMatch = getMatchByField(Field.UDP_DST, match);
        Assertions.assertEquals(VXLAN_UDP_DST, updDestMatch.getValue());
        Assertions.assertFalse(updDestMatch.isMasked());
    }

    @Override
    protected void checkInstructions(Instructions instructions, MeterId meterId) {
        Assertions.assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        Assertions.assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        Assertions.assertTrue(second instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) second;
        Assertions.assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());

        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertEquals(instructions.getGoToMeter(), meterId);
        Assertions.assertNull(instructions.getGoToTable());
    }

    @Override
    protected void checkInstructionsOf15(Instructions instructions, MeterId meterId) {
        Assertions.assertEquals(3, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        Assertions.assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        Assertions.assertTrue(second instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) second;
        Assertions.assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());

        Action third = instructions.getApplyActions().get(2);
        Assertions.assertTrue(third instanceof MeterAction);
        MeterAction meterAction = (MeterAction) third;
        Assertions.assertEquals(meterId, meterAction.getMeterId());

        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());
    }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class LldpPostIngressVxlanRuleGeneratorTest extends LldpRuleGeneratorTest {

    @Before
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
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertTrue(commands.isEmpty());
    }

    @Override
    protected void checkMatch(Set<FieldMatch> match) {
        assertEquals(4, match.size());
        FieldMatch metadataMatch = getMatchByField(Field.METADATA, match);
        RoutingMetadata expectedMetadata = RoutingMetadata.builder().lldpFlag(true).build(sw.getFeatures());
        assertEquals(expectedMetadata.getValue(), metadataMatch.getValue());
        assertTrue(metadataMatch.isMasked());
        assertEquals(expectedMetadata.getMask(), metadataMatch.getMask().longValue());

        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, match);
        assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        assertFalse(ipProtoMatch.isMasked());

        FieldMatch udpSrcMatch = getMatchByField(Field.UDP_SRC, match);
        assertEquals(STUB_VXLAN_UDP_SRC, udpSrcMatch.getValue());
        assertFalse(udpSrcMatch.isMasked());

        FieldMatch updDestMatch = getMatchByField(Field.UDP_DST, match);
        assertEquals(VXLAN_UDP_DST, updDestMatch.getValue());
        assertFalse(updDestMatch.isMasked());
    }

    @Override
    protected void checkInstructions(Instructions instructions, MeterId meterId) {
        assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        assertTrue(second instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) second;
        assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());

        assertNull(instructions.getWriteActions());
        assertEquals(instructions.getGoToMeter(), meterId);
        assertNull(instructions.getGoToTable());
    }

    @Override
    protected void checkInstructionsOf15(Instructions instructions, MeterId meterId) {
        assertEquals(3, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        assertTrue(second instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) second;
        assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());

        Action third = instructions.getApplyActions().get(2);
        assertTrue(third instanceof MeterAction);
        MeterAction meterAction = (MeterAction) third;
        assertEquals(meterId, meterAction.getMeterId());

        assertNull(instructions.getWriteActions());
        assertNull(instructions.getGoToMeter());
        assertNull(instructions.getGoToTable());
    }
}

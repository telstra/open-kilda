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

package org.openkilda.rulemanager.factory.generator.service.isl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.rulemanager.Constants.Priority.ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE;
import static org.openkilda.rulemanager.Constants.STUB_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_DST;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TransitIslVxlanRuleGeneratorTest {

    private static final int ISL_PORT = 8;

    private TransitIslVxlanRuleGenerator generator;

    @Before
    public void setup() {
        generator = TransitIslVxlanRuleGenerator.builder()
                .islPort(ISL_PORT)
                .build();
    }

    @Test
    public void shouldBuildCorrectRuleWithNoviflowVxlanFeature() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES, ISL_PORT),
                flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE, flowCommandData.getPriority());

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(4, match.size());
        checkMatch(match);

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(OfTable.TRANSIT, instructions.getGoToTable());
    }

    @Test
    public void shouldBuildCorrectRuleWithOpenKildaVxlanFeature() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VXLAN_TRANSIT_RULES, ISL_PORT),
                flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(ISL_TRANSIT_VXLAN_RULE_PRIORITY_MULTITABLE, flowCommandData.getPriority());

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(5, match.size());
        checkMatch(match);

        FieldMatch ethTypeMatch = getMatchByField(Field.ETH_TYPE, match);
        assertEquals(EthType.IPv4, ethTypeMatch.getValue());
        assertFalse(ethTypeMatch.isMasked());

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(OfTable.TRANSIT, instructions.getGoToTable());
    }

    @Test
    public void shouldSkipRuleWhenNoVxlanFeatures() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertTrue(commands.isEmpty());
    }

    private void checkMatch(Set<FieldMatch> match) {
        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, match);
        assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        assertFalse(ipProtoMatch.isMasked());

        FieldMatch inPortMatch = getMatchByField(Field.IN_PORT, match);
        assertEquals(ISL_PORT, inPortMatch.getValue());
        assertFalse(inPortMatch.isMasked());

        FieldMatch udpSrcMatch = getMatchByField(Field.UDP_SRC, match);
        assertEquals(STUB_VXLAN_UDP_SRC, udpSrcMatch.getValue());
        assertFalse(udpSrcMatch.isMasked());

        FieldMatch udpDstMatch = getMatchByField(Field.UDP_DST, match);
        assertEquals(VXLAN_UDP_DST, udpDstMatch.getValue());
        assertFalse(udpDstMatch.isMasked());
    }
}

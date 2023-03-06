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
import static org.openkilda.rulemanager.Constants.Priority.ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class EgressIslVlanRuleGeneratorTest {

    private static final int ISL_PORT = 8;

    private EgressIslVlanRuleGenerator generator;

    @Before
    public void setup() {
        generator = EgressIslVlanRuleGenerator.builder()
                .islPort(ISL_PORT)
                .build();
    }

    @Test
    public void shouldBuildCorrectRule() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new PortColourCookie(CookieType.MULTI_TABLE_ISL_VLAN_EGRESS_RULES, ISL_PORT),
                flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(ISL_EGRESS_VLAN_RULE_PRIORITY_MULTITABLE, flowCommandData.getPriority());

        assertEquals(1, flowCommandData.getMatch().size());
        FieldMatch inPortMatch = getMatchByField(Field.IN_PORT, flowCommandData.getMatch());
        assertEquals(ISL_PORT, inPortMatch.getValue());
        assertFalse(inPortMatch.isMasked());

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(OfTable.EGRESS, instructions.getGoToTable());
    }
}

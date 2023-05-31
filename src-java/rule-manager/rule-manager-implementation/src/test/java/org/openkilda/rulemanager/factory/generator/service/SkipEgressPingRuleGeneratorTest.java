/* Copyright 2023 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.cookie.Cookie.SKIP_EGRESS_FLOW_PING_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.SKIP_EGRESS_FLOW_PING_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.generator.flow.haflow.SkipEgressPingRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class SkipEgressPingRuleGeneratorTest {

    private static final String FLOW_PING_MAGIC_SRC_MAC_ADDRESS = "00:26:E1:FF:FF:FE";

    private SkipEgressPingRuleGenerator generator;

    @Before
    public void setup() {
        generator = SkipEgressPingRuleGenerator.builder()
                .flowPingMagicSrcMacAddress(FLOW_PING_MAGIC_SRC_MAC_ADDRESS)
                .build();
    }

    @Test
    public void generateRuleForOf13Ok() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new Cookie(SKIP_EGRESS_FLOW_PING_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.EGRESS, flowCommandData.getTable());
        assertEquals(SKIP_EGRESS_FLOW_PING_PRIORITY, flowCommandData.getPriority());

        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, flowCommandData.getMatch());
        assertEquals(new SwitchId(FLOW_PING_MAGIC_SRC_MAC_ADDRESS).toLong(), ethSrcMatch.getValue());
        assertFalse(ethSrcMatch.isMasked());

        assertEquals(flowCommandData.getInstructions().getGoToTable(), OfTable.TRANSIT);

        assertNull(flowCommandData.getInstructions().getGoToMeter());
        assertNull(flowCommandData.getInstructions().getApplyActions());
        assertNull(flowCommandData.getInstructions().getWriteMetadata());
        assertNull(flowCommandData.getInstructions().getWriteActions());
    }

    @Test
    public void generateRuleForOf12ReturnNull() {
        Switch sw = buildSwitch("OF_12", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);
        assertNull(commands);
    }
}

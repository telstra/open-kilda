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

package org.openkilda.rulemanager.factory.generator.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.cookie.Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.DROP_DISCOVERY_LOOP_RULE_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class DropDiscoveryLoopRuleGeneratorTest {

    private RuleManagerConfig config;

    private DropDiscoveryLoopRuleGenerator generator;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");

        generator = DropDiscoveryLoopRuleGenerator.builder()
                .config(config)
                .build();
    }

    @Test
    public void shouldBuildCorrectRuleForOf13() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new Cookie(DROP_VERIFICATION_LOOP_RULE_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(DROP_DISCOVERY_LOOP_RULE_PRIORITY, flowCommandData.getPriority());

        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, flowCommandData.getMatch());
        assertEquals(new SwitchId(config.getDiscoveryBcastPacketDst()).toLong(), ethDstMatch.getValue());
        assertFalse(ethDstMatch.isMasked());

        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, flowCommandData.getMatch());
        assertEquals(sw.getSwitchId().toLong(), ethSrcMatch.getValue());
        assertFalse(ethSrcMatch.isMasked());

        assertNull(flowCommandData.getInstructions());
    }

    @Test
    public void shouldSkipRuleForOf12() {
        Switch sw = buildSwitch("OF_12", Collections.emptySet());
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertTrue(commands.isEmpty());
    }
}

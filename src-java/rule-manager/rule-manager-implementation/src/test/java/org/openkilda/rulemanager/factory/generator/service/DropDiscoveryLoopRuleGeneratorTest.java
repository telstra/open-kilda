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
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class DropDiscoveryLoopRuleGeneratorTest {

    private RuleManagerConfig config;

    private DropDiscoveryLoopRuleGenerator generator;

    @BeforeEach
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
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        Assertions.assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        Assertions.assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        Assertions.assertEquals(new Cookie(DROP_VERIFICATION_LOOP_RULE_COOKIE), flowCommandData.getCookie());
        Assertions.assertEquals(OfTable.INPUT, flowCommandData.getTable());
        Assertions.assertEquals(DROP_DISCOVERY_LOOP_RULE_PRIORITY, flowCommandData.getPriority());

        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, flowCommandData.getMatch());
        Assertions.assertEquals(new SwitchId(config.getDiscoveryBcastPacketDst()).toLong(), ethDstMatch.getValue());
        Assertions.assertFalse(ethDstMatch.isMasked());

        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, flowCommandData.getMatch());
        Assertions.assertEquals(sw.getSwitchId().toLong(), ethSrcMatch.getValue());
        Assertions.assertFalse(ethSrcMatch.isMasked());

        Assertions.assertNull(flowCommandData.getInstructions().getApplyActions());
        Assertions.assertNull(flowCommandData.getInstructions().getGoToMeter());
        Assertions.assertNull(flowCommandData.getInstructions().getGoToTable());
        Assertions.assertNull(flowCommandData.getInstructions().getWriteMetadata());
        Assertions.assertNull(flowCommandData.getInstructions().getWriteActions());
    }

    @Test
    public void shouldSkipRuleForOf12() {
        Switch sw = buildSwitch("OF_12", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertTrue(commands.isEmpty());
    }
}

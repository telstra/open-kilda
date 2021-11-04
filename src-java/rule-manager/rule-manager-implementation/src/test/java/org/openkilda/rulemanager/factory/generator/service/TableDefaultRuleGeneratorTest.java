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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.MINIMAL_POSITIVE_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.SpeakerCommandData;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class TableDefaultRuleGeneratorTest {

    @Test
    public void shouldBuildCorrectRuleForOf13() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        TableDefaultRuleGenerator generator = TableDefaultRuleGenerator.builder()
                .cookie(new Cookie(DROP_RULE_COOKIE))
                .ofTable(OfTable.INPUT)
                .build();
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new Cookie(DROP_RULE_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(MINIMAL_POSITIVE_PRIORITY, flowCommandData.getPriority());

        assertNull(flowCommandData.getMatch());
        assertNull(flowCommandData.getInstructions());
    }
}

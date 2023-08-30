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

package org.openkilda.rulemanager.factory.generator.service.isl;

import static org.openkilda.rulemanager.Constants.Priority.PING_INPUT_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class InputPingRuleGeneratorTest {

    private RuleGenerator generator;
    private static final int ISL_PORT = 1;
    private static final String FLOW_PING_MAGIC_SRC_MAC_ADDRESS = "00:00:00:00:00:01";

    @BeforeEach
    public void setup() {
        generator = InputPingRuleGenerator.builder()
                .islPort(ISL_PORT)
                .flowPingMagicSrcMacAddress(FLOW_PING_MAGIC_SRC_MAC_ADDRESS)
                .build();
    }

    @Test
    public void generateRule() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        Assertions.assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        Assertions.assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        Assertions.assertEquals(new PortColourCookie(CookieType.PING_INPUT, ISL_PORT), flowCommandData.getCookie());
        Assertions.assertEquals(OfTable.INPUT, flowCommandData.getTable());
        Assertions.assertEquals(PING_INPUT_PRIORITY, flowCommandData.getPriority());

        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, flowCommandData.getMatch());
        Assertions.assertEquals(new SwitchId(FLOW_PING_MAGIC_SRC_MAC_ADDRESS).toLong(), ethSrcMatch.getValue());
        Assertions.assertFalse(ethSrcMatch.isMasked());

        FieldMatch inPortMatch = getMatchByField(Field.IN_PORT, flowCommandData.getMatch());
        Assertions.assertEquals(ISL_PORT, inPortMatch.getValue());
        Assertions.assertFalse(inPortMatch.isMasked());

        Assertions.assertEquals(flowCommandData.getInstructions().getGoToTable(), OfTable.TRANSIT);

        Assertions.assertNull(flowCommandData.getInstructions().getWriteMetadata());
        Assertions.assertNull(flowCommandData.getInstructions().getGoToMeter());
        Assertions.assertNull(flowCommandData.getInstructions().getApplyActions());
        Assertions.assertNull(flowCommandData.getInstructions().getWriteActions());
    }

}

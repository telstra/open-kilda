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
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.BFD;
import static org.openkilda.model.cookie.Cookie.CATCH_BFD_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.CATCH_BFD_RULE_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class BfdCatchRuleGeneratorTest {

    @Test
    public void shouldBuildCorrectRuleForOf13() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(BFD));
        BfdCatchRuleGenerator generator = new BfdCatchRuleGenerator();
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new Cookie(CATCH_BFD_RULE_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(CATCH_BFD_RULE_PRIORITY, flowCommandData.getPriority());

        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, flowCommandData.getMatch());
        assertEquals(sw.getSwitchId().toLong(), ethDstMatch.getValue());
        assertFalse(ethDstMatch.isMasked());

        FieldMatch ethTypeMatch = getMatchByField(Field.ETH_TYPE, flowCommandData.getMatch());
        assertEquals(EthType.IPv4, ethTypeMatch.getValue());
        assertFalse(ethTypeMatch.isMasked());

        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, flowCommandData.getMatch());
        assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        assertFalse(ipProtoMatch.isMasked());

        FieldMatch udpDstMatch = getMatchByField(Field.UDP_DST, flowCommandData.getMatch());
        assertEquals(Constants.BDF_DEFAULT_PORT, udpDstMatch.getValue());
        assertFalse(udpDstMatch.isMasked());

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(1, instructions.getApplyActions().size());
        Action action = instructions.getApplyActions().get(0);
        assertTrue(action instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) action;
        assertEquals(SpecialPortType.LOCAL, portOutAction.getPortNumber().getPortType());
    }

    @Test
    public void shouldSkipRuleWhenNoBfdFeatureForOf13() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        BfdCatchRuleGenerator generator = new BfdCatchRuleGenerator();
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertTrue(commands.isEmpty());
    }
}

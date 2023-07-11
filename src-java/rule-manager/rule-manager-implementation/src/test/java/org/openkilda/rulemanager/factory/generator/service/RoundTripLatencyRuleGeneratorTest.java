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
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.cookie.Cookie.ROUND_TRIP_LATENCY_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.ROUND_TRIP_LATENCY_RULE_PRIORITY;
import static org.openkilda.rulemanager.Constants.ROUND_TRIP_LATENCY_T1_OFFSET;
import static org.openkilda.rulemanager.Constants.ROUND_TRIP_LATENCY_TIMESTAMP_SIZE;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;
import static org.openkilda.rulemanager.action.noviflow.OpenFlowOxms.NOVIFLOW_PACKET_OFFSET;
import static org.openkilda.rulemanager.action.noviflow.OpenFlowOxms.NOVIFLOW_RX_TIMESTAMP;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.factory.generator.service.noviflow.RoundTripLatencyRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class RoundTripLatencyRuleGeneratorTest {

    private RuleManagerConfig config;

    @BeforeEach
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");
    }

    @Test
    public void shouldBuildCorrectRuleForOf13() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(NOVIFLOW_COPY_FIELD));
        RoundTripLatencyRuleGenerator generator = RoundTripLatencyRuleGenerator.builder()
                .config(config)
                .build();
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        Assertions.assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        Assertions.assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        Assertions.assertEquals(new Cookie(ROUND_TRIP_LATENCY_RULE_COOKIE), flowCommandData.getCookie());
        Assertions.assertEquals(OfTable.INPUT, flowCommandData.getTable());
        Assertions.assertEquals(ROUND_TRIP_LATENCY_RULE_PRIORITY, flowCommandData.getPriority());

        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, flowCommandData.getMatch());
        Assertions.assertEquals(sw.getSwitchId().toLong(), ethSrcMatch.getValue());
        Assertions.assertFalse(ethSrcMatch.isMasked());

        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, flowCommandData.getMatch());
        Assertions.assertEquals(new SwitchId(config.getDiscoveryBcastPacketDst()).toLong(), ethDstMatch.getValue());
        Assertions.assertFalse(ethDstMatch.isMasked());

        FieldMatch ethTypeMatch = getMatchByField(Field.ETH_TYPE, flowCommandData.getMatch());
        Assertions.assertEquals(EthType.IPv4, ethTypeMatch.getValue());
        Assertions.assertFalse(ethTypeMatch.isMasked());

        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, flowCommandData.getMatch());
        Assertions.assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        Assertions.assertFalse(ipProtoMatch.isMasked());

        FieldMatch udpDstMatch = getMatchByField(Field.UDP_DST, flowCommandData.getMatch());
        Assertions.assertEquals(Constants.LATENCY_PACKET_UDP_PORT, udpDstMatch.getValue());
        Assertions.assertFalse(udpDstMatch.isMasked());

        Instructions instructions = flowCommandData.getInstructions();
        Assertions.assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof CopyFieldAction);
        CopyFieldAction copyFieldAction = (CopyFieldAction) first;
        Assertions.assertEquals(ROUND_TRIP_LATENCY_TIMESTAMP_SIZE, copyFieldAction.getNumberOfBits());
        Assertions.assertEquals(0, copyFieldAction.getSrcOffset());
        Assertions.assertEquals(ROUND_TRIP_LATENCY_T1_OFFSET, copyFieldAction.getDstOffset());
        Assertions.assertEquals(NOVIFLOW_RX_TIMESTAMP, copyFieldAction.getOxmSrcHeader());
        Assertions.assertEquals(NOVIFLOW_PACKET_OFFSET, copyFieldAction.getOxmDstHeader());

        Action second = instructions.getApplyActions().get(1);
        Assertions.assertTrue(second instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) second;
        Assertions.assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());
    }

    @Test
    public void shouldSkipRuleWhenNoCopyFieldFeatureForOf13() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        BfdCatchRuleGenerator generator = new BfdCatchRuleGenerator();
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertTrue(commands.isEmpty());
    }
}

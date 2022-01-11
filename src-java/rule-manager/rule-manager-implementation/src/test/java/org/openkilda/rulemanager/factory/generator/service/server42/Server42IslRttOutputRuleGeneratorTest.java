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

package org.openkilda.rulemanager.factory.generator.service.server42;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_ISL_RTT_OUTPUT_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_ISL_RTT_REVERSE_UDP_PORT;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.Utils;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class Server42IslRttOutputRuleGeneratorTest {
    public static final MacAddress MAC_ADDRESS = new MacAddress("12:34:56:78:9A:BC");

    private Server42IslRttOutputRuleGenerator generator;

    @Before
    public void setup() {
        RuleManagerConfig config = mock(RuleManagerConfig.class);
        when(config.getServer42IslRttMagicMacAddress()).thenReturn(MAC_ADDRESS.toString());

        generator = Server42IslRttOutputRuleGenerator.builder()
                .server42Port(Utils.SERVER_42_PORT)
                .server42Vlan(Utils.SERVER_42_VLAN)
                .server42MacAddress(Utils.SERVER_42_MAC_ADDRESS)
                .config(config)
                .build();
    }

    @Test
    public void server42IslRttOutputRuleGeneratorTest() {
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(NOVIFLOW_COPY_FIELD));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new Cookie(SERVER_42_ISL_RTT_OUTPUT_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(SERVER_42_ISL_RTT_OUTPUT_PRIORITY, flowCommandData.getPriority());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.ETH_DST).value(MAC_ADDRESS.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(SERVER_42_ISL_RTT_REVERSE_UDP_PORT).build());
        assertEquals(expectedMatch, flowCommandData.getMatch());

        List<Action> expectedApplyActions = Lists.newArrayList(
                PushVlanAction.builder().vlanId((short) Utils.SERVER_42_VLAN).build(),
                SetFieldAction.builder().field(Field.ETH_SRC).value(sw.getSwitchId().toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(Utils.SERVER_42_MAC_ADDRESS.toLong()).build(),
                new PortOutAction(new PortNumber(Utils.SERVER_42_PORT)));

        Instructions expectedInstructions = Instructions.builder().applyActions(expectedApplyActions).build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
    }
}

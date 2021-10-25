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

package org.openkilda.rulemanager.factory.generator.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InputLldpRuleGeneratorTest {
    public static final int PORT_NUMBER_1 = 1;
    public static final Switch SW = buildSwitch("OF_13", Sets.newHashSet(SwitchFeature.RESET_COUNTS_FLAG));

    @Test
    public void buildCorrectRuleForOf13Test() {
        FlowEndpoint endpoint = new FlowEndpoint(SW.getSwitchId(), PORT_NUMBER_1, 0, 0, true, false);

        FlowSideAdapter overlapAdapter = new FlowSourceAdapter(Flow.builder()
                .flowId("some")
                .srcSwitch(SW)
                .destSwitch(buildSwitch("OF_13", Collections.emptySet()))
                .detectConnectedDevices(DetectConnectedDevices.builder().srcLldp(false).srcSwitchLldp(false).build())
                .build());

        InputLldpRuleGenerator generator = InputLldpRuleGenerator.builder()
                .ingressEndpoint(endpoint)
                .multiTable(true)
                .overlappingIngressAdapters(Sets.newHashSet(overlapAdapter))
                .build();

        List<SpeakerCommandData> commands = generator.generateCommands(SW);

        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(SW.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(SW.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(new PortColourCookie(CookieType.LLDP_INPUT_CUSTOMER_TYPE, PORT_NUMBER_1),
                flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(Priority.LLDP_INPUT_CUSTOMER_PRIORITY, flowCommandData.getPriority());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.LLDP).build());
        assertEquals(expectedMatch, flowCommandData.getMatch());

        RoutingMetadata metadata = RoutingMetadata.builder().lldpFlag(true).build();
        Instructions expectedInstructions = Instructions.builder()
                .writeMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()))
                .goToTable(OfTable.PRE_INGRESS)
                .build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), flowCommandData.getFlags());
    }

    @Test
    public void buildLldpRuleForSingleTableTest() {
        FlowEndpoint endpoint = new FlowEndpoint(SW.getSwitchId(), PORT_NUMBER_1, 0, 0, true, false);
        InputLldpRuleGenerator generator = InputLldpRuleGenerator.builder()
                .ingressEndpoint(endpoint)
                .multiTable(false)
                .overlappingIngressAdapters(new HashSet<>())
                .build();

        assertEquals(0, generator.generateCommands(SW).size());
    }

    @Test
    public void buildLldpRuleWithoutLldpTest() {
        FlowEndpoint endpoint = new FlowEndpoint(SW.getSwitchId(), PORT_NUMBER_1, 0, 0, false, false);
        InputLldpRuleGenerator generator = InputLldpRuleGenerator.builder()
                .ingressEndpoint(endpoint)
                .multiTable(true)
                .overlappingIngressAdapters(new HashSet<>())
                .build();

        assertEquals(0, generator.generateCommands(SW).size());
    }

    @Test
    public void buildLldpRuleWithOverlappedEndpointsTest() {
        FlowEndpoint endpoint = new FlowEndpoint(SW.getSwitchId(), PORT_NUMBER_1, 0, 0, true, false);
        FlowSideAdapter adapter = new FlowSourceAdapter(Flow.builder()
                .flowId("some")
                .srcSwitch(SW)
                .destSwitch(buildSwitch("OF_13", Collections.emptySet()))
                .detectConnectedDevices(DetectConnectedDevices.builder().srcLldp(true).srcSwitchLldp(true).build())
                .build());
        InputLldpRuleGenerator generator = InputLldpRuleGenerator.builder()
                .ingressEndpoint(endpoint)
                .multiTable(true)
                .overlappingIngressAdapters(Sets.newHashSet(adapter))
                .build();

        assertEquals(0, generator.generateCommands(SW).size());
    }
}

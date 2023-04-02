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

package org.openkilda.rulemanager;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.LAG_PORTS;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.buildSwitchProperties;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchProperties.RttState;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.adapter.InMemoryDataAdapter;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BfdCatchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.BroadCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.DropDiscoveryLoopRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TableDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.TablePassThroughDefaultRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UniCastDiscoveryRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.UnicastVerificationVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpInputPreDropRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressOneSwitchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpPostIngressVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.arp.ArpTransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lacp.DropSlowProtocolsLoopRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lacp.LacpReplyRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpInputPreDropRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressOneSwitchRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpPostIngressVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.lldp.LldpTransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.noviflow.RoundTripLatencyRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttOutputVlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttOutputVxlanRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttTurningRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42FlowRttVxlanTurningRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42IslRttInputRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42IslRttOutputRuleGenerator;
import org.openkilda.rulemanager.factory.generator.service.server42.Server42IslRttTurningRuleGenerator;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RuleManagerServiceRulesTest {

    private RuleManagerImpl ruleManager;
    public static final int ISL_PORT = 1;

    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final MeterId METER_ID = new MeterId(17);
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Set<SwitchFeature> FEATURES = Sets.newHashSet(
            RESET_COUNTS_FLAG, METERS, NOVIFLOW_PUSH_POP_VXLAN);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, FEATURES);
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);

    public static final int TRANSIT_VLAN_ID = 14;
    public static final int BANDWIDTH = 1000;
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);

    public static final FlowSegmentCookie FORWARD_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);
    public static final FlowSegmentCookie REVERSE_COOKIE = new FlowSegmentCookie(FlowPathDirection.REVERSE, 123);

    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(FORWARD_COOKIE)
            .meterId(METER_ID)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_2)
            .srcWithMultiTable(true)
            .bandwidth(BANDWIDTH)
            .segments(newArrayList(PathSegment.builder()
                    .pathId(PATH_ID)
                    .srcPort(PORT_NUMBER_2)
                    .srcSwitch(SWITCH_1)
                    .destPort(PORT_NUMBER_3)
                    .destSwitch(SWITCH_2)
                    .srcWithMultiTable(true)
                    .build()))
            .build();

    @Before
    public void setup() {
        RuleManagerConfig config = mock(RuleManagerConfig.class);
        when(config.getBroadcastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getFlowPingMagicSrcMacAddress()).thenReturn("00:26:E1:FF:FF:FE");
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");

        ruleManager = new RuleManagerImpl(config);
    }

    @Test
    public void buildSharedEndpointYFlowCommands() {
        DataAdapter adapter = buildYFlowAdapter(new MeterId(17));
        List<FlowPath> flowPaths = new ArrayList<>();
        flowPaths.add(PATH);
        List<SpeakerData> speakerData = ruleManager.buildRulesForYFlow(flowPaths, adapter);

        assertEquals(2, speakerData.size());
    }

    @Test
    public void buildSharedEndpointYFlowCommandsNullMeterId() {
        DataAdapter adapter = buildYFlowAdapter(null);
        List<FlowPath> flowPaths = new ArrayList<>();
        flowPaths.add(PATH);

        List<SpeakerData> speakerData = ruleManager.buildRulesForYFlow(flowPaths, adapter);
        assertEquals(1, speakerData.size());
    }

    @Test
    public void buildYPointYFlowCommands() {
        DataAdapter adapter = buildYFlowAdapter(new MeterId(17));
        FlowPath path = FlowPath.builder()
                .pathId(PATH_ID)
                .cookie(REVERSE_COOKIE)
                .meterId(METER_ID)
                .srcSwitch(SWITCH_2)
                .destSwitch(SWITCH_1)
                .srcWithMultiTable(true)
                .bandwidth(BANDWIDTH)
                .segments(newArrayList(PathSegment.builder()
                        .pathId(PATH_ID)
                        .srcPort(PORT_NUMBER_2)
                        .srcSwitch(SWITCH_1)
                        .destPort(PORT_NUMBER_3)
                        .destSwitch(SWITCH_2)
                        .srcWithMultiTable(true)
                        .build()))
                .build();

        List<FlowPath> flowPaths = new ArrayList<>();
        flowPaths.add(path);

        List<SpeakerData> speakerData = ruleManager.buildRulesForYFlow(flowPaths, adapter);
        assertEquals(2, speakerData.size());
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInSingleTableMode() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchId switchId = sw.getSwitchId();
        SwitchProperties switchProperties = buildSwitchProperties(sw, false);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(
                switchId, buildAdapter(switchId, switchProperties, new HashSet<>(), false, LAG_PORTS));

        assertEquals(10, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof TableDefaultRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropDiscoveryLoopRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BfdCatchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof RoundTripLatencyRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UnicastVerificationVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropSlowProtocolsLoopRuleGenerator));
        assertEquals(2, generators.stream().filter(g -> g instanceof LacpReplyRuleGenerator).count());
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInMultiTableMode() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchId switchId = sw.getSwitchId();
        SwitchProperties switchProperties = buildSwitchProperties(sw, true);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(
                switchId, buildAdapter(switchId, switchProperties, new HashSet<>(), false, LAG_PORTS));

        assertEquals(21, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof DropDiscoveryLoopRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof BfdCatchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof RoundTripLatencyRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UnicastVerificationVxlanRuleGenerator));

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof TablePassThroughDefaultRuleGenerator).count());
        assertEquals(1, generators.stream().filter(g -> g instanceof DropSlowProtocolsLoopRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof LacpReplyRuleGenerator).count());

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressOneSwitchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressOneSwitchRuleGenerator));
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInMultiTableModeWithSwitchArpAndLldp() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchId switchId = sw.getSwitchId();
        SwitchProperties switchProperties = buildSwitchProperties(sw, true, true, true);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(
                switchId, buildAdapter(switchId, switchProperties, new HashSet<>(), false, null));

        assertEquals(24, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof TablePassThroughDefaultRuleGenerator).count());

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressOneSwitchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressOneSwitchRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpTransitRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpInputPreDropRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpIngressRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpTransitRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpInputPreDropRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpIngressRuleGenerator));
    }

    @Test
    public void shouldUseCorrectServiceRuleGeneratorsForSwitchInMultiTableModeWithAllRules() {
        Switch sw = buildSwitch("OF_13", Collections.emptySet());
        SwitchId switchId = sw.getSwitchId();
        SwitchProperties switchProperties = buildSwitchProperties(sw, true, true, true, true, RttState.ENABLED);

        List<RuleGenerator> generators = ruleManager.getServiceRuleGenerators(
                switchId, buildAdapter(switchId, switchProperties, Sets.newHashSet(ISL_PORT), true, LAG_PORTS));

        assertEquals(37, generators.size());
        assertTrue(generators.stream().anyMatch(g -> g instanceof BroadCastDiscoveryRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof UniCastDiscoveryRuleGenerator));

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof TablePassThroughDefaultRuleGenerator).count());

        assertEquals(4, generators.stream().filter(g -> g instanceof TableDefaultRuleGenerator).count());
        assertEquals(2, generators.stream().filter(g -> g instanceof TablePassThroughDefaultRuleGenerator).count());

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpPostIngressOneSwitchRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressVxlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpPostIngressOneSwitchRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpTransitRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpInputPreDropRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof LldpIngressRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpTransitRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpInputPreDropRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof ArpIngressRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42FlowRttTurningRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42FlowRttVxlanTurningRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42FlowRttOutputVlanRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42FlowRttOutputVxlanRuleGenerator));

        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42IslRttInputRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42IslRttTurningRuleGenerator));
        assertTrue(generators.stream().anyMatch(g -> g instanceof Server42IslRttOutputRuleGenerator));
    }

    private DataAdapter buildAdapter(
            SwitchId switchId, SwitchProperties switchProperties, Set<Integer> islPorts, boolean server42,
            List<LagLogicalPort> lagLogicalPorts) {
        Map<SwitchId, SwitchProperties> switchPropertiesMap = new HashMap<>();
        switchPropertiesMap.put(switchId, switchProperties);
        Map<SwitchId, Set<Integer>> islMap = new HashMap<>();
        islMap.putIfAbsent(switchId, islPorts);
        Map<SwitchId, List<LagLogicalPort>> lagMap = new HashMap<>();
        if (lagLogicalPorts != null) {
            lagMap.put(switchId, lagLogicalPorts);
        }
        return InMemoryDataAdapter.builder()
                .switchProperties(switchPropertiesMap)
                .switchIslPorts(islMap)
                .featureToggles(KildaFeatureToggles.builder()
                        .server42FlowRtt(server42)
                        .server42IslRtt(server42)
                        .build())
                .switchLagPorts(lagMap)
                .build();
    }

    private DataAdapter buildYFlowAdapter(MeterId meterId) {
        SwitchProperties switchProperties = buildSwitchProperties(SWITCH_1, false);

        Map<SwitchId, SwitchProperties> switchPropertiesMap = new HashMap<>();
        switchPropertiesMap.put(SWITCH_ID_1, switchProperties);
        Map<SwitchId, Set<Integer>> islMap = new HashMap<>();
        islMap.putIfAbsent(SWITCH_ID_1, new HashSet<>());
        Map<SwitchId, List<LagLogicalPort>> lagMap = new HashMap<>();
        lagMap.put(SWITCH_ID_1, LAG_PORTS);

        YFlow yFlow = YFlow.builder()
                .yFlowId("yFlowId")
                .sharedEndpoint(new SharedEndpoint(SWITCH_ID_1, 1))
                .yPoint(SWITCH_ID_2)
                .protectedPathYPoint(SWITCH_ID_2)
                .sharedEndpointMeterId(meterId)
                .meterId(meterId)
                .build();
        Map<PathId, YFlow> yFlows = new HashMap<>();
        yFlows.put(PATH_ID, yFlow);

        Map<SwitchId, Switch> switches = new HashMap<>();
        switches.put(SWITCH_ID_1, SWITCH_1);
        switches.put(SWITCH_ID_2, SWITCH_2);

        Flow flow = Flow.builder()
                .flowId("flow")
                .srcSwitch(buildSwitch(SWITCH_ID_1, Collections.emptySet()))
                .destSwitch(buildSwitch(SWITCH_ID_2, Collections.emptySet()))
                .build();
        flow.setForwardPathId(PATH_ID);
        flow.setReversePathId(PATH_ID);

        Map<PathId, Flow> flows = new HashMap<>();
        flows.put(PATH_ID, flow);

        Map<PathId, FlowTransitEncapsulation> transitEncapsulationMap = new HashMap<>();
        transitEncapsulationMap.put(PATH_ID, VLAN_ENCAPSULATION);

        return InMemoryDataAdapter.builder()
                .switchProperties(switchPropertiesMap)
                .switches(switches)
                .transitEncapsulations(transitEncapsulationMap)
                .flows(flows)
                .switchIslPorts(islMap)
                .switchLagPorts(lagMap)
                .yFlows(yFlows)
                .build();
    }
}

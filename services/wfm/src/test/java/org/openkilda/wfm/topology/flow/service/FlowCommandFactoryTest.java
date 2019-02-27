/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.service;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.UnidirectionalFlow;

import com.google.common.collect.ImmutableList;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class FlowCommandFactoryTest {
    private static final String TEST_FLOW = "test-flow";
    private static final long TEST_COOKIE = Cookie.FORWARD_FLOW_COOKIE_MASK | 1;
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");

    @Test
    public void shouldCreateInstallRulesFor1SegmentPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_2).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(destSwitch)
                .destPort(21)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<InstallTransitFlow> rules = factory.createInstallTransitAndEgressRulesForFlow(flow,
                Collections.singletonList(segment1to2));
        assertThat(rules, hasSize(1));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getInputPort());
        assertEquals(2, (int) rules.get(0).getOutputPort());
        assertEquals(301, (int) rules.get(0).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(0)).getOutputVlanId());

        BaseInstallFlow ingressRule = factory.createInstallIngressRulesForFlow(flow,
                Collections.singletonList(segment1to2));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getInputPort());
        assertEquals(11, (int) ingressRule.getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) ingressRule).getInputVlanId());
    }

    @Test
    public void shouldCreateRemoveRulesFor1SegmentPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_2).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(destSwitch)
                .destPort(21)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveTransitAndEgressRulesForFlow(flow,
                Collections.singletonList(segment1to2));
        assertThat(rules, hasSize(1));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(0).getCriteria().getInVlan());

        RemoveFlow ingressRule = factory.createRemoveIngressRulesForFlow(flow, Collections.singletonList(segment1to2));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getCriteria().getInPort());
        assertEquals(11, (int) ingressRule.getCriteria().getOutPort());
        assertEquals(101, (int) ingressRule.getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateInstallRulesFor2SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();
        PathSegment segment2to3 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2, segment2to3));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<InstallTransitFlow> rules = factory.createInstallTransitAndEgressRulesForFlow(flow,
                asList(segment1to2, segment2to3));
        assertThat(rules, hasSize(2));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getInputPort());
        assertEquals(22, (int) rules.get(0).getOutputPort());
        assertEquals(301, (int) rules.get(0).getTransitVlanId());

        assertEquals(SWITCH_ID_3, rules.get(1).getSwitchId());
        assertEquals(31, (int) rules.get(1).getInputPort());
        assertEquals(2, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) rules.get(1).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(1)).getOutputVlanId());

        BaseInstallFlow ingressRule = factory.createInstallIngressRulesForFlow(flow,
                asList(segment1to2, segment2to3));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getInputPort());
        assertEquals(11, (int) ingressRule.getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) ingressRule).getInputVlanId());
    }

    @Test
    public void shouldCreateInstallRulesFor3SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch switch3 = Switch.builder().switchId(SWITCH_ID_3).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_4).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();
        PathSegment segment2to3 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(switch3)
                .destPort(31)
                .build();
        PathSegment segment3to4 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch3)
                .srcPort(32)
                .destSwitch(destSwitch)
                .destPort(41)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2, segment2to3, segment3to4));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<InstallTransitFlow> rules = factory.createInstallTransitAndEgressRulesForFlow(flow,
                asList(segment1to2, segment2to3, segment3to4));
        assertThat(rules, hasSize(3));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getInputPort());
        assertEquals(22, (int) rules.get(0).getOutputPort());
        assertEquals(301, (int) rules.get(0).getTransitVlanId());

        assertEquals(SWITCH_ID_3, rules.get(1).getSwitchId());
        assertEquals(31, (int) rules.get(1).getInputPort());
        assertEquals(32, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) rules.get(1).getTransitVlanId());

        assertEquals(SWITCH_ID_4, rules.get(2).getSwitchId());
        assertEquals(41, (int) rules.get(2).getInputPort());
        assertEquals(2, (int) rules.get(2).getOutputPort());
        assertEquals(301, (int) rules.get(2).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(2)).getOutputVlanId());

        BaseInstallFlow ingressRule = factory.createInstallIngressRulesForFlow(flow,
                asList(segment1to2, segment2to3, segment3to4));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getInputPort());
        assertEquals(11, (int) ingressRule.getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) ingressRule).getInputVlanId());
    }

    @Ignore("The order of segments is handled in DAO")
    @Test
    public void shouldCreateInstallRulesFor2SegmentsPathWithWrongOrderOfSegments() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();
        PathSegment segment2to3 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2, segment2to3));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<InstallTransitFlow> rules = factory.createInstallTransitAndEgressRulesForFlow(flow,
                asList(segment2to3, segment1to2));
        assertThat(rules, hasSize(2));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getInputPort());
        assertEquals(22, (int) rules.get(0).getOutputPort());
        assertEquals(301, (int) rules.get(0).getTransitVlanId());

        assertEquals(SWITCH_ID_3, rules.get(1).getSwitchId());
        assertEquals(31, (int) rules.get(1).getInputPort());
        assertEquals(2, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) rules.get(1).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(1)).getOutputVlanId());

        BaseInstallFlow ingressRule = factory.createInstallIngressRulesForFlow(flow,
                asList(segment2to3, segment1to2));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getInputPort());
        assertEquals(11, (int) ingressRule.getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) ingressRule).getInputVlanId());
    }


    @Test
    public void shouldCreateRemoveRulesFor2SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();
        PathSegment segment2to3 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2, segment2to3));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveTransitAndEgressRulesForFlow(flow,
                asList(segment1to2, segment2to3));
        assertThat(rules, hasSize(2));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(22, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_3, rules.get(1).getSwitchId());
        assertEquals(31, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());

        RemoveFlow ingressRule = factory.createRemoveIngressRulesForFlow(flow, asList(segment1to2, segment2to3));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getCriteria().getInPort());
        assertEquals(11, (int) ingressRule.getCriteria().getOutPort());
        assertEquals(101, (int) ingressRule.getCriteria().getInVlan());
    }

    @Ignore("The order of segments is handled in DAO")
    @Test
    public void shouldCreateRemoveRulesFor2SegmentsPathWithWrongOrderOfSegments() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();
        PathSegment segment2to3 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2, segment2to3));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveTransitAndEgressRulesForFlow(flow,
                asList(segment2to3, segment1to2));
        assertThat(rules, hasSize(2));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(22, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_3, rules.get(1).getSwitchId());
        assertEquals(31, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());

        RemoveFlow ingressRule = factory.createRemoveIngressRulesForFlow(flow, asList(segment2to3, segment1to2));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getCriteria().getInPort());
        assertEquals(11, (int) ingressRule.getCriteria().getOutPort());
        assertEquals(101, (int) ingressRule.getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateRemoveRulesFor3SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch switch3 = Switch.builder().switchId(SWITCH_ID_3).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_4).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();
        PathSegment segment2to3 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(switch3)
                .destPort(31)
                .build();
        PathSegment segment3to4 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(switch3)
                .srcPort(32)
                .destSwitch(destSwitch)
                .destPort(41)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                destSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2, segment2to3, segment3to4));

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveTransitAndEgressRulesForFlow(flow,
                asList(segment1to2, segment2to3, segment3to4));
        assertThat(rules, hasSize(3));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_2, rules.get(0).getSwitchId());
        assertEquals(21, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(22, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_3, rules.get(1).getSwitchId());
        assertEquals(31, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(32, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_4, rules.get(2).getSwitchId());
        assertEquals(41, (int) rules.get(2).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(2).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(2).getCriteria().getInVlan());

        RemoveFlow ingressRule = factory.createRemoveIngressRulesForFlow(flow,
                asList(segment1to2, segment2to3, segment3to4));
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getCriteria().getInPort());
        assertEquals(11, (int) ingressRule.getCriteria().getOutPort());
        assertEquals(101, (int) ingressRule.getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateRemoveRulesWithoutMeter() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch dstSwitch = Switch.builder().switchId(SWITCH_ID_2).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        PathSegment segment1to2 = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(dstSwitch)
                .destPort(21)
                .build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, srcSwitch, 1, 101,
                dstSwitch, 2, 201, 301, TEST_COOKIE, 0, true,
                asList(segment1to2));

        FlowCommandFactory factory = new FlowCommandFactory();
        RemoveFlow command = factory.createRemoveIngressRulesForFlow(flow, ImmutableList.of(segment1to2));

        assertEquals(SWITCH_ID_1, command.getSwitchId());
        assertEquals(1, (int) command.getCriteria().getInPort());
        assertEquals(11, (int) command.getCriteria().getOutPort());
        assertEquals(101, (int) command.getCriteria().getInVlan());
        assertNull(command.getMeterId());
    }

    @Test
    public void shouldCreateInstallRulesForSingleSwitch() {
        Switch theSwitch = Switch.builder().switchId(SWITCH_ID_1).build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, theSwitch, 1, 101,
                theSwitch, 2, 201, 0, TEST_COOKIE, 0, true,
                Collections.emptyList());

        FlowCommandFactory factory = new FlowCommandFactory();
        List<InstallTransitFlow> rules =
                factory.createInstallTransitAndEgressRulesForFlow(flow, Collections.emptyList());
        assertThat(rules, hasSize(0));

        BaseInstallFlow ingressRule = factory.createInstallIngressRulesForFlow(flow, Collections.emptyList());
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getInputPort());
        assertEquals(101, (int) ((InstallOneSwitchFlow) ingressRule).getInputVlanId());
        assertEquals(2, (int) ingressRule.getOutputPort());
        assertEquals(201, (int) ((InstallOneSwitchFlow) ingressRule).getOutputVlanId());
    }

    @Test
    public void shouldCreateRemoveRulesForSingleSwitch() {
        Switch theSwitch = Switch.builder().switchId(SWITCH_ID_1).build();

        UnidirectionalFlow flow = buildFlow(TEST_FLOW, theSwitch, 1, 101,
                theSwitch, 2, 201, 0, TEST_COOKIE, 0, true,
                Collections.emptyList());

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveTransitAndEgressRulesForFlow(flow, Collections.emptyList());
        assertThat(rules, hasSize(0));

        RemoveFlow ingressRule = factory.createRemoveIngressRulesForFlow(flow, Collections.emptyList());
        assertEquals(TEST_COOKIE, (long) ingressRule.getCookie());
        assertEquals(SWITCH_ID_1, ingressRule.getSwitchId());
        assertEquals(1, (int) ingressRule.getCriteria().getInPort());
        assertNull(ingressRule.getCriteria().getOutPort());
        assertEquals(101, (int) ingressRule.getCriteria().getInVlan());
    }

    private UnidirectionalFlow buildFlow(String flowId, Switch srcSwitch, int srcPort, int srcVlan,
                                         Switch destSwitch, int destPort, int destVlan,
                                         int transitVlan, long cookie, int bandwidth,
                                         boolean ignoreBandwidth, List<PathSegment> pathSegments) {
        PathId pathId = pathSegments.isEmpty() ? new PathId(UUID.randomUUID().toString())
                : pathSegments.get(0).getPathId();

        TransitVlan vlan = TransitVlan.builder()
                .flowId(flowId)
                .pathId(pathId)
                .vlan(transitVlan)
                .build();

        FlowPath flowPath = FlowPath.builder()
                .flowId(flowId)
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .cookie(new Cookie(cookie))
                .segments(pathSegments)
                .build();

        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .destVlan(destVlan)
                .bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .forwardPath(flowPath)
                .build();

        return new UnidirectionalFlow(flow, flowPath, vlan, true);
    }
}

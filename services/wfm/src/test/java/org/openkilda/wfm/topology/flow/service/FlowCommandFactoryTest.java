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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class FlowCommandFactoryTest {
    private static final long TEST_COOKIE = Flow.FORWARD_FLOW_COOKIE_MASK | 1;
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");

    @Test
    public void shouldCreateInstallRulesFor1SegmentPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_2).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(destSwitch)
                .destPort(21)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<BaseInstallFlow> rules = factory.createInstallRulesForFlow(flow, Collections.singletonList(segment1to2));

        assertThat(rules, hasSize(2));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getInputPort());
        assertEquals(11, (int) rules.get(0).getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) rules.get(0)).getInputVlanId());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getInputPort());
        assertEquals(2, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) ((InstallEgressFlow) rules.get(1)).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(1)).getOutputVlanId());
    }

    @Test
    public void shouldCreateRemoveRulesFor1SegmentPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_2).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(destSwitch)
                .destPort(21)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveRulesForFlow(flow, Collections.singletonList(segment1to2));

        assertThat(rules, hasSize(2));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(11, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(101, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateInstallRulesFor2SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();

        FlowSegment segment2to3 = FlowSegment.builder()
                .seqId(1)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<BaseInstallFlow> rules = factory.createInstallRulesForFlow(flow, asList(segment1to2, segment2to3));

        assertThat(rules, hasSize(3));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getInputPort());
        assertEquals(11, (int) rules.get(0).getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) rules.get(0)).getInputVlanId());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getInputPort());
        assertEquals(22, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) ((InstallTransitFlow) rules.get(1)).getTransitVlanId());

        assertEquals(SWITCH_ID_3, rules.get(2).getSwitchId());
        assertEquals(31, (int) rules.get(2).getInputPort());
        assertEquals(2, (int) rules.get(2).getOutputPort());
        assertEquals(301, (int) ((InstallEgressFlow) rules.get(2)).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(2)).getOutputVlanId());
    }

    @Test
    public void shouldCreateInstallRulesFor3SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch switch3 = Switch.builder().switchId(SWITCH_ID_3).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_4).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();

        FlowSegment segment2to3 = FlowSegment.builder()
                .seqId(1)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(switch3)
                .destPort(31)
                .build();

        FlowSegment segment3to4 = FlowSegment.builder()
                .seqId(2)
                .srcSwitch(switch3)
                .srcPort(32)
                .destSwitch(destSwitch)
                .destPort(41)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<BaseInstallFlow> rules = factory.createInstallRulesForFlow(flow,
                asList(segment1to2, segment2to3, segment3to4));

        assertThat(rules, hasSize(4));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getInputPort());
        assertEquals(11, (int) rules.get(0).getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) rules.get(0)).getInputVlanId());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getInputPort());
        assertEquals(22, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) ((InstallTransitFlow) rules.get(1)).getTransitVlanId());

        assertEquals(SWITCH_ID_3, rules.get(2).getSwitchId());
        assertEquals(31, (int) rules.get(2).getInputPort());
        assertEquals(32, (int) rules.get(2).getOutputPort());
        assertEquals(301, (int) ((InstallTransitFlow) rules.get(2)).getTransitVlanId());

        assertEquals(SWITCH_ID_4, rules.get(3).getSwitchId());
        assertEquals(41, (int) rules.get(3).getInputPort());
        assertEquals(2, (int) rules.get(3).getOutputPort());
        assertEquals(301, (int) ((InstallEgressFlow) rules.get(3)).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(3)).getOutputVlanId());
    }

    @Test
    public void shouldCreateInstallRulesFor2SegmentsPathWithWrongOrderOfSegments() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();

        FlowSegment segment2to3 = FlowSegment.builder()
                .seqId(1)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<BaseInstallFlow> rules = factory.createInstallRulesForFlow(flow, asList(segment2to3, segment1to2));

        assertThat(rules, hasSize(3));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getInputPort());
        assertEquals(11, (int) rules.get(0).getOutputPort());
        assertEquals(101, (int) ((InstallIngressFlow) rules.get(0)).getInputVlanId());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getInputPort());
        assertEquals(22, (int) rules.get(1).getOutputPort());
        assertEquals(301, (int) ((InstallTransitFlow) rules.get(1)).getTransitVlanId());

        assertEquals(SWITCH_ID_3, rules.get(2).getSwitchId());
        assertEquals(31, (int) rules.get(2).getInputPort());
        assertEquals(2, (int) rules.get(2).getOutputPort());
        assertEquals(301, (int) ((InstallEgressFlow) rules.get(2)).getTransitVlanId());
        assertEquals(201, (int) ((InstallEgressFlow) rules.get(2)).getOutputVlanId());
    }


    @Test
    public void shouldCreateRemoveRulesFor2SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();

        FlowSegment segment2to3 = FlowSegment.builder()
                .seqId(1)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveRulesForFlow(flow, asList(segment1to2, segment2to3));

        assertThat(rules, hasSize(3));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(11, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(101, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(22, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_3, rules.get(2).getSwitchId());
        assertEquals(31, (int) rules.get(2).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(2).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(2).getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateRemoveRulesFor2SegmentsPathWithWrongOrderOfSegments() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_3).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();

        FlowSegment segment2to3 = FlowSegment.builder()
                .seqId(1)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(destSwitch)
                .destPort(31)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveRulesForFlow(flow, asList(segment1to2, segment2to3));

        assertThat(rules, hasSize(3));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(11, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(101, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(22, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_3, rules.get(2).getSwitchId());
        assertEquals(31, (int) rules.get(2).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(2).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(2).getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateRemoveRulesFor3SegmentsPath() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_ID_1).build();
        Switch switch2 = Switch.builder().switchId(SWITCH_ID_2).build();
        Switch switch3 = Switch.builder().switchId(SWITCH_ID_3).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_ID_4).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(destSwitch)
                .destPort(2)
                .destVlan(201)
                .transitVlan(301)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowSegment segment1to2 = FlowSegment.builder()
                .seqId(0)
                .srcSwitch(srcSwitch)
                .srcPort(11)
                .destSwitch(switch2)
                .destPort(21)
                .build();

        FlowSegment segment2to3 = FlowSegment.builder()
                .seqId(1)
                .srcSwitch(switch2)
                .srcPort(22)
                .destSwitch(switch3)
                .destPort(31)
                .build();

        FlowSegment segment3to4 = FlowSegment.builder()
                .seqId(2)
                .srcSwitch(switch3)
                .srcPort(32)
                .destSwitch(destSwitch)
                .destPort(41)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveRulesForFlow(flow, asList(segment1to2, segment2to3, segment3to4));

        assertThat(rules, hasSize(4));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getCriteria().getInPort());
        assertEquals(11, (int) rules.get(0).getCriteria().getOutPort());
        assertEquals(101, (int) rules.get(0).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_2, rules.get(1).getSwitchId());
        assertEquals(21, (int) rules.get(1).getCriteria().getInPort());
        assertEquals(22, (int) rules.get(1).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(1).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_3, rules.get(2).getSwitchId());
        assertEquals(31, (int) rules.get(2).getCriteria().getInPort());
        assertEquals(32, (int) rules.get(2).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(2).getCriteria().getInVlan());

        assertEquals(SWITCH_ID_4, rules.get(3).getSwitchId());
        assertEquals(41, (int) rules.get(3).getCriteria().getInPort());
        assertEquals(2, (int) rules.get(3).getCriteria().getOutPort());
        assertEquals(301, (int) rules.get(3).getCriteria().getInVlan());
    }

    @Test
    public void shouldCreateInstallRulesForSingleSwitch() {
        Switch theSwitch = Switch.builder().switchId(SWITCH_ID_1).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(theSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(theSwitch)
                .destPort(2)
                .destVlan(201)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<BaseInstallFlow> rules = factory.createInstallRulesForFlow(flow, Collections.emptyList());

        assertThat(rules, hasSize(1));
        assertThat(rules, everyItem(hasProperty("cookie", equalTo(TEST_COOKIE))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getInputPort());
        assertEquals(101, (int) ((InstallOneSwitchFlow) rules.get(0)).getInputVlanId());
        assertEquals(2, (int) rules.get(0).getOutputPort());
        assertEquals(201, (int) ((InstallOneSwitchFlow) rules.get(0)).getOutputVlanId());
    }

    @Test
    public void shouldCreateRemoveRulesForSingleSwitch() {
        Switch theSwitch = Switch.builder().switchId(SWITCH_ID_1).build();

        Flow flow = Flow.builder()
                .flowId("test-flow")
                .srcSwitch(theSwitch)
                .srcPort(1)
                .srcVlan(101)
                .destSwitch(theSwitch)
                .destPort(2)
                .destVlan(201)
                .cookie(TEST_COOKIE)
                .bandwidth(0)
                .ignoreBandwidth(true)
                .build();

        FlowCommandFactory factory = new FlowCommandFactory();
        List<RemoveFlow> rules = factory.createRemoveRulesForFlow(flow, Collections.emptyList());

        assertThat(rules, hasSize(1));
        assertThat(rules, everyItem(hasProperty("criteria",
                hasProperty("cookie", equalTo(TEST_COOKIE)))));
        assertEquals(SWITCH_ID_1, rules.get(0).getSwitchId());
        assertEquals(1, (int) rules.get(0).getCriteria().getInPort());
        assertNull(rules.get(0).getCriteria().getOutPort());
        assertEquals(101, (int) rules.get(0).getCriteria().getInVlan());
    }
}

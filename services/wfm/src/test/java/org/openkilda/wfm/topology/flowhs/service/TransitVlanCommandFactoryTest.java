/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.openkilda.floodlight.flow.request.InstallEgressRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallMultiSwitchIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.Neo4jBasedTest;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class TransitVlanCommandFactoryTest extends Neo4jBasedTest {
    private static final CommandContext COMMAND_CONTEXT = new CommandContext();
    private static final SwitchId SWITCH_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final Random UNSEED_RANDOM = new Random();

    private TransitVlanCommandFactory target;
    private TransitVlanRepository vlanRepository;

    @Before
    public void setUp() {
        target = new TransitVlanCommandFactory(persistenceManager.getRepositoryFactory().createTransitVlanRepository());
        vlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
    }

    @Test
    public void shouldCreateNonIngressCommandsWithoutVlans() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 0, destSwitch, 2, 0, 0);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<InstallTransitRule> commands = target.createInstallNonIngressRules(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());
        InstallTransitRule command = commands.get(0);
        assertThat("Should be command for egress rule", command, instanceOf(InstallEgressRule.class));

        InstallEgressRule installEgressRule = (InstallEgressRule) command;
        assertEquals(flow.getFlowId(), installEgressRule.getFlowId());
        assertEquals(destSwitch.getSwitchId(), installEgressRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), installEgressRule.getCookie());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) installEgressRule.getTransitVlanId());
        assertEquals(flow.getDestVlan(), (int) installEgressRule.getOutputVlanId());
        assertEquals(OutputVlanType.NONE, installEgressRule.getOutputVlanType());
        assertEquals(flow.getDestPort(), (int) installEgressRule.getOutputPort());
    }

    @Test
    public void shouldCreateNonIngressCommandsWithPushAndPopOutputType() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 0, 0);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<InstallTransitRule> commands = target.createInstallNonIngressRules(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());
        InstallTransitRule command = commands.get(0);
        assertThat("Should be command for egress rule", command, instanceOf(InstallEgressRule.class));

        InstallEgressRule forwardEgressRule = (InstallEgressRule) command;
        assertEquals(flow.getFlowId(), forwardEgressRule.getFlowId());
        assertEquals(destSwitch.getSwitchId(), forwardEgressRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), forwardEgressRule.getCookie());
        assertEquals(flow.getForwardPath().getSegments().get(0).getDestPort(), (int) forwardEgressRule.getInputPort());
        assertEquals(flow.getDestPort(), (int) forwardEgressRule.getOutputPort());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) forwardEgressRule.getTransitVlanId());
        assertEquals(flow.getDestVlan(), (int) forwardEgressRule.getOutputVlanId());
        assertEquals(OutputVlanType.POP, forwardEgressRule.getOutputVlanType());

        command = commands.get(1);
        assertThat("Should be command for egress rule", command, instanceOf(InstallEgressRule.class));
        InstallEgressRule reverseEgressRule = (InstallEgressRule) command;
        assertEquals(flow.getFlowId(), reverseEgressRule.getFlowId());
        assertEquals(srcSwitch.getSwitchId(), reverseEgressRule.getSwitchId());
        assertEquals(flow.getReversePath().getCookie(), reverseEgressRule.getCookie());
        assertEquals(flow.getReversePath().getSegments().get(0).getDestPort(), (int) reverseEgressRule.getInputPort());
        assertEquals(flow.getSrcPort(), (int) reverseEgressRule.getOutputPort());
        TransitVlan reverseVlan = vlanRepository.findByPathId(flow.getReversePathId(), flow.getForwardPathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(reverseVlan.getVlan(), (int) reverseEgressRule.getTransitVlanId());
        assertEquals(flow.getSrcVlan(), (int) reverseEgressRule.getOutputVlanId());
        assertEquals(OutputVlanType.PUSH, reverseEgressRule.getOutputVlanType());
    }

    @Test
    public void shouldCreateNonIngressCommandsWithReplaceOutputType() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 0);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<InstallTransitRule> commands = target.createInstallNonIngressRules(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());
        InstallTransitRule command = commands.get(0);
        assertThat("Should be command for egress rule", command, instanceOf(InstallEgressRule.class));

        InstallEgressRule srcSwitchRule = (InstallEgressRule) command;
        assertEquals(flow.getFlowId(), srcSwitchRule.getFlowId());
        assertEquals(destSwitch.getSwitchId(), srcSwitchRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), srcSwitchRule.getCookie());
        assertEquals(flow.getForwardPath().getSegments().get(0).getDestPort(), (int) srcSwitchRule.getInputPort());
        assertEquals(flow.getDestPort(), (int) srcSwitchRule.getOutputPort());

        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) srcSwitchRule.getTransitVlanId());
        assertEquals(flow.getDestVlan(), (int) srcSwitchRule.getOutputVlanId());
        assertEquals(OutputVlanType.REPLACE, srcSwitchRule.getOutputVlanType());
    }

    @Test
    public void shouldCreateNonIngressCommandsWithTransitSwitch() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_3).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 0);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithTransitSwitches(forward, reverse);

        List<InstallTransitRule> commands = target.createInstallNonIngressRules(COMMAND_CONTEXT, flow);
        assertEquals(4, commands.size());

        InstallTransitRule commandForTransitSwitch = commands.get(0);
        assertEquals(flow.getFlowId(), commandForTransitSwitch.getFlowId());
        assertEquals(SWITCH_2, commandForTransitSwitch.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), commandForTransitSwitch.getCookie());

        PathSegment transitIncomeSegment = flow.getForwardPath().getSegments().get(0);
        assertEquals(transitIncomeSegment.getDestPort(), (int) commandForTransitSwitch.getInputPort());
        PathSegment transitOutcomeSegment = flow.getForwardPath().getSegments().get(1);
        assertEquals(transitOutcomeSegment.getSrcPort(), (int) commandForTransitSwitch.getOutputPort());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) commandForTransitSwitch.getTransitVlanId());

        InstallTransitRule commandForDestSwitch = commands.get(1);
        assertThat("Should be command for egress rule", commandForDestSwitch, instanceOf(InstallEgressRule.class));

        InstallEgressRule egressRule = (InstallEgressRule) commandForDestSwitch;
        assertEquals(flow.getFlowId(), egressRule.getFlowId());
        assertEquals(destSwitch.getSwitchId(), egressRule.getSwitchId());
        assertEquals(flow.getForwardPath().getSegments().get(1).getDestPort(), (int) egressRule.getInputPort());
        assertEquals(flow.getDestPort(), (int) egressRule.getOutputPort());
        assertEquals(flow.getForwardPath().getCookie(), egressRule.getCookie());
        assertEquals(forwardVlan.getVlan(), (int) egressRule.getTransitVlanId());
        assertEquals(flow.getDestVlan(), (int) egressRule.getOutputVlanId());
        assertEquals(OutputVlanType.REPLACE, egressRule.getOutputVlanType());
    }

    @Test
    public void shouldCreateUnmeteredIngressCommands() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 0);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<InstallIngressRule> commands = target.createInstallIngressRules(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());

        InstallMultiSwitchIngressRule sourceSwitchRule = (InstallMultiSwitchIngressRule) commands.get(0);
        assertEquals(srcSwitch.getSwitchId(), sourceSwitchRule.getSwitchId());
        assertEquals(flow.getFlowId(), sourceSwitchRule.getFlowId());
        assertEquals(flow.getForwardPath().getCookie(), sourceSwitchRule.getCookie());
        assertEquals(flow.getSrcVlan(), (int) sourceSwitchRule.getInputVlanId());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) sourceSwitchRule.getTransitVlanId());
        assertEquals(0, (long) sourceSwitchRule.getBandwidth());
        assertNull(sourceSwitchRule.getMeterId());

        InstallMultiSwitchIngressRule destSwitchRule = (InstallMultiSwitchIngressRule) commands.get(1);
        assertEquals(destSwitch.getSwitchId(), destSwitchRule.getSwitchId());
        assertEquals(flow.getFlowId(), destSwitchRule.getFlowId());
        assertEquals(flow.getReversePath().getCookie(), destSwitchRule.getCookie());
        assertEquals(flow.getDestVlan(), (int) destSwitchRule.getInputVlanId());
        TransitVlan reverseVlan = vlanRepository.findByPathId(flow.getReversePathId(), flow.getForwardPathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(reverseVlan.getVlan(), (int) destSwitchRule.getTransitVlanId());
        assertEquals(0, (long) destSwitchRule.getBandwidth());
        assertNull(destSwitchRule.getMeterId());
    }

    @Test
    public void shouldCreateIngressCommands() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 1000);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<InstallIngressRule> commands = target.createInstallIngressRules(COMMAND_CONTEXT, flow);
        assertEquals(2, commands.size());
        InstallMultiSwitchIngressRule sourceSwitchRule = (InstallMultiSwitchIngressRule) commands.get(0);
        assertEquals(srcSwitch.getSwitchId(), sourceSwitchRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), sourceSwitchRule.getCookie());
        assertEquals(flow.getFlowId(), sourceSwitchRule.getFlowId());
        assertEquals(flow.getSrcVlan(), (int) sourceSwitchRule.getInputVlanId());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) sourceSwitchRule.getTransitVlanId());
        assertEquals(flow.getBandwidth(), (long) sourceSwitchRule.getBandwidth());
        assertEquals(flow.getForwardPath().getMeterId(), sourceSwitchRule.getMeterId());

        InstallMultiSwitchIngressRule destSwitchRule = (InstallMultiSwitchIngressRule) commands.get(1);
        assertEquals(destSwitchRule.getSwitchId(), destSwitchRule.getSwitchId());
        assertEquals(flow.getReversePath().getCookie(), destSwitchRule.getCookie());
        assertEquals(flow.getFlowId(), destSwitchRule.getFlowId());
        assertEquals(flow.getDestVlan(), (int) destSwitchRule.getInputVlanId());
        TransitVlan reverseVlan = vlanRepository.findByPathId(flow.getReversePathId(), flow.getForwardPathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(reverseVlan.getVlan(), (int) destSwitchRule.getTransitVlanId());
        assertEquals(flow.getBandwidth(), (long) destSwitchRule.getBandwidth());
        assertEquals(flow.getReversePath().getMeterId(), destSwitchRule.getMeterId());
    }

    @Test
    public void shouldCreateRemoveUnmeteredIngressCommands() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 0);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<RemoveRule> commands = target.createRemoveIngressRules(COMMAND_CONTEXT, flow);
        assertEquals("2 commands for ingress rules should be created", 2, commands.size());

        RemoveRule srcSwitchCommand = commands.get(0);
        assertEquals(srcSwitch.getSwitchId(), srcSwitchCommand.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), srcSwitchCommand.getCookie());
        assertEquals(flow.getFlowId(), srcSwitchCommand.getFlowId());
        assertNull(srcSwitchCommand.getMeterId());

        DeleteRulesCriteria srcSwitchCriteria = srcSwitchCommand.getCriteria();
        assertEquals(flow.getForwardPath().getCookie().getValue(), (long) srcSwitchCriteria.getCookie());
        assertEquals(flow.getSrcPort(), (int) srcSwitchCriteria.getInPort());
        assertEquals(flow.getForwardPath().getSegments().get(0).getSrcPort(), (int) srcSwitchCriteria.getOutPort());
        assertEquals(flow.getSrcVlan(), (int) srcSwitchCriteria.getInVlan());

        RemoveRule destSwitchCommand = commands.get(1);
        assertEquals(destSwitch.getSwitchId(), destSwitchCommand.getSwitchId());
        assertEquals(flow.getReversePath().getCookie(), destSwitchCommand.getCookie());
        assertEquals(flow.getFlowId(), destSwitchCommand.getFlowId());
        assertNull(destSwitchCommand.getMeterId());

        DeleteRulesCriteria destSwitchCriteria = destSwitchCommand.getCriteria();
        assertEquals(flow.getReversePath().getCookie().getValue(), (long) destSwitchCriteria.getCookie());
        assertEquals(flow.getDestPort(), (int) destSwitchCriteria.getInPort());
        assertEquals(flow.getReversePath().getSegments().get(0).getSrcPort(), (int) destSwitchCriteria.getOutPort());
        assertEquals(flow.getDestVlan(), (int) destSwitchCriteria.getInVlan());
    }

    @Test
    public void shouldCreateRemoveMeteredIngressCommands() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 1000);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);

        List<RemoveRule> commands = target.createRemoveIngressRules(COMMAND_CONTEXT, flow);
        assertEquals("2 commands for ingress rules should be created", 2, commands.size());

        RemoveRule srcSwitchCommand = commands.get(0);
        assertEquals(srcSwitch.getSwitchId(), srcSwitchCommand.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), srcSwitchCommand.getCookie());
        assertEquals(flow.getFlowId(), srcSwitchCommand.getFlowId());
        assertEquals(flow.getForwardPath().getMeterId(), srcSwitchCommand.getMeterId());

        DeleteRulesCriteria srcSwitchCriteria = srcSwitchCommand.getCriteria();
        assertEquals(flow.getForwardPath().getCookie().getValue(), (long) srcSwitchCriteria.getCookie());
        assertEquals(flow.getSrcPort(), (int) srcSwitchCriteria.getInPort());
        assertEquals(flow.getForwardPath().getSegments().get(0).getSrcPort(), (int) srcSwitchCriteria.getOutPort());
        assertEquals(flow.getSrcVlan(), (int) srcSwitchCriteria.getInVlan());

        RemoveRule destSwitchCommand = commands.get(1);
        assertEquals(destSwitch.getSwitchId(), destSwitchCommand.getSwitchId());
        assertEquals(flow.getReversePath().getCookie(), destSwitchCommand.getCookie());
        assertEquals(flow.getFlowId(), destSwitchCommand.getFlowId());
        assertEquals(flow.getReversePath().getMeterId(), destSwitchCommand.getMeterId());

        DeleteRulesCriteria destSwitchCriteria = destSwitchCommand.getCriteria();
        assertEquals(flow.getReversePath().getCookie().getValue(), (long) destSwitchCriteria.getCookie());
        assertEquals(flow.getDestPort(), (int) destSwitchCriteria.getInPort());
        assertEquals(flow.getReversePath().getSegments().get(0).getSrcPort(), (int) destSwitchCriteria.getOutPort());
        assertEquals(flow.getDestVlan(), (int) destSwitchCriteria.getInVlan());
    }

    @Test
    public void shouldCreateRemoveEgressRuleWithTransitSwitches() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_3).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 1000);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithTransitSwitches(forward, reverse);

        List<RemoveRule> commands = target.createRemoveNonIngressRules(COMMAND_CONTEXT, flow);
        assertEquals("4 commands for ingress rules should be created", 4, commands.size());

        RemoveRule forwardTransitRule = commands.get(0);
        assertEquals(SWITCH_2, forwardTransitRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), forwardTransitRule.getCookie());
        assertEquals(flow.getFlowId(), forwardTransitRule.getFlowId());
        assertNull(forwardTransitRule.getMeterId());

        DeleteRulesCriteria forwardTransitSwitchCriteria = forwardTransitRule.getCriteria();
        assertEquals(flow.getForwardPath().getCookie().getValue(), (long) forwardTransitSwitchCriteria.getCookie());

        PathSegment transitIncomeSegment = flow.getForwardPath().getSegments().get(0);
        assertEquals(transitIncomeSegment.getDestPort(), (int) forwardTransitSwitchCriteria.getInPort());
        PathSegment transitOutcomeSegment = flow.getForwardPath().getSegments().get(1);
        assertEquals(transitOutcomeSegment.getSrcPort(), (int) forwardTransitSwitchCriteria.getOutPort());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) forwardTransitSwitchCriteria.getInVlan());

        RemoveRule forwardEgressRule = commands.get(1);
        assertEquals(destSwitch.getSwitchId(), forwardEgressRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), forwardEgressRule.getCookie());
        assertEquals(flow.getFlowId(), forwardEgressRule.getFlowId());
        assertNull(forwardEgressRule.getMeterId());

        DeleteRulesCriteria forwardEgressSwitchCriteria = forwardEgressRule.getCriteria();
        assertEquals(flow.getForwardPath().getCookie().getValue(), (long) forwardEgressSwitchCriteria.getCookie());
        assertEquals(flow.getForwardPath().getSegments().get(1).getDestPort(),
                (int) forwardEgressSwitchCriteria.getInPort());
        assertEquals(flow.getDestPort(), (int) forwardEgressSwitchCriteria.getOutPort());
        assertEquals(forwardVlan.getVlan(), (int) forwardEgressSwitchCriteria.getInVlan());


    }

    @Test
    public void shouldCreateRemoveEgressRuleWithoutTransitSwitches() {
        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();
        Switch destSwitch = Switch.builder().switchId(SWITCH_2).build();

        Flow flow = buildFlow(srcSwitch, 1, 101, destSwitch, 2, 102, 1000);
        FlowPath forward = buildFlowPath(flow, flow.getSrcSwitch(), flow.getDestSwitch(), flow.getBandwidth());
        flow.setForwardPath(forward);
        FlowPath reverse = buildFlowPath(flow, flow.getDestSwitch(), flow.getSrcSwitch(), flow.getBandwidth());
        flow.setReversePath(reverse);
        setSegmentsWithoutTransitSwitches(forward, reverse);
        List<RemoveRule> commands = target.createRemoveNonIngressRules(COMMAND_CONTEXT, flow);
        assertEquals("2 commands for ingress rules should be created", 2, commands.size());

        RemoveRule forwardEgressRule = commands.get(0);
        assertEquals(destSwitch.getSwitchId(), forwardEgressRule.getSwitchId());
        assertEquals(flow.getForwardPath().getCookie(), forwardEgressRule.getCookie());
        assertEquals(flow.getFlowId(), forwardEgressRule.getFlowId());
        assertNull(forwardEgressRule.getMeterId());

        DeleteRulesCriteria forwardEgressSwitchCriteria = forwardEgressRule.getCriteria();
        assertEquals(flow.getForwardPath().getCookie().getValue(), (long) forwardEgressSwitchCriteria.getCookie());
        assertEquals(flow.getForwardPath().getSegments().get(0).getDestPort(),
                (int) forwardEgressSwitchCriteria.getInPort());
        assertEquals(flow.getDestPort(), (int) forwardEgressSwitchCriteria.getOutPort());
        TransitVlan forwardVlan = vlanRepository.findByPathId(flow.getForwardPathId(), flow.getReversePathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(forwardVlan.getVlan(), (int) forwardEgressSwitchCriteria.getInVlan());

        RemoveRule reverseEgressRule = commands.get(1);
        assertEquals(srcSwitch.getSwitchId(), reverseEgressRule.getSwitchId());
        assertEquals(flow.getReversePath().getCookie(), reverseEgressRule.getCookie());
        assertEquals(flow.getFlowId(), reverseEgressRule.getFlowId());
        assertNull(reverseEgressRule.getMeterId());

        DeleteRulesCriteria reverseEgressSwitchCriteria = reverseEgressRule.getCriteria();
        assertEquals(flow.getReversePath().getCookie().getValue(), (long) reverseEgressSwitchCriteria.getCookie());
        assertEquals(flow.getReversePath().getSegments().get(0).getDestPort(),
                (int) reverseEgressSwitchCriteria.getInPort());
        assertEquals(flow.getSrcPort(), (int) reverseEgressSwitchCriteria.getOutPort());
        TransitVlan reverseVlan = vlanRepository.findByPathId(flow.getReversePathId(), flow.getForwardPathId())
                .stream().findAny()
                .orElseThrow(() -> new IllegalStateException("Vlan should be present"));
        assertEquals(reverseVlan.getVlan(), (int) reverseEgressSwitchCriteria.getInVlan());
    }

    private void setSegmentsWithoutTransitSwitches(FlowPath forward, FlowPath reverse) {
        PathSegment switch1ToSwitch2 = PathSegment.builder()
                .path(forward)
                .srcSwitch(forward.getSrcSwitch())
                .srcPort(12)
                .destSwitch(forward.getDestSwitch())
                .destPort(22)
                .build();
        forward.setSegments(ImmutableList.of(switch1ToSwitch2));
        PathSegment switch2ToSwitch1 = PathSegment.builder()
                .path(reverse)
                .srcSwitch(reverse.getSrcSwitch())
                .srcPort(22)
                .destSwitch(reverse.getDestSwitch())
                .destPort(12)
                .build();
        reverse.setSegments(ImmutableList.of(switch2ToSwitch1));
    }

    private void setSegmentsWithTransitSwitches(FlowPath forward, FlowPath reverse) {
        PathSegment switch1ToSwitch2 = PathSegment.builder()
                .path(forward)
                .srcSwitch(forward.getSrcSwitch())
                .srcPort(12)
                .destSwitch(Switch.builder().switchId(SWITCH_2).build())
                .destPort(21)
                .build();
        PathSegment switch2ToSwitch3 = PathSegment.builder()
                .path(forward)
                .srcSwitch(Switch.builder().switchId(SWITCH_2).build())
                .srcPort(23)
                .destSwitch(forward.getDestSwitch())
                .destPort(32)
                .build();
        forward.setSegments(ImmutableList.of(switch1ToSwitch2, switch2ToSwitch3));

        PathSegment switch3ToSwitch2 = PathSegment.builder()
                .path(reverse)
                .srcSwitch(reverse.getSrcSwitch())
                .srcPort(32)
                .destSwitch(Switch.builder().switchId(SWITCH_2).build())
                .destPort(23)
                .build();
        PathSegment switch2ToSwitch1 = PathSegment.builder()
                .path(reverse)
                .srcSwitch(Switch.builder().switchId(SWITCH_2).build())
                .srcPort(21)
                .destSwitch(reverse.getDestSwitch())
                .destPort(12)
                .build();
        reverse.setSegments(ImmutableList.of(switch3ToSwitch2, switch2ToSwitch1));
    }

    private FlowPath buildFlowPath(Flow flow, Switch srcSwitch, Switch dstSwitch, long bandwidth) {
        PathId forwardPathId = new PathId(UUID.randomUUID().toString());
        TransitVlan forwardVlan = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(forwardPathId)
                .vlan(UNSEED_RANDOM.nextInt())
                .build();
        vlanRepository.createOrUpdate(forwardVlan);
        return FlowPath.builder()
                .flow(flow)
                .bandwidth(bandwidth)
                .cookie(new Cookie(UNSEED_RANDOM.nextLong()))
                .meterId(bandwidth != 0 ? new MeterId(UNSEED_RANDOM.nextInt()) : null)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .pathId(forwardPathId)
                .build();
    }

    private Flow buildFlow(Switch srcSwitch, int srcPort, int srcVlan, Switch dstSwitch, int dstPort, int dstVlan,
                           int bandwidth) {
        return Flow.builder()
                .flowId(UUID.randomUUID().toString())
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(dstSwitch)
                .destPort(dstPort)
                .destVlan(dstVlan)
                .bandwidth(bandwidth)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();
    }
}

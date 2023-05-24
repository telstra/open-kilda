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

package org.openkilda.rulemanager.factory;

import static java.lang.String.format;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchProperties;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.factory.generator.flow.EgressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.EgressYRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.EmptyGenerator;
import org.openkilda.rulemanager.factory.generator.flow.InputArpRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.InputLldpRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.MultiTableIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.MultiTableIngressYRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.MultiTableServer42IngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.SingleTableIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.SingleTableIngressYRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.SingleTableServer42IngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.TransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.TransitYRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.VlanStatsRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.haflow.EgressHaRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.haflow.IngressHaRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.haflow.TransitHaRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.haflow.YPointForwardEgressHaRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.haflow.YPointForwardIngressHaRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.haflow.YPointForwardTransitHaRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.loop.FlowLoopIngressRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.loop.FlowLoopTransitRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.mirror.EgressMirrorRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.mirror.IngressMirrorRuleGenerator;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class FlowRulesGeneratorFactory {

    private final RuleManagerConfig config;

    public FlowRulesGeneratorFactory(RuleManagerConfig config) {
        this.config = config;
    }

    /**
     * Get ingress rule generator.
     */
    public RuleGenerator getIngressRuleGenerator(
            FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation,
            Set<FlowSideAdapter> overlappingIngressAdapters) {
        boolean multiTable = isPathSrcMultiTable(flowPath, flow);
        if (multiTable) {
            return MultiTableIngressRuleGenerator.builder()
                    .config(config)
                    .flowPath(flowPath)
                    .flow(flow)
                    .encapsulation(encapsulation)
                    .overlappingIngressAdapters(overlappingIngressAdapters)
                    .build();
        } else {
            return SingleTableIngressRuleGenerator.builder()
                    .config(config)
                    .flowPath(flowPath)
                    .flow(flow)
                    .encapsulation(encapsulation)
                    .build();
        }
    }

    /**
     * Get server42 ingress rule generator.
     */
    public RuleGenerator getServer42IngressRuleGenerator(
            FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation,
            SwitchProperties switchProperties, Set<FlowSideAdapter> overlappingIngressAdapters) {
        boolean multiTable = isPathSrcMultiTable(flowPath, flow);
        if (multiTable) {
            return MultiTableServer42IngressRuleGenerator.builder()
                    .config(config)
                    .flowPath(flowPath)
                    .flow(flow)
                    .encapsulation(encapsulation)
                    .overlappingIngressAdapters(overlappingIngressAdapters)
                    .switchProperties(switchProperties)
                    .build();
        } else {
            return SingleTableServer42IngressRuleGenerator.builder()
                    .config(config)
                    .flowPath(flowPath)
                    .flow(flow)
                    .encapsulation(encapsulation)
                    .switchProperties(switchProperties)
                    .build();
        }
    }

    /**
     * Get vlan stats rule generator.
     */
    public RuleGenerator getVlanStatsRuleGenerator(FlowPath flowPath, Flow flow) {
        boolean multiTable = isPathSrcMultiTable(flowPath, flow);
        if (multiTable) {
            return VlanStatsRuleGenerator.builder()
                    .flow(flow)
                    .flowPath(flowPath)
                    .build();
        } else {
            // Vlan stats feature is not supported in single table mode.
            return new EmptyGenerator();
        }
    }

    /**
     * Get ingress y-rule generator.
     */
    public RuleGenerator getIngressYRuleGenerator(
            FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation,
            Set<FlowSideAdapter> overlappingIngressAdapters, MeterId sharedMeterId, UUID externalMeterCommandUuid,
            boolean generateMeterCommand) {
        boolean multiTable = isPathSrcMultiTable(flowPath, flow);
        if (multiTable) {
            return MultiTableIngressYRuleGenerator.builder()
                    .config(config)
                    .flowPath(flowPath)
                    .flow(flow)
                    .encapsulation(encapsulation)
                    .overlappingIngressAdapters(overlappingIngressAdapters)
                    .sharedMeterId(sharedMeterId)
                    .externalMeterCommandUuid(externalMeterCommandUuid)
                    .generateMeterCommand(generateMeterCommand)
                    .build();
        } else {
            return SingleTableIngressYRuleGenerator.builder()
                    .config(config)
                    .flowPath(flowPath)
                    .flow(flow)
                    .encapsulation(encapsulation)
                    .sharedMeterId(sharedMeterId)
                    .externalMeterCommandUuid(externalMeterCommandUuid)
                    .generateMeterCommand(generateMeterCommand)
                    .build();
        }
    }

    /**
     * Get ingress loop rule generator.
     */
    public RuleGenerator getIngressLoopRuleGenerator(FlowPath flowPath, Flow flow) {
        return FlowLoopIngressRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .multiTable(isPathSrcMultiTable(flowPath, flow))
                .build();
    }

    /**
     * Get ingress mirror rule generator.
     */
    public RuleGenerator getIngressMirrorRuleGenerator(
            FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation, UUID sharedMeterCommandUuid) {
        return IngressMirrorRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .multiTable(isPathSrcMultiTable(flowPath, flow))
                .config(config)
                .encapsulation(encapsulation)
                .sharedMeterCommandUuid(sharedMeterCommandUuid)
                .build();
    }

    /**
     * Get input LLDP rule generator.
     */
    public RuleGenerator getInputLldpRuleGenerator(
            FlowPath flowPath, Flow flow, Set<FlowSideAdapter> overlappingIngressAdapters) {
        return InputLldpRuleGenerator.builder()
                .ingressEndpoint(FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint())
                .multiTable(isPathSrcMultiTable(flowPath, flow))
                .overlappingIngressAdapters(overlappingIngressAdapters)
                .build();
    }

    /**
     * Get input ARP rule generator.
     */
    public RuleGenerator getInputArpRuleGenerator(
            FlowPath flowPath, Flow flow, Set<FlowSideAdapter> overlappingIngressAdapters) {
        return InputArpRuleGenerator.builder()
                .ingressEndpoint(FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint())
                .multiTable(isPathSrcMultiTable(flowPath, flow))
                .overlappingIngressAdapters(overlappingIngressAdapters)
                .build();
    }

    /**
     * Get egress rule generator.
     */
    public RuleGenerator getEgressRuleGenerator(FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation) {
        checkEgressRulePreRequirements(flowPath, flow, "egress");
        return EgressRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .encapsulation(encapsulation)
                .build();
    }

    /**
     * Get egress rule generator.
     */
    public RuleGenerator getEgressYRuleGenerator(FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation,
                                                 MeterId sharedMeterId, UUID externalMeterCommandUuid,
                                                 boolean generateMeterCommand) {
        checkEgressRulePreRequirements(flowPath, flow, "egress");
        return EgressYRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .encapsulation(encapsulation)
                .config(config)
                .sharedMeterId(sharedMeterId)
                .externalMeterCommandUuid(externalMeterCommandUuid)
                .generateMeterCommand(generateMeterCommand)
                .build();
    }

    /**
     * Get egress mirror rule generator.
     */
    public RuleGenerator getEgressMirrorRuleGenerator(
            FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation) {
        checkEgressRulePreRequirements(flowPath, flow, "egress mirror");
        return EgressMirrorRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .encapsulation(encapsulation)
                .build();
    }

    /**
     * Get transit rule generator.
     */
    public RuleGenerator getTransitRuleGenerator(FlowPath flowPath, FlowTransitEncapsulation encapsulation,
                                                 PathSegment firstSegment, PathSegment secondSegment) {
        if (flowPath.isOneSwitchPath()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create transit rule for path %s because it is one switch path", flowPath.getPathId()));
        }

        if (!firstSegment.getDestSwitchId().equals(secondSegment.getSrcSwitchId())) {
            throw new IllegalArgumentException(format(
                    "Couldn't create transit rule for path %s because segments switch ids are different: %s, %s",
                    flowPath.getPathId(), firstSegment.getDestSwitchId(), secondSegment.getSrcSwitchId()));
        }

        return TransitRuleGenerator.builder()
                .flowPath(flowPath)
                .encapsulation(encapsulation)
                .inPort(firstSegment.getDestPort())
                .outPort(secondSegment.getSrcPort())
                .multiTable(isSegmentMultiTable(firstSegment, secondSegment))
                .build();
    }

    /**
     * Get transit y-rule generator.
     */
    public TransitYRuleGenerator getTransitYRuleGenerator(FlowPath flowPath, FlowTransitEncapsulation encapsulation,
                                                          PathSegment firstSegment, PathSegment secondSegment,
                                                          MeterId sharedMeterId, UUID externalMeterCommandUuid,
                                                          boolean generateMeterCommand) {
        if (flowPath.isOneSwitchPath()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create transit rule for path %s because it is one switch path", flowPath.getPathId()));
        }

        if (!firstSegment.getDestSwitchId().equals(secondSegment.getSrcSwitchId())) {
            throw new IllegalArgumentException(format(
                    "Couldn't create transit rule for path %s because segments switch ids are different: %s, %s",
                    flowPath.getPathId(), firstSegment.getDestSwitchId(), secondSegment.getSrcSwitchId()));
        }

        return TransitYRuleGenerator.builder()
                .flowPath(flowPath)
                .encapsulation(encapsulation)
                .inPort(firstSegment.getDestPort())
                .outPort(secondSegment.getSrcPort())
                .multiTable(isSegmentMultiTable(firstSegment, secondSegment))
                .config(config)
                .sharedMeterId(sharedMeterId)
                .externalMeterCommandUuid(externalMeterCommandUuid)
                .generateMeterCommand(generateMeterCommand)
                .build();
    }

    /**
     * Get transit ha rule generator.
     */
    public RuleGenerator getTransitHaRuleGenerator(
            FlowPath subPath, FlowTransitEncapsulation encapsulation, PathSegment firstSegment,
            PathSegment secondSegment, boolean sharedSegment, MeterId sharedMeterId, UUID externalMeterCommandUuid,
            boolean generateCreateMeterCommand) {
        if (subPath.isOneSwitchPath()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create ha transit rule for path %s because it is one switch path", subPath.getPathId()));
        }

        if (!firstSegment.getDestSwitchId().equals(secondSegment.getSrcSwitchId())) {
            throw new IllegalArgumentException(format(
                    "Couldn't create ha transit rule for path %s because segments switch ids are different: %s, %s",
                    subPath.getPathId(), firstSegment.getDestSwitchId(), secondSegment.getSrcSwitchId()));
        }

        return TransitHaRuleGenerator.builder()
                .subPath(subPath)
                .encapsulation(encapsulation)
                .inPort(firstSegment.getDestPort())
                .outPort(secondSegment.getSrcPort())
                .sharedSegment(sharedSegment)
                .sharedMeterId(sharedMeterId)
                .externalMeterCommandUuid(externalMeterCommandUuid)
                .generateCreateMeterCommand(generateCreateMeterCommand)
                .config(config)
                .build();
    }

    /**
     * Get Y point forward ingress ha rule generator.
     */
    public RuleGenerator getYPointForwardIngressHaRuleGenerator(
            HaFlow haFlow, HaFlowPath haFlowPath, List<FlowPath> subPaths, FlowTransitEncapsulation encapsulation,
            Set<FlowSideAdapter> overlappingIngressAdapters) {
        //TODO check one switch sub paths
        return YPointForwardIngressHaRuleGenerator.builder()
                .config(config)
                .haFlow(haFlow)
                .haFlowPath(haFlowPath)
                .subPaths(subPaths)
                .encapsulation(encapsulation)
                .overlappingIngressAdapters(overlappingIngressAdapters)
                .build();
    }

    /**
     * Get Y point forward transit ha rule generator.
     */
    public RuleGenerator getYPointForwardTransitHaRuleGenerator(
            HaFlow haFlow, HaFlowPath haFlowPath, List<FlowPath> subPaths, FlowTransitEncapsulation encapsulation,
            int inPort, Map<PathId, Integer> outPorts) {
        return YPointForwardTransitHaRuleGenerator.builder()
                .haFlow(haFlow)
                .haFlowPath(haFlowPath)
                .subPaths(subPaths)
                .encapsulation(encapsulation)
                .inPort(inPort)
                .outPorts(outPorts)
                .build();
    }

    /**
     * Get Y point forward egress ha rule generator.
     */
    public RuleGenerator getYPointForwardEgressHaRuleGenerator(
            HaFlow haFlow, HaFlowPath haFlowPath, List<FlowPath> subPaths, FlowTransitEncapsulation encapsulation,
            int inPort) {
        return YPointForwardEgressHaRuleGenerator.builder()
                .haFlow(haFlow)
                .haFlowPath(haFlowPath)
                .subPaths(subPaths)
                .encapsulation(encapsulation)
                .inPort(inPort)
                .build();
    }

    /**
     * Get ingress ha rule generator.
     */
    public RuleGenerator getIngressHaRuleGenerator(
            HaFlow haFlow, FlowPath subPath, MeterId meterId, FlowTransitEncapsulation encapsulation,
            boolean sharedPath, Set<FlowSideAdapter> overlappingIngressAdapters, UUID externalMeterCommandUuid,
            boolean generateMeterCommand) {
        return IngressHaRuleGenerator.builder()
                .config(config)
                .haFlow(haFlow)
                .subPath(subPath)
                .meterId(meterId)
                .isSharedPath(sharedPath)
                .encapsulation(encapsulation)
                .overlappingIngressAdapters(overlappingIngressAdapters)
                .externalMeterCommandUuid(externalMeterCommandUuid)
                .generateCreateMeterCommand(generateMeterCommand)
                .build();
    }

    /**
     * Get egress ha rule generator.
     */
    public RuleGenerator getEgressHaRuleGenerator(
            HaFlow haFlow, FlowPath subPath, FlowTransitEncapsulation encapsulation, boolean isSharedPath,
            MeterId sharedMeterId, UUID externalMeterCommandUuid, boolean generateCreateMeterCommand) {
        return EgressHaRuleGenerator.builder()
                .haFlow(haFlow)
                .subPath(subPath)
                .encapsulation(encapsulation)
                .isSharedPath(isSharedPath)
                .sharedMeterId(sharedMeterId)
                .externalMeterCommandUuid(externalMeterCommandUuid)
                .generateCreateMeterCommand(generateCreateMeterCommand)
                .config(config)
                .build();
    }

    /**
     * Get transit loop rule generator.
     */
    public RuleGenerator getTransitLoopRuleGenerator(
            FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation, int inPort) {
        return FlowLoopTransitRuleGenerator.builder()
                .flowPath(flowPath)
                .flow(flow)
                .multiTable(isPathSrcMultiTable(flowPath, flow))
                .inPort(inPort)
                .encapsulation(encapsulation)
                .build();
    }

    private boolean isSegmentMultiTable(PathSegment first, PathSegment second) {
        if (first.isDestWithMultiTable() != second.isSrcWithMultiTable()) {
            throw new IllegalStateException(
                    format("Paths segments %s and %s has different multi table flag for switch %s",
                            first, second, first.getDestSwitchId()));
        }
        return first.isDestWithMultiTable();
    }

    private boolean isPathSrcMultiTable(FlowPath flowPath, Flow flow) {
        if (flowPath.isOneSwitchPath()) {
            return flowPath.isSrcWithMultiTable();
        }
        ensureEqualMultiTableFlag(flowPath, flow);
        return flowPath.isSrcWithMultiTable();
    }

    private void ensureEqualMultiTableFlag(FlowPath flowPath, Flow flow) {
        if (flowPath.getSegments() == null || flowPath.getSegments().isEmpty()) {
            throw new IllegalStateException(
                    format("No segments found for path %s", flowPath.getPathId()));
        }
        PathSegment segment = flowPath.getSegments().get(0);
        if (flowPath.isSrcWithMultiTable() != segment.isSrcWithMultiTable()) {
            String errorMessage = String.format("First flow(id:%s, path:%s) segment and flow path level multi-table "
                            + "flag values are incompatible to each other - flow path(%s) != segment(%s)",
                    flow.getFlowId(), flowPath.getPathId(),
                    flowPath.isSrcWithMultiTable(), segment.isSrcWithMultiTable());
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void checkEgressRulePreRequirements(FlowPath flowPath, Flow flow, String ruleName) {
        if (flowPath.isOneSwitchPath()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create %s rule for flow %s and path %s because it is one switch flow",
                    ruleName, flow.getFlowId(), flowPath.getPathId()));
        }

        if (flowPath.getSegments().isEmpty()) {
            throw new IllegalArgumentException(format(
                    "Couldn't create %s rule for flow %s and path %s because path segments list is empty",
                    ruleName, flow.getFlowId(), flowPath.getPathId()));
        }
    }
}

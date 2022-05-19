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

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openkilda.adapter.FlowSideAdapter.makeIngressAdapter;
import static org.openkilda.model.cookie.Cookie.DROP_RULE_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_EGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_POST_INGRESS_DROP_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE;
import static org.openkilda.model.cookie.Cookie.MULTITABLE_TRANSIT_DROP_COOKIE;
import static org.openkilda.rulemanager.utils.RuleManagerHelper.postProcessCommands;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.YFlow;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.factory.FlowRulesGeneratorFactory;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.ServiceRulesGeneratorFactory;
import org.openkilda.rulemanager.factory.generator.flow.JointRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.JointRuleGenerator.JointRuleGeneratorBuilder;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class RuleManagerImpl implements RuleManager {

    ServiceRulesGeneratorFactory serviceRulesFactory;
    FlowRulesGeneratorFactory flowRulesFactory;

    public RuleManagerImpl(RuleManagerConfig config) {
        serviceRulesFactory = new ServiceRulesGeneratorFactory(config);
        flowRulesFactory = new FlowRulesGeneratorFactory(config);
    }

    @Override
    public List<SpeakerData> buildRulesForFlowPath(
            FlowPath flowPath, boolean filterOutUsedSharedRules, DataAdapter adapter) {
        List<SpeakerData> result = new ArrayList<>();
        Flow flow = adapter.getFlow(flowPath.getPathId());
        PathId oppositePathId = flow.getOppositePathId(flowPath.getPathId()).orElse(null);
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(flowPath.getPathId(), oppositePathId);

        if (!flow.isProtectedPath(flowPath.getPathId())) {
            Set<FlowSideAdapter> overlappingAdapters = new HashSet<>();
            if (filterOutUsedSharedRules) {
                overlappingAdapters = getOverlappingMultiTableIngressAdapters(flowPath, adapter);
            }
            buildIngressCommands(adapter.getSwitch(flowPath.getSrcSwitchId()), flowPath, flow, encapsulation,
                    overlappingAdapters, adapter.getSwitchProperties(flowPath.getSrcSwitchId()),
                    adapter.getFeatureToggles());
        }

        if (flowPath.isOneSwitchFlow()) {
            return result;
        }

        result.addAll(buildEgressCommands(
                adapter.getSwitch(flowPath.getDestSwitchId()), flowPath, flow, encapsulation));

        for (int i = 1; i < flowPath.getSegments().size(); i++) {
            PathSegment firstSegment = flowPath.getSegments().get(i - 1);
            PathSegment secondSegment = flowPath.getSegments().get(i);
            result.addAll(buildTransitCommands(adapter.getSwitch(firstSegment.getDestSwitchId()),
                    flowPath, encapsulation, firstSegment, secondSegment));
        }

        if (flow.isLooped()) {
            Switch loopedSwitch = adapter.getSwitch(flow.getLoopSwitchId());
            result.addAll(buildTransitLoopCommands(loopedSwitch, flowPath, flow, encapsulation));
        }

        return postProcessCommands(result);
    }

    private Set<FlowSideAdapter> getOverlappingMultiTableIngressAdapters(FlowPath path, DataAdapter adapter) {
        FlowEndpoint endpoint = makeIngressAdapter(adapter.getFlow(path.getPathId()), path).getEndpoint();

        Set<FlowSideAdapter> result = new HashSet<>();
        if (!path.isSrcWithMultiTable()) {
            // we do not care about overlapping for single table paths
            return result;
        }

        for (FlowPath overlappingPath : adapter.getFlowPaths().values()) {
            if (overlappingPath.isSrcWithMultiTable()
                    && path.getSrcSwitchId().equals(overlappingPath.getSrcSwitchId())) {
                Flow overlappingFlow = adapter.getFlow(overlappingPath.getPathId());
                FlowSideAdapter flowAdapter = makeIngressAdapter(overlappingFlow, overlappingPath);
                if (endpoint.getPortNumber().equals(flowAdapter.getEndpoint().getPortNumber())) {
                    result.add(flowAdapter);
                }
            }
        }
        return result;
    }

    @Override
    public List<SpeakerData> buildRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        Switch sw = adapter.getSwitch(switchId);
        List<SpeakerData> result = buildServiceRules(sw, adapter);

        result.addAll(buildFlowRulesForSwitch(switchId, adapter));

        return postProcessCommands(result);
    }

    private List<SpeakerData> buildServiceRules(Switch sw, DataAdapter adapter) {
        return getServiceRuleGenerators(sw.getSwitchId(), adapter).stream()
                .flatMap(g -> g.generateCommands(sw).stream())
                .collect(toList());
    }

    @VisibleForTesting
    List<RuleGenerator> getServiceRuleGenerators(SwitchId switchId, DataAdapter adapter) {
        List<RuleGenerator> generators = new ArrayList<>();
        generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(new Cookie(DROP_RULE_COOKIE), OfTable.INPUT));
        generators.add(serviceRulesFactory.getUniCastDiscoveryRuleGenerator());
        generators.add(serviceRulesFactory.getBroadCastDiscoveryRuleGenerator());
        generators.add(serviceRulesFactory.getDropDiscoveryLoopRuleGenerator());
        generators.add(serviceRulesFactory.getBfdCatchRuleGenerator());
        generators.add(serviceRulesFactory.getRoundTripLatencyRuleGenerator());
        generators.add(serviceRulesFactory.getUnicastVerificationVxlanRuleGenerator());

        SwitchProperties switchProperties = adapter.getSwitchProperties(switchId);
        if (switchProperties.isMultiTable()) {
            generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(
                    new Cookie(MULTITABLE_INGRESS_DROP_COOKIE), OfTable.INGRESS));
            generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(
                    new Cookie(MULTITABLE_TRANSIT_DROP_COOKIE), OfTable.TRANSIT));
            generators.add(serviceRulesFactory.getTableDefaultRuleGenerator(
                    new Cookie(MULTITABLE_POST_INGRESS_DROP_COOKIE), OfTable.POST_INGRESS));
            generators.add(serviceRulesFactory.getTablePassThroughDefaultRuleGenerator(
                    new Cookie(MULTITABLE_EGRESS_PASS_THROUGH_COOKIE), OfTable.TRANSIT, OfTable.EGRESS));
            generators.add(serviceRulesFactory.getTablePassThroughDefaultRuleGenerator(
                    new Cookie(MULTITABLE_PRE_INGRESS_PASS_THROUGH_COOKIE), OfTable.INGRESS, OfTable.PRE_INGRESS));
            generators.add(serviceRulesFactory.getLldpPostIngressRuleGenerator());
            generators.add(serviceRulesFactory.getLldpPostIngressVxlanRuleGenerator());
            generators.add(serviceRulesFactory.getLldpPostIngressOneSwitchRuleGenerator());
            generators.add(serviceRulesFactory.getArpPostIngressRuleGenerator());
            generators.add(serviceRulesFactory.getArpPostIngressVxlanRuleGenerator());
            generators.add(serviceRulesFactory.getArpPostIngressOneSwitchRuleGenerator());

            if (switchProperties.isSwitchLldp()) {
                generators.add(serviceRulesFactory.getLldpTransitRuleGenerator());
                generators.add(serviceRulesFactory.getLldpInputPreDropRuleGenerator());
                generators.add(serviceRulesFactory.getLldpIngressRuleGenerator());
            }
            if (switchProperties.isSwitchArp()) {
                generators.add(serviceRulesFactory.getArpTransitRuleGenerator());
                generators.add(serviceRulesFactory.getArpInputPreDropRuleGenerator());
                generators.add(serviceRulesFactory.getArpIngressRuleGenerator());
            }

            Set<Integer> islPorts = adapter.getSwitchIslPorts(switchId);

            islPorts.forEach(islPort -> {
                generators.add(serviceRulesFactory.getEgressIslVxlanRuleGenerator(islPort));
                generators.add(serviceRulesFactory.getEgressIslVlanRuleGenerator(islPort));
                generators.add(serviceRulesFactory.getTransitIslVxlanRuleGenerator(islPort));
            });
        }

        Integer server42Port = switchProperties.getServer42Port();
        Integer server42Vlan = switchProperties.getServer42Vlan();
        MacAddress server42MacAddress = switchProperties.getServer42MacAddress();

        KildaFeatureToggles featureToggles = adapter.getFeatureToggles();

        if (featureToggles.getServer42FlowRtt()) {
            generators.add(serviceRulesFactory.getServer42FlowRttTurningRuleGenerator());
            generators.add(serviceRulesFactory.getServer42FlowRttVxlanTurningRuleGenerator());

            if (switchProperties.isServer42FlowRtt()) {
                generators.add(serviceRulesFactory.getServer42FlowRttOutputVlanRuleGenerator(
                        server42Port, server42Vlan, server42MacAddress));
                generators.add(serviceRulesFactory.getServer42FlowRttOutputVxlanRuleGenerator(
                        server42Port, server42Vlan, server42MacAddress));
            }
        }

        if (featureToggles.getServer42IslRtt() && switchProperties.hasServer42IslRttEnabled()) {
            generators.add(serviceRulesFactory.getServer42IslRttTurningRuleGenerator());
            generators.add(serviceRulesFactory.getServer42IslRttOutputRuleGenerator(
                    server42Port, server42Vlan, server42MacAddress));

            for (Integer islPort : adapter.getSwitchIslPorts(switchId)) {
                generators.add(serviceRulesFactory.getServer42IslRttInputRuleGenerator(server42Port, islPort));
            }
        }

        return generators;
    }

    private List<SpeakerData> buildFlowRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        List<SpeakerData> result = adapter.getFlowPaths().values().stream()
                .flatMap(flowPath -> buildFlowRulesForSwitch(switchId, flowPath, adapter).stream())
                .collect(Collectors.toList());

        result.addAll(buildYFlowRulesForSwitch(switchId, adapter));
        return result;
    }

    /**
     * Builds command data only for switches present in the map. Silently skips all others.
     */
    private List<SpeakerData> buildFlowRulesForSwitch(
            SwitchId switchId, FlowPath flowPath, DataAdapter adapter) {
        List<SpeakerData> result = new ArrayList<>();
        Flow flow = adapter.getFlow(flowPath.getPathId());
        Switch sw = adapter.getSwitch(switchId);
        PathId oppositePathId = flow.getOppositePathId(flowPath.getPathId()).orElse(null);
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(flowPath.getPathId(), oppositePathId);

        if (switchId.equals(flowPath.getSrcSwitchId()) && !flow.isProtectedPath(flowPath.getPathId())) {
            // TODO filter out equal shared rules from the result list
            result.addAll(buildIngressCommands(sw, flowPath, flow, encapsulation, new HashSet<>(),
                    adapter.getSwitchProperties(switchId), adapter.getFeatureToggles()));
        }

        if (!flowPath.isOneSwitchFlow()) {
            if (switchId.equals(flowPath.getDestSwitchId())) {
                result.addAll(buildEgressCommands(sw, flowPath, flow, encapsulation));
            }
            for (int i = 1; i < flowPath.getSegments().size(); i++) {
                PathSegment firstSegment = flowPath.getSegments().get(i - 1);
                PathSegment secondSegment = flowPath.getSegments().get(i);
                if (switchId.equals(firstSegment.getDestSwitchId())
                        && switchId.equals(secondSegment.getSrcSwitchId())) {
                    result.addAll(buildTransitCommands(sw, flowPath, encapsulation, firstSegment, secondSegment));
                    break;
                }
            }

            if (flow.isLooped() && sw.getSwitchId().equals(flow.getLoopSwitchId())
                    && !flow.isProtectedPath(flowPath.getPathId())) {
                result.addAll(buildTransitLoopCommands(sw, flowPath, flow, encapsulation));
            }
        }

        return result;
    }

    private List<SpeakerData> buildIngressCommands(Switch sw, FlowPath flowPath, Flow flow,
                                                   FlowTransitEncapsulation encapsulation,
                                                   Set<FlowSideAdapter> overlappingIngressAdapters,
                                                   SwitchProperties switchProperties,
                                                   KildaFeatureToggles featureToggles) {
        List<SpeakerData> ingressCommands = flowRulesFactory.getIngressRuleGenerator(
                flowPath, flow, encapsulation, overlappingIngressAdapters).generateCommands(sw);
        UUID ingressMeterCommandUuid = Utils.getCommand(MeterSpeakerData.class, ingressCommands)
                .map(SpeakerData::getUuid).orElse(null);

        List<RuleGenerator> generators = new ArrayList<>();
        if (featureToggles.getServer42FlowRtt()) {
            generators.add(flowRulesFactory.getServer42IngressRuleGenerator(flowPath, flow, encapsulation,
                    switchProperties));
        }
        generators.add(flowRulesFactory.getInputLldpRuleGenerator(flowPath, flow, overlappingIngressAdapters));
        generators.add(flowRulesFactory.getInputArpRuleGenerator(flowPath, flow, overlappingIngressAdapters));

        if (flow.isLooped() && sw.getSwitchId().equals(flow.getLoopSwitchId())) {
            generators.add(flowRulesFactory.getIngressLoopRuleGenerator(flowPath, flow));
        }
        if (flowPath.getFlowMirrorPointsSet() != null && !flowPath.getFlowMirrorPointsSet().isEmpty()) {
            generators.add(flowRulesFactory.getIngressMirrorRuleGenerator(
                    flowPath, flow, encapsulation, ingressMeterCommandUuid));
        }

        ingressCommands.addAll(generateRules(sw, generators));
        return ingressCommands;
    }

    private List<SpeakerData> buildEgressCommands(Switch sw, FlowPath flowPath, Flow flow,
                                                  FlowTransitEncapsulation encapsulation) {
        List<RuleGenerator> generators = new ArrayList<>();

        generators.add(flowRulesFactory.getEgressRuleGenerator(flowPath, flow, encapsulation));
        if (flowPath.getFlowMirrorPointsSet() != null && !flowPath.getFlowMirrorPointsSet().isEmpty()) {
            generators.add(flowRulesFactory.getEgressMirrorRuleGenerator(flowPath, flow, encapsulation));
        }

        return generateRules(sw, generators);
    }

    private List<SpeakerData> buildTransitCommands(
            Switch sw, FlowPath flowPath, FlowTransitEncapsulation encapsulation, PathSegment firstSegment,
            PathSegment secondSegment) {
        RuleGenerator generator = flowRulesFactory.getTransitRuleGenerator(
                flowPath, encapsulation, firstSegment, secondSegment);
        return generator.generateCommands(sw);

    }

    private List<SpeakerData> buildTransitLoopCommands(
            Switch sw, FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation) {
        List<SpeakerData> result = new ArrayList<>();

        flowPath.getSegments().stream()
                .filter(segment -> segment.getDestSwitchId().equals(flow.getLoopSwitchId()))
                .findFirst()
                .map(PathSegment::getDestPort)
                .map(inPort -> flowRulesFactory.getTransitLoopRuleGenerator(flowPath, flow, encapsulation, inPort))
                .map(generator -> generator.generateCommands(sw))
                .map(result::addAll);
        return result;
    }

    private List<SpeakerData> generateRules(Switch sw, List<RuleGenerator> generators) {
        return generators.stream()
                .flatMap(generator -> generator.generateCommands(sw).stream())
                .collect(Collectors.toList());
    }

    private List<SpeakerData> buildYFlowRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        List<SpeakerData> result = new ArrayList<>();

        Set<YFlow> yFlows = new HashSet<>();
        Map<String, List<FlowPath>> yFlowIdsWithFlowPaths = new HashMap<>();
        for (FlowPath flowPath : adapter.getFlowPaths().values()) {
            YFlow yFlow = adapter.getYFlow(flowPath.getPathId());
            if (yFlow != null) {
                yFlows.add(yFlow);
                yFlowIdsWithFlowPaths.computeIfAbsent(yFlow.getYFlowId(), fp -> new ArrayList<>()).add(flowPath);
            }
        }

        yFlows.stream().filter(yFlow -> switchId.equals(yFlow.getSharedEndpoint().getSwitchId())
                        || switchId.equals(yFlow.getYPoint())
                        || switchId.equals(yFlow.getProtectedPathYPoint()))
                .forEach(yFlow ->
                        result.addAll(buildRulesForYFlow(yFlowIdsWithFlowPaths.get(yFlow.getYFlowId()), adapter)));

        return result;
    }

    @Override
    public List<SpeakerData> buildRulesForYFlow(List<FlowPath> flowPaths, DataAdapter adapter) {
        if (flowPaths == null) {
            return emptyList();
        }

        List<FlowPath> flowPathsForShared = new ArrayList<>();
        List<FlowPath> flowPathsForYPoint = new ArrayList<>();

        for (FlowPath flowPath : flowPaths) {
            YFlow yFlow = adapter.getYFlow(flowPath.getPathId());
            if (yFlow == null) {
                break;
            }
            SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();

            if (sharedSwitchId.equals(flowPath.getSrcSwitchId())) {
                if (!flowPath.isProtected()) {
                    flowPathsForShared.add(flowPath);
                } else {
                    log.trace("Skip a protected path {}, as y-flow rules are not required for it.",
                            flowPath.getPathId());
                }
            } else {
                flowPathsForYPoint.add(flowPath);
            }
        }

        List<SpeakerData> result = new ArrayList<>();
        if (!flowPathsForShared.isEmpty()) {
            result.addAll(buildSharedEndpointYFlowCommands(flowPathsForShared, adapter));
        }
        if (!flowPathsForYPoint.isEmpty()) {
            result.addAll(buildYPointYFlowCommands(flowPathsForYPoint, adapter));
        }
        return result;
    }

    private List<SpeakerData> buildSharedEndpointYFlowCommands(List<FlowPath> flowPaths, DataAdapter adapter) {
        if (flowPaths.isEmpty()) {
            return emptyList();
        }
        PathId firstPathId = flowPaths.get(0).getPathId();
        YFlow yFlow = adapter.getYFlow(firstPathId);
        if (yFlow == null) {
            throw new IllegalArgumentException(format("Corresponding y-flow can't be found for path %s", firstPathId));
        }
        SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();
        Switch sharedSwitch = adapter.getSwitch(sharedSwitchId);
        if (sharedSwitch == null) {
            log.debug("Skip commands for the y-flow {} as the shared endpoint {} can't be found",
                    yFlow.getYFlowId(), sharedSwitchId);
            return emptyList();
        }
        MeterId sharedMeterId = yFlow.getSharedEndpointMeterId();

        UUID externalMeterCommandUuid = null;
        JointRuleGeneratorBuilder builder = JointRuleGenerator.builder();

        for (FlowPath path : flowPaths) {
            if (path.isProtected() || !path.isForward()) {
                throw new IllegalArgumentException(
                        format("Shared endpoint rules must not be requested for protected or reverse path %s",
                                path.getPathId()));
            }
            Flow flow = adapter.getFlow(path.getPathId());
            if (flow == null) {
                throw new IllegalArgumentException(format("Corresponding flow for path %s can't be found",
                        path.getPathId()));
            }
            if (!yFlow.equals(adapter.getYFlow(path.getPathId()))) {
                throw new IllegalArgumentException(format("Paths %s and %s have different y-flow",
                        firstPathId, path.getPathId()));
            }
            if (!sharedSwitchId.equals(path.getSrcSwitchId())) {
                throw new IllegalArgumentException(
                        format("Requested path %s has the source different from the shared endpoint %s",
                                path.getPathId(), sharedSwitchId));
            }

            boolean meterToBeAdded = externalMeterCommandUuid == null;
            if (meterToBeAdded) {
                externalMeterCommandUuid = UUID.randomUUID();
            }
            FlowTransitEncapsulation encapsulation =
                    getFlowTransitEncapsulation(path.getPathId(), flow, adapter);
            builder.generator(flowRulesFactory.getIngressYRuleGenerator(path, flow, encapsulation,
                    new HashSet<>(), sharedMeterId, externalMeterCommandUuid, meterToBeAdded));
        }

        return builder.build().generateCommands(sharedSwitch);
    }

    private List<SpeakerData> buildYPointYFlowCommands(List<FlowPath> flowPaths, DataAdapter adapter) {
        if (flowPaths.isEmpty()) {
            return emptyList();
        }
        PathId firstPathId = flowPaths.get(0).getPathId();
        YFlow yFlow = adapter.getYFlow(firstPathId);
        if (yFlow == null) {
            throw new IllegalArgumentException(format("Corresponding y-flow can't be found for path %s", firstPathId));
        }
        SwitchId yPointSwitchId = yFlow.getYPoint();
        Switch yPointSwitch = adapter.getSwitch(yPointSwitchId);
        SwitchId protectedYPointSwitchId = yFlow.getProtectedPathYPoint();
        Switch protectedYPointSwitch = adapter.getSwitch(protectedYPointSwitchId);

        MeterId yPointMeterId = yFlow.getMeterId();
        MeterId protectedYPointMeterId = yFlow.getProtectedPathMeterId();

        UUID externalMeterCommandUuid = null;
        UUID externalProtectedMeterCommandUuid = null;
        JointRuleGeneratorBuilder builder = JointRuleGenerator.builder();
        JointRuleGeneratorBuilder protectedBuilder = JointRuleGenerator.builder();

        for (FlowPath path : flowPaths) {
            if (path.isForward()) {
                throw new IllegalArgumentException(
                        format("Y-point rules must not be requested for forward path %s", path.getPathId()));
            }
            Flow flow = adapter.getFlow(path.getPathId());
            if (flow == null) {
                throw new IllegalArgumentException(format("Corresponding flow for path %s can't be found",
                        path.getPathId()));
            }
            if (!yFlow.equals(adapter.getYFlow(path.getPathId()))) {
                throw new IllegalArgumentException(format("Paths %s and %s have different y-flow",
                        firstPathId, path.getPathId()));
            }
            FlowTransitEncapsulation encapsulation =
                    getFlowTransitEncapsulation(path.getPathId(), flow, adapter);

            if (path.isProtected()) {
                requireNonNull(protectedYPointSwitchId, "The y-flow protected path y-point can't be null");
                if (protectedYPointSwitch == null) {
                    log.trace("Skip commands for the sub-flow {} as the protected path y-point {} can't be found",
                            flow.getFlowId(), protectedYPointSwitchId);
                    continue;
                }
                if (!doesPathGoThroughSwitch(protectedYPointSwitchId, path.getSegments())) {
                    log.trace("Skip commands for the sub-flow {} as it doesn't go via the protected path y-point {}",
                            flow.getFlowId(), protectedYPointSwitchId);
                    continue;
                }
                requireNonNull(protectedYPointMeterId, "The y-flow protected path meterId can't be null");

                boolean meterToBeAdded = externalProtectedMeterCommandUuid == null;
                if (meterToBeAdded) {
                    externalProtectedMeterCommandUuid = UUID.randomUUID();
                }

                protectedBuilder.generator(buildYPointYRuleGenerator(protectedYPointSwitch, path, flow,
                        encapsulation, protectedYPointMeterId, externalProtectedMeterCommandUuid, meterToBeAdded));
            } else {
                requireNonNull(yPointSwitchId, "The y-flow y-point can't be null");
                if (yPointSwitch == null) {
                    log.trace("Skip commands for the sub-flow {} as the y-point {} can't be found",
                            flow.getFlowId(), yPointSwitchId);
                    continue;
                }
                if (!doesPathGoThroughSwitch(yPointSwitchId, path.getSegments())) {
                    log.trace("Skip commands for the sub-flow {} as it doesn't go via the protected path y-point {}",
                            flow.getFlowId(), yPointSwitchId);
                    continue;
                }
                requireNonNull(yPointMeterId, "The y-flow meterId can't be null");

                boolean meterToBeAdded = externalMeterCommandUuid == null;
                if (meterToBeAdded) {
                    externalMeterCommandUuid = UUID.randomUUID();
                }

                builder.generator(buildYPointYRuleGenerator(yPointSwitch, path, flow, encapsulation, yPointMeterId,
                        externalMeterCommandUuid, meterToBeAdded));
            }
        }

        List<SpeakerData> result = new ArrayList<>(builder.build().generateCommands(yPointSwitch));
        if (yFlow.isAllocateProtectedPath() && protectedYPointSwitch != null) {
            result.addAll(protectedBuilder.build().generateCommands(protectedYPointSwitch));
        }
        return result;
    }

    private boolean doesPathGoThroughSwitch(SwitchId switchId,
                                            List<PathSegment> pathSegments) {
        return pathSegments.stream().anyMatch(segment ->
                segment.getSrcSwitchId().equals(switchId) || segment.getDestSwitchId().equals(switchId));
    }

    private RuleGenerator buildYPointYRuleGenerator(Switch yPointSwitch, FlowPath path,
                                                    Flow flow, FlowTransitEncapsulation encapsulation,
                                                    MeterId yPointMeterId, UUID externalMeterCommandUuid,
                                                    boolean meterToBeAdded) {
        if (path.isForward() || !path.getSrcSwitchId().equals(flow.getDestSwitchId())) {
            throw new IllegalArgumentException(
                    format("Y-point rules must not be requested for forward path %s", path.getPathId()));
        }
        if (path.getSegments().isEmpty()) {
            throw new IllegalArgumentException(
                    format("Transit rules must not be requested for a path %s with no segments", path.getPathId()));
        }

        SwitchId yPointSwitchId = yPointSwitch.getSwitchId();
        if (yPointSwitchId.equals(flow.getSrcSwitchId())) {
            return flowRulesFactory.getEgressYRuleGenerator(path, flow,
                    encapsulation, yPointMeterId, externalMeterCommandUuid, meterToBeAdded);
        } else if (yPointSwitchId.equals(flow.getDestSwitchId())) {
            return flowRulesFactory.getIngressYRuleGenerator(path, flow,
                    encapsulation, emptySet(), yPointMeterId, externalMeterCommandUuid, meterToBeAdded);
        } else {
            SwitchPathSegments switchPathSegments = findPathSegmentsForSwitch(yPointSwitchId, path.getSegments());
            if (switchPathSegments == null) {
                throw new IllegalArgumentException(
                        format("The segment for endpoint %s can't be found among %s, %s / %s", yPointSwitchId,
                                path.getSegments().size(), flow.getSrcSwitchId(), flow.getDestSwitchId()));
            }
            return flowRulesFactory.getTransitYRuleGenerator(path, encapsulation,
                    switchPathSegments.getFirstPathSegment(), switchPathSegments.getSecondPathSegment(),
                    yPointMeterId, externalMeterCommandUuid, meterToBeAdded);
        }
    }

    private SwitchPathSegments findPathSegmentsForSwitch(SwitchId switchId, List<PathSegment> pathSegments) {
        SwitchPathSegments result = null;
        for (int i = 1; i < pathSegments.size(); i++) {
            PathSegment firstSegment = pathSegments.get(i - 1);
            PathSegment secondSegment = pathSegments.get(i);
            if (switchId.equals(firstSegment.getDestSwitchId())
                    && switchId.equals(secondSegment.getSrcSwitchId())) {
                result = new SwitchPathSegments(firstSegment, secondSegment);
                break;
            }
        }
        return result;
    }

    private FlowTransitEncapsulation getFlowTransitEncapsulation(PathId pathId, Flow flow, DataAdapter adapter) {
        PathId oppositePathId = flow.getOppositePathId(pathId).orElse(null);
        return adapter.getTransitEncapsulation(pathId, oppositePathId);
    }

    @Data
    @AllArgsConstructor
    private static class SwitchPathSegments {
        PathSegment firstPathSegment;
        PathSegment secondPathSegment;
    }
}

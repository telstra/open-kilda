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
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(flowPath.getPathId());

        if (!flow.isProtectedPath(flowPath.getPathId())) {
            Set<FlowSideAdapter> overlappingAdapters = new HashSet<>();
            if (filterOutUsedSharedRules) {
                overlappingAdapters = getOverlappingMultiTableIngressAdapters(flowPath, adapter);
            }
            buildIngressCommands(
                    adapter.getSwitch(flowPath.getSrcSwitchId()), flowPath, flow, encapsulation, overlappingAdapters);
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
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(flowPath.getPathId());

        if (switchId.equals(flowPath.getSrcSwitchId()) && !flow.isProtectedPath(flowPath.getPathId())) {
            // TODO filter out equal shared rules from the result list
            result.addAll(buildIngressCommands(sw, flowPath, flow, encapsulation, new HashSet<>()));
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

            if (flow.isLooped() && sw.getSwitchId().equals(flow.getLoopSwitchId())) {
                result.addAll(buildTransitLoopCommands(sw, flowPath, flow, encapsulation));
            }
        }

        return result;
    }

    private List<SpeakerData> buildIngressCommands(Switch sw, FlowPath flowPath, Flow flow,
                                                   FlowTransitEncapsulation encapsulation,
                                                   Set<FlowSideAdapter> overlappingIngressAdapters) {
        List<SpeakerData> ingressCommands = flowRulesFactory.getIngressRuleGenerator(
                flowPath, flow, encapsulation, overlappingIngressAdapters).generateCommands(sw);
        UUID ingressMeterCommandUuid = Utils.getCommand(MeterSpeakerData.class, ingressCommands)
                .map(SpeakerData::getUuid).orElse(null);

        List<RuleGenerator> generators = new ArrayList<>();
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
            return Collections.emptyList();
        }

        FlowPath flowPathForIngress = null;
        FlowPath altFlowPathForIngress = null;
        FlowPath flowPathForTransit = null;
        FlowPath altFlowPathForTransit = null;

        for (FlowPath flowPath : flowPaths) {
            YFlow yFlow = adapter.getYFlow(flowPath.getPathId());
            if (yFlow == null) {
                break;
            }
            SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();

            if (sharedSwitchId.equals(flowPath.getSrcSwitchId())) {
                if (flowPathForIngress == null) {
                    flowPathForIngress = flowPath;
                } else if (altFlowPathForIngress == null) {
                    altFlowPathForIngress = flowPath;
                }
            } else {
                if (flowPathForTransit == null) {
                    flowPathForTransit = flowPath;
                } else if (altFlowPathForTransit == null) {
                    altFlowPathForTransit = flowPath;
                }
            }
        }

        if (flowPathForIngress == null || altFlowPathForIngress == null
                || flowPathForTransit == null || altFlowPathForTransit == null) {
            return Collections.emptyList();
        }

        List<SpeakerData> result =
                new ArrayList<>(buildIngressYFlowCommands(flowPathForIngress, altFlowPathForIngress, adapter));
        result.addAll(buildTransitYFlowCommands(flowPathForTransit, altFlowPathForTransit, adapter));

        return result;
    }

    private List<SpeakerData> buildIngressYFlowCommands(FlowPath flowPath, FlowPath altFlowPath,
                                                        DataAdapter adapter) {

        Flow flow = adapter.getFlow(flowPath.getPathId());
        Flow altFlow = adapter.getFlow(altFlowPath.getPathId());

        if (flow.isProtectedPath(flowPath.getPathId()) || altFlow.isProtectedPath(altFlowPath.getPathId())) {
            return Collections.emptyList();
        }

        YFlow yFlow = adapter.getYFlow(flowPath.getPathId());
        if (yFlow == null) {
            return Collections.emptyList();
        }
        SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();
        Switch sharedSwitch = adapter.getSwitch(sharedSwitchId);

        if (sharedSwitch == null
                || !sharedSwitchId.equals(flowPath.getSrcSwitchId())
                || !sharedSwitchId.equals(altFlowPath.getSrcSwitchId())) {
            return Collections.emptyList();
        }

        FlowTransitEncapsulation encapsulation =
                getFlowTransitEncapsulation(flowPath.getPathId(), flow, adapter);
        FlowTransitEncapsulation altEncapsulation =
                getFlowTransitEncapsulation(altFlowPath.getPathId(), altFlow, adapter);


        MeterId sharedMeterId = yFlow.getSharedEndpointMeterId();

        RuleGenerator generator = flowRulesFactory.getIngressYRuleGenerator(flowPath, flow, encapsulation,
                new HashSet<>(), altFlowPath, altFlow, altEncapsulation, new HashSet<>(), sharedMeterId);

        return generator.generateCommands(sharedSwitch);
    }

    private List<SpeakerData> buildTransitYFlowCommands(FlowPath flowPath, FlowPath altFlowPath,
                                                        DataAdapter adapter) {

        Flow flow = adapter.getFlow(flowPath.getPathId());
        Flow altFlow = adapter.getFlow(altFlowPath.getPathId());

        YFlow yFlow = adapter.getYFlow(flowPath.getPathId());
        if (yFlow == null) {
            return Collections.emptyList();
        }
        SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();

        if (sharedSwitchId.equals(flowPath.getSrcSwitchId()) || sharedSwitchId.equals(altFlowPath.getSrcSwitchId())) {
            return Collections.emptyList();
        }

        SwitchId yPointSwitchId = yFlow.getYPoint();
        MeterId yPointMeterId = yFlow.getMeterId();
        if (flow.isProtectedPath(flowPath.getPathId()) && altFlow.isProtectedPath(altFlowPath.getPathId())) {
            yPointSwitchId = yFlow.getProtectedPathYPoint();
            yPointMeterId = yFlow.getProtectedPathMeterId();
        }

        Switch yPointSwitch = adapter.getSwitch(yPointSwitchId);
        if (yPointSwitch == null) {
            return Collections.emptyList();
        }

        FlowTransitEncapsulation encapsulation =
                getFlowTransitEncapsulation(flowPath.getPathId(), flow, adapter);
        FlowTransitEncapsulation altEncapsulation =
                getFlowTransitEncapsulation(altFlowPath.getPathId(), altFlow, adapter);

        SwitchPathSegments switchPathSegments =
                findPathSegmentsForSwitch(yPointSwitchId, flowPath.getSegments());
        SwitchPathSegments altSwitchPathSegments =
                findPathSegmentsForSwitch(yPointSwitchId, altFlowPath.getSegments());

        if (switchPathSegments == null || altSwitchPathSegments == null) {
            return Collections.emptyList();
        }

        RuleGenerator generator = flowRulesFactory.getTransitYRuleGenerator(flowPath, encapsulation,
                switchPathSegments.getFirstPathSegment(), switchPathSegments.getSecondPathSegment(),
                altFlowPath, altEncapsulation, altSwitchPathSegments.getFirstPathSegment(),
                altSwitchPathSegments.getSecondPathSegment(), yPointMeterId);

        return generator.generateCommands(yPointSwitch);
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
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(pathId);
        if (encapsulation == null) {
            Optional<PathId> oppositePathId = flow.getOppositePathId(pathId);
            if (oppositePathId.isPresent()) {
                encapsulation = adapter.getTransitEncapsulation(oppositePathId.get());
            }
        }
        return encapsulation;
    }

    @Data
    @AllArgsConstructor
    private static class SwitchPathSegments {
        PathSegment firstPathSegment;
        PathSegment secondPathSegment;
    }
}

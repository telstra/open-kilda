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
import static org.openkilda.rulemanager.utils.Utils.getShortestSubPath;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.LagLogicalPort;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LazyMap;

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

        if (flowPath.isOneSwitchPath()) {
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
        Flow flow = adapter.getFlow(path.getPathId());
        FlowEndpoint endpoint = makeIngressAdapter(flow, path).getEndpoint();
        Set<PathId> excludePathIds = Sets.newHashSet(
                path.getPathId(), flow.getForwardPathId(), flow.getReversePathId());
        return getOverlappingMultiTableIngressAdapters(endpoint, path.isSrcWithMultiTable(), excludePathIds, adapter);
    }

    private Set<FlowSideAdapter> getOverlappingMultiTableIngressAdapters(
            HaFlow haFlow, FlowPath subPath, DataAdapter adapter) {
        FlowEndpoint endpoint = makeIngressAdapter(haFlow, subPath).getEndpoint();
        Set<PathId> excludePathIds = Sets.newHashSet(subPath.getPathId());
        haFlow.getForwardPath().getSubPaths().forEach(path -> excludePathIds.add(path.getPathId()));
        haFlow.getReversePath().getSubPaths().forEach(path -> excludePathIds.add(path.getPathId()));
        return getOverlappingMultiTableIngressAdapters(endpoint, true, excludePathIds, adapter);
    }

    private Set<FlowSideAdapter> getOverlappingMultiTableIngressAdapters(
            FlowEndpoint endpoint, boolean multiTable, Set<PathId> excludePathIds, DataAdapter adapter) {

        Set<FlowSideAdapter> result = new HashSet<>();
        if (!multiTable) {
            // we do not care about overlapping for single table paths
            return result;
        }

        for (FlowPath overlappingPath : adapter.getCommonFlowPaths().values()) {
            if (overlappingPath.isSrcWithMultiTable()
                    && !excludePathIds.contains(overlappingPath.getPathId())
                    && endpoint.getSwitchId().equals(overlappingPath.getSrcSwitchId())) {
                Flow overlappingFlow = adapter.getFlow(overlappingPath.getPathId());
                FlowSideAdapter flowAdapter = makeIngressAdapter(overlappingFlow, overlappingPath);
                if (endpoint.getPortNumber().equals(flowAdapter.getEndpoint().getPortNumber())) {
                    result.add(flowAdapter);
                }
            }
        }
        for (FlowPath overlappingHaSubPath : adapter.getHaFlowSubPaths().values()) {
            if (endpoint.getSwitchId().equals(overlappingHaSubPath.getSrcSwitchId())
                    && !excludePathIds.contains(overlappingHaSubPath.getPathId())) {
                HaFlow overlappingHaFlow = adapter.getHaFlow(overlappingHaSubPath.getPathId());
                FlowSideAdapter haFlowAdapter = makeIngressAdapter(overlappingHaFlow, overlappingHaSubPath);
                if (endpoint.getPortNumber().equals(haFlowAdapter.getEndpoint().getPortNumber())) {
                    result.add(haFlowAdapter);
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

            islPorts.forEach(islPort -> generators.addAll(getIslServiceRuleGenerators(islPort)));
        }

        List<LagLogicalPort> lacpPorts = adapter.getLagLogicalPorts(switchId)
                .stream().filter(LagLogicalPort::isLacpReply).collect(toList());

        if (!lacpPorts.isEmpty()) {
            generators.add(serviceRulesFactory.getDropSlowProtocolsLoopRuleGenerator());

            // we need to create only one meter for all Lacp reply rules. So first generator will receive parameter
            // switchHasOtherLacpPackets = false
            generators.add(serviceRulesFactory.getLacpReplyRuleGenerator(
                    lacpPorts.get(0).getLogicalPortNumber(), false));
            for (int i = 1; i < lacpPorts.size(); i++) {
                generators.add(serviceRulesFactory.getLacpReplyRuleGenerator(
                        lacpPorts.get(i).getLogicalPortNumber(), true));
            }
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

    private List<RuleGenerator> getIslServiceRuleGenerators(int port) {
        List<RuleGenerator> result = new ArrayList<>();
        result.add(serviceRulesFactory.getEgressIslVxlanRuleGenerator(port));
        result.add(serviceRulesFactory.getEgressIslVlanRuleGenerator(port));
        result.add(serviceRulesFactory.getTransitIslVxlanRuleGenerator(port));
        return result;
    }

    private List<SpeakerData> buildFlowRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        List<SpeakerData> result = adapter.getCommonFlowPaths().values().stream()
                .flatMap(flowPath -> buildFlowRulesForSwitch(switchId, flowPath, adapter).stream())
                .collect(Collectors.toList());

        result.addAll(buildYFlowRulesForSwitch(switchId, adapter));
        result.addAll(buildHaFlowRulesForSwitch(switchId, adapter));
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

        if (!flowPath.isOneSwitchPath()) {
            if (switchId.equals(flowPath.getDestSwitchId())) {
                result.addAll(buildEgressCommands(sw, flowPath, flow, encapsulation));
            }
            for (int i = 1; i < flowPath.getSegments().size(); i++) {
                PathSegment firstSegment = flowPath.getSegments().get(i - 1);
                PathSegment secondSegment = flowPath.getSegments().get(i);
                if (isTargetSegments(switchId, firstSegment, secondSegment)) {
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
                    switchProperties, overlappingIngressAdapters));
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
        generators.add(flowRulesFactory.getVlanStatsRuleGenerator(flowPath, flow));

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
        for (FlowPath flowPath : adapter.getCommonFlowPaths().values()) {
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

    private List<SpeakerData> buildHaFlowRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        List<SpeakerData> result = new ArrayList<>();
        Map<PathId, UUID> sharedMeterMap = LazyMap.lazyMap(new HashMap<>(), UUID::randomUUID);

        for (FlowPath haSubPath : adapter.getHaFlowSubPaths().values()) {
            result.addAll(buildHaSubPathRulesForSwitch(switchId, haSubPath, sharedMeterMap, adapter));
        }
        return result;
    }

    private List<SpeakerData> buildHaSubPathRulesForSwitch(
            SwitchId switchId, FlowPath subPath, Map<PathId, UUID> sharedMeterMap, DataAdapter adapter) {
        List<SpeakerData> result = new ArrayList<>();
        if (subPath.getHaFlowPathId() == null) {
            // Just log the error to do not fail whole operation.
            log.error("Flow sub path {} has no ha-flow path", subPath);
            return result;
        }

        HaFlow haFlow = adapter.getHaFlow(subPath.getPathId());
        HaFlowPath haPath = subPath.getHaFlowPath();
        SwitchId yPointSwitchId = haPath.getYPointSwitchId();
        PathId oppositePathId = haFlow.getOppositePathId(haPath.getHaPathId()).orElse(null);
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(haPath.getHaPathId(), oppositePathId);

        // ingress
        if (!haFlow.isProtectedPath(haPath.getHaPathId())) {
            if (switchId.equals(FlowSideAdapter.makeIngressAdapter(haFlow, subPath).getEndpoint().getSwitchId())) {
                if (subPath.isForward()) {
                    result.addAll(buildForwardIngressHaRules(haFlow, haPath, encapsulation, false, adapter));
                } else {
                    MeterId meterId;
                    UUID sharedMeterUuid = null;
                    if (subPath.getSrcSwitchId().equals(yPointSwitchId)) {
                        meterId = haPath.getYPointMeterId();
                        sharedMeterUuid = sharedMeterMap.get(haPath.getHaPathId());
                    } else {
                        meterId = subPath.getMeterId();
                    }
                    result.addAll(buildHaIngressRules(haFlow, subPath, encapsulation, false, meterId, new HashSet<>(),
                            sharedMeterUuid, true, adapter));
                }
            }
        }

        if (subPath.isOneSwitchPath()) {
            return result;
        }

        boolean yPointIsPassed = subPath.getSrcSwitchId().equals(yPointSwitchId);

        // transit
        for (int i = 1; i < subPath.getSegments().size(); i++) {
            PathSegment firstSegment = subPath.getSegments().get(i - 1);
            PathSegment secondSegment = subPath.getSegments().get(i);
            if (isTargetSegments(switchId, firstSegment, secondSegment)) {
                if (switchId.equals(yPointSwitchId)) {
                    if (subPath.isForward()) {
                        result.addAll(buildYPointForwardTransitHaRules(
                                haPath, encapsulation, i - 1, haPath.getSubPaths(),
                                firstSegment.getDestPort(), adapter));

                    } else {
                        result.addAll(buildTransitHaRules(
                                subPath, firstSegment, secondSegment, encapsulation, haPath.getYPointMeterId(),
                                sharedMeterMap.get(haPath.getHaPathId()), true, yPointIsPassed, adapter));
                    }
                } else {
                    boolean sharedSegment = subPath.isForward() != yPointIsPassed;
                    result.addAll(buildTransitHaRules(subPath, firstSegment, secondSegment, encapsulation, null,
                            null, false, sharedSegment, adapter));
                }
                break;
            }
            if (firstSegment.getDestSwitchId().equals(yPointSwitchId)) {
                yPointIsPassed = true;
            }
        }

        // egress
        if (switchId.equals(FlowSideAdapter.makeEgressAdapter(haFlow, subPath).getEndpoint().getSwitchId())) {
            if (subPath.isForward()) {
                if (subPath.getDestSwitchId().equals(yPointSwitchId)) {
                    result.addAll(buildForwardYPointEgressOrTransitHaRules(
                            haPath, encapsulation, subPath.getSegments().size() - 1, adapter));
                } else {
                    result.addAll(buildHaEgressRules(
                            haFlow, subPath, encapsulation, false, null, null, false, adapter));
                }
            } else {
                if (subPath.getDestSwitchId().equals(haPath.getYPointSwitchId())) {
                    result.addAll(buildHaEgressRules(haFlow, subPath, encapsulation, false, haPath.getYPointMeterId(),
                            sharedMeterMap.get(haPath.getHaPathId()), true, adapter));
                } else {
                    result.addAll(buildHaEgressRules(
                            haFlow, subPath, encapsulation, true, null, null, false, adapter));
                }
            }
        }

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
            if (flowPath.isForward()) {
                SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();
                if (!sharedSwitchId.equals(flowPath.getSrcSwitchId())) {
                    throw new IllegalArgumentException(
                            format("Provided forward path %s has the source which differs from the shared endpoint %s",
                                    flowPath.getPathId(), sharedSwitchId));
                }
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

    @Override
    public List<SpeakerData> buildIslServiceRules(SwitchId switchId, int port, DataAdapter adapter) {
        SwitchProperties switchProperties = adapter.getSwitchProperties(switchId);
        if (!switchProperties.isMultiTable()) {
            return emptyList();
        }
        Switch sw = adapter.getSwitch(switchId);
        List<RuleGenerator> generators = getIslServiceRuleGenerators(port);
        if (adapter.getFeatureToggles().getServer42IslRtt() && switchProperties.hasServer42IslRttEnabled()) {
            generators.add(serviceRulesFactory
                    .getServer42IslRttInputRuleGenerator(switchProperties.getServer42Port(), port));
        }
        return generateRules(sw, generators);
    }

    @Override
    public List<SpeakerData> buildLacpRules(SwitchId switchId, int logicalPort, DataAdapter adapter) {
        boolean switchHasOtherLacpPorts = adapter.getLagLogicalPorts(switchId).stream()
                .filter(LagLogicalPort::isLacpReply)
                .anyMatch(port -> port.getLogicalPortNumber() != logicalPort);

        List<RuleGenerator> generators = Lists.newArrayList(
                serviceRulesFactory.getLacpReplyRuleGenerator(logicalPort, switchHasOtherLacpPorts));
        if (!switchHasOtherLacpPorts) {
            generators.add(serviceRulesFactory.getDropSlowProtocolsLoopRuleGenerator());
        }

        Switch sw = adapter.getSwitch(switchId);
        return postProcessCommands(generateRules(sw, generators));
    }

    @Override
    public List<SpeakerData> buildMirrorPointRules(FlowMirrorPoints mirrorPoints, DataAdapter adapter) {
        FlowPath flowPath = adapter.getCommonFlowPaths().get(mirrorPoints.getFlowPathId());
        Flow flow = adapter.getFlow(flowPath.getPathId());
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(
                flowPath.getPathId(), flow.getOppositePathId(flowPath.getPathId()).orElse(null));
        List<SpeakerData> result = new ArrayList<>();

        if (mirrorPoints.getMirrorSwitchId().equals(flowPath.getSrcSwitchId())) {
            result.addAll(flowRulesFactory.getIngressMirrorRuleGenerator(flowPath, flow, encapsulation, null)
                    .generateCommands(adapter.getSwitch(mirrorPoints.getMirrorSwitchId())));
        } else if (mirrorPoints.getMirrorSwitchId().equals(flowPath.getDestSwitchId())) {
            result.addAll(flowRulesFactory.getEgressMirrorRuleGenerator(flowPath, flow, encapsulation)
                    .generateCommands(adapter.getSwitch(mirrorPoints.getMirrorSwitchId())));

        }
        return result;
    }

    @Override
    public List<SpeakerData> buildRulesHaFlowPath(
            HaFlowPath haPath, boolean filterOutUsedSharedRules, DataAdapter adapter) {

        if (haPath.getSubPaths().size() != 2) {
            throw new IllegalArgumentException(
                    format("HaPath %s must contain 2 sub paths, but found %d", haPath, haPath.getSubPaths().size()));
        }

        HaFlow haFlow = adapter.getHaFlow(haPath.getSubPaths().iterator().next().getPathId());
        PathId oppositePathId = haFlow.getOppositePathId(haPath.getHaPathId()).orElse(null);
        FlowTransitEncapsulation encapsulation = adapter.getTransitEncapsulation(haPath.getHaPathId(), oppositePathId);

        if (haPath.isForward()) {
            return buildForwardHaRules(haPath, filterOutUsedSharedRules, adapter, haFlow, encapsulation);
        } else {
            return buildReverseHaRules(haPath, filterOutUsedSharedRules, adapter, haFlow, encapsulation);
        }
    }

    private List<SpeakerData> buildForwardHaRules(
            HaFlowPath haPath, boolean filterOutUsedSharedRules, DataAdapter adapter, HaFlow haFlow,
            FlowTransitEncapsulation encapsulation) {
        List<SpeakerData> rules = new ArrayList<>();
        if (!haFlow.isProtectedPath(haPath.getHaPathId())) {
            rules.addAll(buildForwardIngressHaRules(
                    haFlow, haPath, encapsulation, filterOutUsedSharedRules, adapter));
        }

        if (!haPath.getSharedSwitchId().equals(haPath.getYPointSwitchId())) {
            rules.addAll(buildForwardSharedHaRules(haPath, adapter, encapsulation));
        }

        for (FlowPath subPath : haPath.getSubPaths()) {
            rules.addAll(buildForwardHaNotSharedTransitRules(
                    subPath, haPath.getYPointSwitchId(), encapsulation, adapter));
            if (!subPath.getDestSwitchId().equals(haPath.getYPointSwitchId())) {
                rules.addAll(buildHaEgressRules(haFlow, subPath, encapsulation, false, null, null, false, adapter));
            }
        }
        return rules;
    }

    private List<SpeakerData> buildReverseHaRules(
            HaFlowPath haPath, boolean filterOutUsedSharedRules, DataAdapter adapter, HaFlow haFlow,
            FlowTransitEncapsulation encapsulation) {
        List<SpeakerData> rules = new ArrayList<>();
        UUID yPointMeterCommandUuid = UUID.randomUUID();
        boolean sharedMeterWasCreated = false;

        for (FlowPath subPath : haPath.getSubPaths()) {
            Set<FlowSideAdapter> overlappingAdapters = new HashSet<>();
            if (filterOutUsedSharedRules) {
                overlappingAdapters.addAll(getOverlappingMultiTableIngressAdapters(
                        haFlow, haPath.getSubPaths().get(0), adapter));
            }

            // reverse ingress rule
            if (!haFlow.isProtectedPath(haPath.getHaPathId())) {
                if (subPath.getSrcSwitchId().equals(haPath.getYPointSwitchId())) {
                    rules.addAll(buildHaIngressRules(haFlow, subPath, encapsulation, false, haPath.getYPointMeterId(),
                            overlappingAdapters, yPointMeterCommandUuid, !sharedMeterWasCreated, adapter));
                    sharedMeterWasCreated = true;
                } else {
                    rules.addAll(buildHaIngressRules(haFlow, subPath, encapsulation, false, subPath.getMeterId(),
                            overlappingAdapters, null, true, adapter));
                }
            }

            if (subPath.isOneSwitchPath()) {
                continue; // one switch path has only ingress rules
            }

            boolean yPointIsPassed = subPath.getSrcSwitchId().equals(haPath.getYPointSwitchId());

            // reverse transit rule
            for (int i = 0; i + 1 < subPath.getSegments().size(); i++) {
                PathSegment firstSegment = subPath.getSegments().get(i);
                PathSegment secondSegment = subPath.getSegments().get(i + 1);

                if (firstSegment.getDestSwitchId().equals(haPath.getYPointSwitchId())) {
                    rules.addAll(buildTransitHaRules(
                            subPath, firstSegment, secondSegment, encapsulation, haPath.getYPointMeterId(),
                            yPointMeterCommandUuid, !sharedMeterWasCreated, yPointIsPassed, adapter));
                    yPointIsPassed = true;
                    sharedMeterWasCreated = true;
                } else {
                    rules.addAll(buildTransitHaRules(subPath, firstSegment, secondSegment, encapsulation, null,
                            null, false, yPointIsPassed, adapter));
                }
            }

            // reverse egress rule
            if (subPath.getDestSwitchId().equals(haPath.getYPointSwitchId())) {
                rules.addAll(buildHaEgressRules(haFlow, subPath, encapsulation, false, haPath.getYPointMeterId(),
                        yPointMeterCommandUuid, !sharedMeterWasCreated, adapter));
                sharedMeterWasCreated = true;
            } else {
                rules.addAll(buildHaEgressRules(haFlow, subPath, encapsulation, true, null, null, false, adapter));
            }
        }
        return rules;
    }

    private List<SpeakerData> buildTransitHaRules(
            FlowPath subPath, PathSegment firstSegment, PathSegment secondSegment,
            FlowTransitEncapsulation encapsulation, MeterId meterId, UUID sharedMeterCommandUuid, boolean createMeter,
            boolean sharedSegment, DataAdapter adapter) {
        return flowRulesFactory.getTransitHaRuleGenerator(
                        subPath, encapsulation, firstSegment, secondSegment, sharedSegment, meterId,
                        sharedMeterCommandUuid, createMeter)
                .generateCommands(adapter.getSwitch(firstSegment.getDestSwitchId()));
    }

    /**
     * Builds shared rules for forward ha-flow path.
     * Shared rules are rules for common transit switches and Y-point switch.
     */
    private List<SpeakerData> buildForwardSharedHaRules(
            HaFlowPath haPath, DataAdapter adapter, FlowTransitEncapsulation encapsulation) {
        List<SpeakerData> rules = new ArrayList<>();
        FlowPath shortestSubPath = getShortestSubPath(haPath.getSubPaths());
        int lastCommonSegmentId = 0;
        for (; lastCommonSegmentId + 1 < shortestSubPath.getSegments().size(); lastCommonSegmentId++) {
            PathSegment firstSegment = shortestSubPath.getSegments().get(lastCommonSegmentId);
            PathSegment secondSegment = shortestSubPath.getSegments().get(lastCommonSegmentId + 1);
            rules.addAll(buildTransitHaRules(shortestSubPath, firstSegment, secondSegment, encapsulation, null,
                    null, false, true, adapter));

            if (secondSegment.getDestSwitchId().equals(haPath.getYPointSwitchId())) {
                lastCommonSegmentId++;
                break; // end of common transit path
            }
        }

        rules.addAll(buildForwardYPointEgressOrTransitHaRules(haPath, encapsulation, lastCommonSegmentId, adapter));
        return rules;
    }

    private List<SpeakerData> buildForwardYPointEgressOrTransitHaRules(
            HaFlowPath haPath, FlowTransitEncapsulation encapsulation, int lastCommonSegmentId, DataAdapter adapter) {
        List<FlowPath> subPaths = haPath.getSubPaths();
        int yPointInPort = getShortestSubPath(subPaths).getSegments().get(lastCommonSegmentId).getDestPort();

        if (subPaths.get(0).getDestSwitchId().equals(subPaths.get(1).getDestSwitchId())
                && subPaths.get(0).getDestSwitchId().equals(haPath.getYPointSwitchId())) {
            RuleGenerator generator = flowRulesFactory.getYPointForwardEgressHaRuleGenerator(
                    haPath, subPaths, encapsulation, yPointInPort);
            return generator.generateCommands(adapter.getSwitch(haPath.getYPointSwitchId()));
        } else {
            return buildYPointForwardTransitHaRules(
                    haPath, encapsulation, lastCommonSegmentId, subPaths, yPointInPort, adapter);
        }
    }

    private List<SpeakerData> buildYPointForwardTransitHaRules(
            HaFlowPath haPath, FlowTransitEncapsulation encapsulation, int lastCommonSegmentId, List<FlowPath> subPaths,
            int yPointInPort, DataAdapter adapter) {
        Map<PathId, Integer> outPorts = getHaYPointOutPorts(lastCommonSegmentId, subPaths);
        return flowRulesFactory.getYPointForwardTransitHaRuleGenerator(
                        haPath, subPaths, encapsulation, yPointInPort, outPorts)
                .generateCommands(adapter.getSwitch(haPath.getYPointSwitchId()));
    }

    private List<SpeakerData> buildForwardIngressHaRules(
            HaFlow haFlow, HaFlowPath haPath, FlowTransitEncapsulation encapsulation, boolean filterOutUsedSharedRules,
            DataAdapter adapter) {
        Set<FlowSideAdapter> overlappingAdapters = new HashSet<>();
        if (filterOutUsedSharedRules) {
            overlappingAdapters.addAll(getOverlappingMultiTableIngressAdapters(
                    haFlow, haPath.getSubPaths().get(0), adapter));
        }

        if (haPath.getSharedSwitchId().equals(haPath.getYPointSwitchId())) {
            RuleGenerator generator = flowRulesFactory.getYPointForwardIngressHaRuleGenerator(
                    haFlow, haPath, haPath.getSubPaths(), encapsulation, overlappingAdapters);
            return generator.generateCommands(adapter.getSwitch(haPath.getSharedSwitchId()));

        } else {
            FlowPath randomSubPath = haPath.getSubPaths().get(0);
            return buildHaIngressRules(haFlow, randomSubPath, encapsulation, true,
                    haPath.getSharedPointMeterId(), overlappingAdapters, null, true, adapter);
        }
    }

    private List<SpeakerData> buildHaIngressRules(
            HaFlow haFlow, FlowPath subPath, FlowTransitEncapsulation encapsulation, boolean sharedPath,
            MeterId meterId, Set<FlowSideAdapter> overlappingAdapters, UUID externalMeterCommandUuid,
            boolean generateMeterCommand, DataAdapter adapter) {
        return flowRulesFactory.getIngressHaRuleGenerator(
                        haFlow, subPath, meterId, encapsulation, sharedPath, overlappingAdapters,
                        externalMeterCommandUuid, generateMeterCommand)
                .generateCommands(adapter.getSwitch(subPath.getSrcSwitchId()));
    }

    private List<SpeakerData> buildHaEgressRules(
            HaFlow haFlow, FlowPath subPath, FlowTransitEncapsulation encapsulation, boolean sharedPath,
            MeterId meterId, UUID externalMeterCommandUuid,
            boolean generateMeterCommand, DataAdapter adapter) {
        return flowRulesFactory.getEgressHaRuleGenerator(haFlow, subPath, encapsulation,
                        sharedPath, meterId, externalMeterCommandUuid, generateMeterCommand)
                .generateCommands(adapter.getSwitch(subPath.getDestSwitchId()));
    }

    private Map<PathId, Integer> getHaYPointOutPorts(int lastCommonSegmentId, List<FlowPath> subPaths) {
        Map<PathId, Integer> result = new HashMap<>();
        for (FlowPath subPath : subPaths) {
            if (lastCommonSegmentId + 1 >= subPath.getSegments().size()) {
                result.put(subPath.getPathId(), subPath.getHaSubFlow().getEndpointPort());
            } else {
                result.put(subPath.getPathId(), subPath.getSegments().get(lastCommonSegmentId + 1).getSrcPort());
            }
        }
        return result;
    }

    private List<SpeakerData> buildForwardHaNotSharedTransitRules(
            FlowPath subPath, SwitchId yPointSwitchId, FlowTransitEncapsulation encapsulation, DataAdapter adapter) {

        List<PathSegment> segments = subPath.getSegments();
        int lastCommonSegmentId = segments.size() - 1;
        while (lastCommonSegmentId >= 0
                && !segments.get(lastCommonSegmentId).getDestSwitchId().equals(yPointSwitchId)) {
            lastCommonSegmentId--;
        }

        List<SpeakerData> rules = new ArrayList<>();

        for (int i = lastCommonSegmentId + 1; i + 1 < subPath.getSegments().size(); i++) {
            PathSegment firstSegment = subPath.getSegments().get(i);
            PathSegment secondSegment = subPath.getSegments().get(i + 1);
            rules.addAll(buildTransitHaRules(subPath, firstSegment, secondSegment, encapsulation, null, null, false,
                    false, adapter));
        }
        return rules;
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
                if (!doesPathGoThroughSwitch(protectedYPointSwitchId, path)) {
                    log.trace("Skip commands for the sub-flow {} as it doesn't go via the protected path y-point {}",
                            flow.getFlowId(), protectedYPointSwitchId);
                    continue;
                }

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
                if (!doesPathGoThroughSwitch(yPointSwitchId, path)) {
                    log.trace("Skip commands for the sub-flow {} as it doesn't go via the path y-point {}",
                            flow.getFlowId(), yPointSwitchId);
                    continue;
                }

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
                                            FlowPath path) {
        return path.getSrcSwitchId().equals(switchId) || path.getDestSwitchId().equals(switchId)
                || path.getSegments().stream().anyMatch(segment ->
                segment.getSrcSwitchId().equals(switchId) || segment.getDestSwitchId().equals(switchId));
    }

    @VisibleForTesting
    private RuleGenerator buildYPointYRuleGenerator(Switch yPointSwitch, FlowPath path,
                                                    Flow flow, FlowTransitEncapsulation encapsulation,
                                                    MeterId yPointMeterId, UUID externalMeterCommandUuid,
                                                    boolean meterToBeAdded) {
        if (path.isForward() || !path.getSrcSwitchId().equals(flow.getDestSwitchId())) {
            throw new IllegalArgumentException(
                    format("Y-point rules must not be requested for forward path %s", path.getPathId()));
        }

        SwitchId yPointSwitchId = yPointSwitch.getSwitchId();
        if (yPointSwitchId.equals(path.getSrcSwitchId())) {
            return flowRulesFactory.getIngressYRuleGenerator(path, flow,
                    encapsulation, emptySet(), yPointMeterId, externalMeterCommandUuid, meterToBeAdded);
        } else {
            if (path.getSegments().isEmpty()) {
                throw new IllegalArgumentException(
                        format("Transit rules must not be requested for a path %s with no segments", path.getPathId()));
            }

            if (yPointSwitchId.equals(path.getDestSwitchId())) {
                return flowRulesFactory.getEgressYRuleGenerator(path, flow,
                        encapsulation, yPointMeterId, externalMeterCommandUuid, meterToBeAdded);
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
    }

    private SwitchPathSegments findPathSegmentsForSwitch(SwitchId switchId, List<PathSegment> pathSegments) {
        SwitchPathSegments result = null;
        for (int i = 1; i < pathSegments.size(); i++) {
            PathSegment firstSegment = pathSegments.get(i - 1);
            PathSegment secondSegment = pathSegments.get(i);
            if (isTargetSegments(switchId, firstSegment, secondSegment)) {
                result = new SwitchPathSegments(firstSegment, secondSegment);
                break;
            }
        }
        return result;
    }

    private static boolean isTargetSegments(SwitchId switchId, PathSegment firstSegment, PathSegment secondSegment) {
        return switchId.equals(firstSegment.getDestSwitchId()) && switchId.equals(secondSegment.getSrcSwitchId());
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

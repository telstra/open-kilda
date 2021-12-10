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
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.factory.FlowRulesGeneratorFactory;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.factory.ServiceRulesGeneratorFactory;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RuleManagerImpl implements RuleManager {

    ServiceRulesGeneratorFactory serviceRulesFactory;
    FlowRulesGeneratorFactory flowRulesFactory;

    public RuleManagerImpl(RuleManagerConfig config) {
        serviceRulesFactory = new ServiceRulesGeneratorFactory(config);
        flowRulesFactory = new FlowRulesGeneratorFactory(config);
    }

    @Override
    public List<SpeakerCommandData> buildRulesForFlowPath(
            FlowPath flowPath, boolean filterOutUsedSharedRules, DataAdapter adapter) {
        List<SpeakerCommandData> result = new ArrayList<>();
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
    public List<SpeakerCommandData> buildRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        Switch sw = adapter.getSwitch(switchId);
        List<SpeakerCommandData> result = buildServiceRules(sw, adapter);

        result.addAll(buildFlowRulesForSwitch(switchId, adapter));

        return postProcessCommands(result);
    }

    private List<SpeakerCommandData> buildServiceRules(Switch sw, DataAdapter adapter) {
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

    private List<SpeakerCommandData> buildFlowRulesForSwitch(SwitchId switchId, DataAdapter adapter) {
        return adapter.getFlowPaths().values().stream()
                .flatMap(flowPath -> buildFlowRulesForSwitch(switchId, flowPath, adapter).stream())
                .collect(Collectors.toList());
    }

    /**
     * Builds command data only for switches present in the map. Silently skips all others.
     */
    private List<SpeakerCommandData> buildFlowRulesForSwitch(
            SwitchId switchId, FlowPath flowPath, DataAdapter adapter) {
        List<SpeakerCommandData> result = new ArrayList<>();
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

    private List<SpeakerCommandData> buildIngressCommands(Switch sw, FlowPath flowPath, Flow flow,
            FlowTransitEncapsulation encapsulation, Set<FlowSideAdapter> overlappingIngressAdapters) {
        List<SpeakerCommandData> ingressCommands = flowRulesFactory.getIngressRuleGenerator(
                flowPath, flow, encapsulation, overlappingIngressAdapters).generateCommands(sw);
        String ingressMeterCommandUuid = Utils.getCommand(MeterSpeakerCommandData.class, ingressCommands)
                .map(SpeakerCommandData::getUuid).orElse(null);

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

    private List<SpeakerCommandData> buildEgressCommands(Switch sw, FlowPath flowPath, Flow flow,
            FlowTransitEncapsulation encapsulation) {
        List<RuleGenerator> generators = new ArrayList<>();

        generators.add(flowRulesFactory.getEgressRuleGenerator(flowPath, flow, encapsulation));
        if (flowPath.getFlowMirrorPointsSet() != null && !flowPath.getFlowMirrorPointsSet().isEmpty()) {
            generators.add(flowRulesFactory.getEgressMirrorRuleGenerator(flowPath, flow, encapsulation));
        }

        return generateRules(sw, generators);
    }

    private List<SpeakerCommandData> buildTransitCommands(
            Switch sw, FlowPath flowPath, FlowTransitEncapsulation encapsulation, PathSegment firstSegment,
            PathSegment secondSegment) {
        RuleGenerator generator = flowRulesFactory.getTransitRuleGenerator(
                flowPath, encapsulation, firstSegment, secondSegment);
        return generator.generateCommands(sw);

    }

    private List<SpeakerCommandData> buildTransitLoopCommands(
            Switch sw, FlowPath flowPath, Flow flow, FlowTransitEncapsulation encapsulation) {
        List<SpeakerCommandData> result = new ArrayList<>();

        flowPath.getSegments().stream()
                .filter(segment -> segment.getDestSwitchId().equals(flow.getLoopSwitchId()))
                .findFirst()
                .map(PathSegment::getDestPort)
                .map(inPort -> flowRulesFactory.getTransitLoopRuleGenerator(flowPath, flow, encapsulation, inPort))
                .map(generator -> generator.generateCommands(sw))
                .map(result::addAll);
        return result;
    }

    private List<SpeakerCommandData> generateRules(Switch sw, List<RuleGenerator> generators) {
        return generators.stream()
                .flatMap(generator -> generator.generateCommands(sw).stream())
                .collect(Collectors.toList());
    }
}

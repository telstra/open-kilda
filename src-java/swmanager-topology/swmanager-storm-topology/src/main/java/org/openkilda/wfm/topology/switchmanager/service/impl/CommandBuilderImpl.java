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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.lang.String.format;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE;
import static org.openkilda.model.cookie.Cookie.SERVER_42_ISL_RTT_OUTPUT_COOKIE;

import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallServer42Flow;
import org.openkilda.messaging.command.flow.InstallServer42Flow.InstallServer42FlowBuilder;
import org.openkilda.messaging.command.flow.InstallSharedFlow;
import org.openkilda.messaging.command.flow.ModifyDefaultMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ModifyFlowMeterForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallDefaultFlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.ReinstallServer42FlowForSwitchManagerRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.MirrorConfig.MirrorConfigData;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.switchmanager.model.GroupInstallContext;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class CommandBuilderImpl implements CommandBuilder {
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final MirrorGroupRepository mirrorGroupRepository;
    private final FlowCommandFactory flowCommandFactory = new FlowCommandFactory();
    private final FlowResourcesManager flowResourcesManager;

    public CommandBuilderImpl(PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.mirrorGroupRepository = persistenceManager.getRepositoryFactory().createMirrorGroupRepository();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
    }

    @Override
    public List<BaseFlow> buildCommandsToSyncMissingRules(SwitchId switchId, List<Long> switchRules) {

        List<BaseFlow> commands = new ArrayList<>(buildInstallDefaultRuleCommands(switchId, switchRules));
        commands.addAll(buildInstallFlowSharedRuleCommands(switchId, switchRules));

        flowPathRepository.findBySegmentDestSwitch(switchId)
                .forEach(flowPath -> {
                    FlowSegmentCookie mirrorCookie = flowPath.getCookie().toBuilder().mirror(true).build();
                    boolean switchRulesContainsFlowPathCookie = switchRules.contains(flowPath.getCookie().getValue());
                    boolean switchRulesContainsMirrorCookie = switchRules.contains(mirrorCookie.getValue());
                    if (switchRulesContainsFlowPathCookie || switchRulesContainsMirrorCookie) {
                        PathSegment segment = flowPath.getSegments().stream()
                                .filter(pathSegment -> pathSegment.getDestSwitchId().equals(switchId))
                                .findAny()
                                .orElseThrow(() -> new IllegalStateException(
                                        format("PathSegment not found, path %s, switch %s", flowPath, switchId)));
                        log.info("Rule {} is to be (re)installed on switch {}", flowPath.getCookie(), switchId);
                        commands.addAll(buildInstallCommandFromSegment(flowPath, segment,
                                switchRulesContainsFlowPathCookie, switchRulesContainsMirrorCookie));
                    }
                });

        SwitchProperties switchProperties = getSwitchProperties(switchId);
        flowPathRepository.findByEndpointSwitch(switchId)
                .forEach(flowPath -> {
                    FlowSegmentCookie mirrorCookie = flowPath.getCookie().toBuilder().mirror(true).build();
                    boolean switchRulesContainsFlowPathCookie = switchRules.contains(flowPath.getCookie().getValue());
                    boolean switchRulesContainsMirrorCookie = switchRules.contains(mirrorCookie.getValue());
                    if (switchRulesContainsFlowPathCookie || switchRulesContainsMirrorCookie) {
                        Flow flow = getFlow(flowPath);
                        if (flowPath.isOneSwitchFlow()) {
                            log.info("One-switch flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            SwitchId swId = flowPath.isForward() ? flow.getDestSwitchId() : flow.getSrcSwitchId();
                            int port = flowPath.isForward() ? flow.getDestPort() : flow.getSrcPort();
                            if (switchRulesContainsMirrorCookie) {
                                MirrorConfig mirrorConfig = makeMirrorConfig(flowPath, swId, port);
                                commands.add(flowCommandFactory.makeOneSwitchMirrorRule(flow, flowPath, mirrorConfig));
                            }
                            if (switchRulesContainsFlowPathCookie) {
                                commands.add(flowCommandFactory.makeOneSwitchRule(flow, flowPath));
                            }
                        } else if (flowPath.getSrcSwitchId().equals(switchId)) {
                            log.info("Ingress flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            if (flowPath.getSegments().isEmpty()) {
                                log.warn("Output port was not found for ingress flow rule");
                            } else {
                                PathSegment foundIngressSegment = flowPath.getSegments().get(0);
                                EncapsulationResources encapsulationResources = getEncapsulationResources(
                                        flowPath, flow);
                                if (switchRulesContainsMirrorCookie) {
                                    MirrorConfig mirrorConfig = makeMirrorConfig(flowPath,
                                            foundIngressSegment.getSrcSwitchId(), foundIngressSegment.getSrcPort());
                                    commands.add(flowCommandFactory.buildInstallIngressMirrorFlow(flow, flowPath,
                                            foundIngressSegment.getSrcPort(), encapsulationResources,
                                            foundIngressSegment.isSrcWithMultiTable(), mirrorConfig));
                                }
                                if (switchRulesContainsFlowPathCookie) {
                                    commands.add(flowCommandFactory.buildInstallIngressFlow(flow, flowPath,
                                            foundIngressSegment.getSrcPort(), encapsulationResources,
                                            foundIngressSegment.isSrcWithMultiTable()));
                                }
                            }
                        }
                    }

                    long server42Cookie = flowPath.getCookie().toBuilder()
                            .type(CookieType.SERVER_42_FLOW_RTT_INGRESS)
                            .build()
                            .getValue();
                    if (switchRules.contains(server42Cookie) && !flowPath.isOneSwitchFlow()
                            && flowPath.getSrcSwitchId().equals(switchId)) {
                        log.info("Ingress server 42 flow {} is to be (re)installed on switch {}",
                                server42Cookie, switchId);

                        if (flowPath.getSegments().isEmpty()) {
                            log.warn("Output port was not found for server 42 ingress flow rule {}", server42Cookie);
                        } else {
                            Flow flow = getFlow(flowPath);
                            PathSegment foundIngressSegment = flowPath.getSegments().get(0);
                            EncapsulationResources encapsulationResources = getEncapsulationResources(flowPath, flow);
                            commands.add(flowCommandFactory.buildInstallServer42IngressFlow(
                                    flow, flowPath, foundIngressSegment.getSrcPort(),
                                    switchProperties.getServer42Port(), switchProperties.getServer42MacAddress(),
                                    encapsulationResources, foundIngressSegment.isSrcWithMultiTable()));
                        }
                    }

                    long loopCookie = flowPath.getCookie().toBuilder().looped(true).build().getValue();
                    if (switchRules.contains(loopCookie)) {
                        log.info("Loop rule with cookie {} is to be reinstalled on switch {}", loopCookie, switchId);
                        Flow flow = getFlow(flowPath);
                        EncapsulationResources encapsulationResources = getEncapsulationResources(
                                flowPath, flow);
                        if (flowPath.getSrcSwitch().getSwitchId().equals(switchId)) {
                            boolean srcWithMultiTable = flowPath.getSegments().get(0).isSrcWithMultiTable();
                            commands.add(flowCommandFactory.buildInstallIngressLoopFlow(flow, flowPath,
                                    encapsulationResources, srcWithMultiTable));
                        } else {
                            PathSegment lastSegment = flowPath.getSegments().get(flowPath.getSegments().size() - 1);
                            boolean destWithMultiTable = lastSegment.isDestWithMultiTable();
                            commands.add(flowCommandFactory.buildInstallTransitLoopFlow(flow, flowPath,
                                    lastSegment.getDestPort(), encapsulationResources, destWithMultiTable));
                        }

                    }
                });

        return commands;
    }

    private Flow getFlow(FlowPath flowPath) {
        return flowRepository.findById(flowPath.getFlow().getFlowId())
                                    .orElseThrow(() ->
                                            new IllegalStateException(format("Abandon FlowPath found: %s", flowPath)));
    }

    private EncapsulationResources getEncapsulationResources(FlowPath flowPath, Flow flow) {
        return flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                flow.getOppositePathId(flowPath.getPathId())
                        .orElseThrow(() -> new IllegalStateException(
                                format("Flow %s does not have reverse path for %s",
                                        flow.getFlowId(), flowPath.getPathId()))),
                flow.getEncapsulationType())
                .orElseThrow(() -> new IllegalStateException(
                        format("Encapsulation resources are not found for path %s", flowPath)));
    }

    /**
     * Some default rules require additional properties to be installed. This method filters such rules.
     */
    private static boolean isDefaultRuleWithSpecialRequirements(long cookie) {
        return cookie == SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE
                || cookie == SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE
                || new PortColourCookie(cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT
                || cookie == SERVER_42_ISL_RTT_OUTPUT_COOKIE
                || new PortColourCookie(cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT;
    }

    /**
     * Some default rules require additional properties to be installed. This method creates commands for such rules.
     */
    private List<BaseInstallFlow> buildInstallSpecialDefaultRuleCommands(SwitchId switchId, List<Long> switchRules) {
        SwitchProperties properties = getSwitchProperties(switchId);

        List<BaseInstallFlow> commands = new ArrayList<>();
        for (Long cookie : switchRules) {
            InstallServer42FlowBuilder command = InstallServer42Flow.builder()
                    .transactionId(transactionIdGenerator.generate())
                    .cookie(cookie)
                    .switchId(switchId)
                    .multiTable(properties.isMultiTable())
                    .inputPort(0)
                    .outputPort(0)
                    .server42Vlan(properties.getServer42Vlan())
                    .server42MacAddress(properties.getServer42MacAddress());

            if (cookie == SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE) {
                commands.add(command
                        .id("SWMANAGER_SERVER_42_FLOW_RTT_OUTPUT_VLAN_RULE_INSTALL")
                        .outputPort(properties.getServer42Port())
                        .build());
            } else if (cookie == SERVER_42_FLOW_RTT_OUTPUT_VXLAN_COOKIE) {
                commands.add(command
                        .id("SWMANAGER_SERVER_42_FLOW_RTT_OUTPUT_VXLAN_RULE_INSTALL")
                        .outputPort(properties.getServer42Port())
                        .build());
            } else if (new PortColourCookie(cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT) {
                commands.add(command
                        .id("SWMANAGER_SERVER_42_FLOW_RTT_INPUT_RULE_INSTALL")
                        .inputPort(properties.getServer42Port())
                        .build());
            } else if (cookie == SERVER_42_ISL_RTT_OUTPUT_COOKIE) {
                commands.add(command
                        .id("SWMANAGER_SERVER_42_ISL_RTT_OUTPUT_RULE_INSTALL")
                        .outputPort(properties.getServer42Port())
                        .build());
            } else if (new PortColourCookie(cookie).getType() == CookieType.SERVER_42_ISL_RTT_INPUT) {
                commands.add(command
                        .id("SWMANAGER_SERVER_42_ISL_RTT_INPUT_RULE_INSTALL")
                        .inputPort(properties.getServer42Port())
                        .build());
            } else {
                log.warn("Got request for installation of unknown rule {} on switch {}", cookie, switchId);
            }
        }
        return commands;
    }

    private SwitchProperties getSwitchProperties(SwitchId switchId) {
        return switchPropertiesRepository.findBySwitchId(switchId).orElseThrow(
                () -> new IllegalStateException(format("Switch properties not found for switch %s", switchId)));
    }

    private List<BaseInstallFlow> buildInstallDefaultRuleCommands(SwitchId switchId, List<Long> switchRules) {

        List<BaseInstallFlow> commands = new ArrayList<>(
                buildInstallSpecialDefaultRuleCommands(
                        switchId, switchRules.stream()
                        .filter(CommandBuilderImpl::isDefaultRuleWithSpecialRequirements)
                        .collect(Collectors.toList())));

        switchRules.stream()
                .filter(Cookie::isDefaultRule)
                .filter(cookie -> !isDefaultRuleWithSpecialRequirements(cookie))
                .map(cookie -> new BaseInstallFlow(transactionIdGenerator.generate(), "SWMANAGER_DEFAULT_RULE_INSTALL",
                        cookie, switchId, 0, 0, false))
                .forEach(commands::add);

        return commands;
    }

    private List<BaseFlow> buildInstallFlowSharedRuleCommands(SwitchId switchId, List<Long> switchRules) {
        List<BaseFlow> results = new ArrayList<>();
        for (long rawCookie : switchRules) {
            FlowSharedSegmentCookie cookie = new FlowSharedSegmentCookie(rawCookie);
            if (cookie.getType() != CookieType.SHARED_OF_FLOW) {
                continue;
            }

            if (cookie.getSegmentType() == SharedSegmentType.QINQ_OUTER_VLAN) {
                results.add(new InstallSharedFlow(
                        transactionIdGenerator.generate(), "SWMANAGER_SHARED_FLOW_INSTALL", rawCookie, switchId));
            } else if (cookie.getSegmentType() == SharedSegmentType.SERVER42_QINQ_OUTER_VLAN) {
                results.add(InstallServer42Flow.builder()
                        .id("SWMANAGER_SERVER42_SHARED_FLOW_INSTALL")
                        .transactionId(transactionIdGenerator.generate())
                        .cookie(cookie.getValue())
                        .switchId(switchId)
                        .multiTable(true)
                        .inputPort(cookie.getPortNumber())
                        .outputPort(0)
                        .build());
            }
        }

        return results;
    }

    @Override
    public List<RemoveFlow> buildCommandsToRemoveExcessRules(SwitchId switchId,
                                                             List<FlowEntry> flows,
                                                             List<Long> excessRulesCookies) {
        return flows.stream()
                .filter(flow -> excessRulesCookies.contains(flow.getCookie()))
                .map(entry -> buildRemoveFlowWithoutMeterFromFlowEntry(switchId, entry))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReinstallDefaultFlowForSwitchManagerRequest> buildCommandsToReinstallRules(
            SwitchId switchId, List<Long> reinstallRulesCookies) {

        SwitchProperties properties = getSwitchProperties(switchId);
        List<ReinstallDefaultFlowForSwitchManagerRequest> commands = new ArrayList<>();

        for (Long cookie : reinstallRulesCookies) {
            if (isDefaultRuleWithSpecialRequirements(cookie)) {
                commands.add(new ReinstallServer42FlowForSwitchManagerRequest(
                        switchId, cookie, properties.getServer42MacAddress(), properties.getServer42Vlan(),
                        properties.getServer42Port()));
            } else {
                commands.add(new ReinstallDefaultFlowForSwitchManagerRequest(switchId, cookie));
            }
        }

        return commands;
    }

    @Override
    public List<ModifyDefaultMeterForSwitchManagerRequest> buildCommandsToModifyMisconfiguredMeters(
            SwitchId switchId, List<Long> misconfiguredDefaultMeters, List<MeterInfoEntry> misconfiguredFlowMeters) {
        List<ModifyDefaultMeterForSwitchManagerRequest> commands = misconfiguredDefaultMeters.stream()
                .map(meterId -> new ModifyDefaultMeterForSwitchManagerRequest(switchId, meterId))
                .collect(Collectors.toList());

        for (MeterInfoEntry meter : misconfiguredFlowMeters) {
            long rate = Optional.ofNullable(meter.getExpected().getRate()).orElse(meter.getRate());
            commands.add(new ModifyFlowMeterForSwitchManagerRequest(switchId, meter.getMeterId(), rate));
        }
        return commands;
    }

    @Override
    public List<GroupInstallContext> buildGroupInstallContexts(SwitchId switchId, List<Integer> groupIds) {
        List<GroupInstallContext> groupInstallContexts = new ArrayList<>();

        Map<PathId, MirrorGroup> mirrorGroups = new HashMap<>();
        groupIds.stream()
                .map(GroupId::new)
                .map(mirrorGroupRepository::findByGroupId)
                .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .forEach(mirrorGroup -> mirrorGroups.put(mirrorGroup.getPathId(), mirrorGroup));

        flowPathRepository.findBySegmentDestSwitch(switchId)
                .forEach(flowPath -> {
                    if (mirrorGroups.containsKey(flowPath.getPathId())) {
                        PathSegment segment = flowPath.getSegments().stream()
                                .filter(pathSegment -> pathSegment.getDestSwitchId().equals(switchId))
                                .findAny()
                                .orElseThrow(() -> new IllegalStateException(
                                        format("PathSegment not found, path %s, switch %s", flowPath, switchId)));

                        Flow flow = flowPath.getFlow();
                        if (segment.getDestSwitchId().equals(flowPath.getDestSwitchId())) {
                            MirrorConfig mirrorConfig
                                    = makeMirrorConfig(flowPath, flow.getDestSwitchId(), flow.getDestPort());
                            groupInstallContexts.add(GroupInstallContext.builder().mirrorConfig(mirrorConfig).build());

                            log.info("Group {} is to be (re)installed on switch {}",
                                    mirrorConfig.getGroupId(), switchId);
                        }
                    }
                });

        flowPathRepository.findByEndpointSwitch(switchId)
                .forEach(flowPath -> {
                    if (mirrorGroups.containsKey(flowPath.getPathId())) {
                        Flow flow = getFlow(flowPath);
                        if (flowPath.isOneSwitchFlow()) {
                            SwitchId swId = flowPath.isForward() ? flow.getDestSwitchId() : flow.getSrcSwitchId();
                            int port = flowPath.isForward() ? flow.getDestPort() : flow.getSrcPort();
                            MirrorConfig mirrorConfig = makeMirrorConfig(flowPath, swId, port);
                            groupInstallContexts.add(GroupInstallContext.builder().mirrorConfig(mirrorConfig).build());
                            log.info("Group {} is to be (re)installed on switch {}",
                                    mirrorConfig.getGroupId(), switchId);
                        } else if (flowPath.getSrcSwitchId().equals(switchId)) {
                            if (flowPath.getSegments().isEmpty()) {
                                log.warn("Output port was not found for mirror config");
                            } else {
                                PathSegment foundIngressSegment = flowPath.getSegments().get(0);
                                MirrorConfig mirrorConfig = makeMirrorConfig(flowPath,
                                        foundIngressSegment.getSrcSwitchId(), foundIngressSegment.getSrcPort());
                                EncapsulationResources encapsulation = getEncapsulationResources(flowPath, flow);
                                groupInstallContexts.add(GroupInstallContext.builder()
                                        .mirrorConfig(mirrorConfig)
                                        .encapsulation(
                                                new FlowTransitEncapsulation(encapsulation.getTransitEncapsulationId(),
                                                        encapsulation.getEncapsulationType()))
                                        .egressSwitchId(flowPath.getDestSwitchId())
                                        .build());
                                log.info("Group {} is to be (re)installed on switch {}",
                                        mirrorConfig.getGroupId(), switchId);
                            }
                        }
                    }
                });

        return groupInstallContexts;
    }

    @VisibleForTesting
    RemoveFlow buildRemoveFlowWithoutMeterFromFlowEntry(SwitchId switchId, FlowEntry entry) {
        Optional<FlowMatchField> entryMatch = Optional.ofNullable(entry.getMatch());

        Integer inPort = entryMatch.map(FlowMatchField::getInPort).map(Integer::valueOf).orElse(null);

        FlowEncapsulationType encapsulationType = FlowEncapsulationType.TRANSIT_VLAN;
        Integer encapsulationId = null;
        Integer vlan = entryMatch.map(FlowMatchField::getVlanVid).map(Integer::valueOf).orElse(null);
        if (vlan != null) {
            encapsulationId = vlan;
        } else {
            Integer tunnelId = entryMatch.map(FlowMatchField::getTunnelId).map(Integer::decode).orElse(null);

            if (tunnelId != null) {
                encapsulationId = tunnelId;
                encapsulationType = FlowEncapsulationType.VXLAN;
            }
        }

        Optional<FlowApplyActions> actions = Optional.ofNullable(entry.getInstructions())
                .map(FlowInstructions::getApplyActions);

        Integer outPort = actions
                .map(FlowApplyActions::getFlowOutput)
                .filter(NumberUtils::isNumber)
                .map(Integer::valueOf)
                .orElse(null);

        SwitchId ingressSwitchId = entryMatch.map(FlowMatchField::getEthSrc).map(SwitchId::new).orElse(null);
        Long metadataValue = entryMatch.map(FlowMatchField::getMetadataValue).map(Long::decode).orElse(null);
        Long metadataMask = entryMatch.map(FlowMatchField::getMetadataMask).map(Long::decode).orElse(null);

        DeleteRulesCriteria criteria = new DeleteRulesCriteria(entry.getCookie(), inPort, encapsulationId,
                0, outPort, encapsulationType, ingressSwitchId, metadataValue, metadataMask);

        return RemoveFlow.builder()
                .transactionId(transactionIdGenerator.generate())
                .flowId("SWMANAGER_BATCH_REMOVE")
                .cookie(entry.getCookie())
                .switchId(switchId)
                .criteria(criteria)
                .build();
    }

    private List<BaseInstallFlow> buildInstallCommandFromSegment(FlowPath flowPath, PathSegment segment,
                                                                 boolean switchRulesContainsFlowPathCookie,
                                                                 boolean switchRulesContainsMirrorCookie) {
        if (segment.getSrcSwitchId().equals(segment.getDestSwitchId())) {
            log.warn("One-switch flow segment {} is provided", flowPath.getCookie());
            return new ArrayList<>();
        }

        Optional<Flow> foundFlow = flowRepository.findById(flowPath.getFlow().getFlowId());
        if (!foundFlow.isPresent()) {
            log.warn("Flow with id {} was not found", flowPath.getFlow().getFlowId());
            return new ArrayList<>();
        }
        Flow flow = foundFlow.get();

        EncapsulationResources encapsulationResources = getEncapsulationResources(flowPath, flow);

        if (segment.getDestSwitchId().equals(flowPath.getDestSwitchId())) {
            List<BaseInstallFlow> commands = new ArrayList<>();
            if (switchRulesContainsMirrorCookie) {
                MirrorConfig mirrorConfig = makeMirrorConfig(flowPath, flow.getDestSwitchId(), flow.getDestPort());
                commands.add(flowCommandFactory.buildInstallEgressMirrorFlow(flowPath, segment.getDestPort(),
                                encapsulationResources, segment.isDestWithMultiTable(), mirrorConfig));
            }
            if (switchRulesContainsFlowPathCookie) {
                commands.add(flowCommandFactory.buildInstallEgressFlow(flowPath, segment.getDestPort(),
                                encapsulationResources, segment.isDestWithMultiTable()));
            }
            return commands;
        } else {
            int segmentIdx = flowPath.getSegments().indexOf(segment);
            if (segmentIdx < 0 || segmentIdx + 1 == flowPath.getSegments().size()) {
                log.warn("Paired segment for switch {} and cookie {} has not been found",
                        segment.getDestSwitchId(), flowPath.getCookie());
                return new ArrayList<>();
            }

            PathSegment foundPairedFlowSegment = flowPath.getSegments().get(segmentIdx + 1);

            return Collections.singletonList(flowCommandFactory.buildInstallTransitFlow(
                    flowPath, segment.getDestSwitchId(), segment.getDestPort(),
                    foundPairedFlowSegment.getSrcPort(), encapsulationResources,
                    segment.isDestWithMultiTable()));
        }
    }

    private MirrorConfig makeMirrorConfig(@NonNull FlowPath flowPath, @NonNull SwitchId mirrorSwitchId,
                                          int mirrorPort) {
        MirrorConfig mirrorConfig = null;
        FlowMirrorPoints flowMirrorPoints = flowPath.getFlowMirrorPointsSet().stream()
                .filter(mirrorPoints -> mirrorSwitchId.equals(mirrorPoints.getMirrorSwitchId()))
                .findFirst().orElse(null);

        if (flowMirrorPoints != null) {
            Set<MirrorConfigData> mirrorConfigDataSet = flowMirrorPoints.getMirrorPaths().stream()
                    .map(mirrorPath ->
                            new MirrorConfigData(mirrorPath.getEgressPort(), mirrorPath.getEgressOuterVlan()))
                    .collect(Collectors.toSet());

            if (!mirrorConfigDataSet.isEmpty()) {
                mirrorConfig = MirrorConfig.builder()
                        .groupId(flowMirrorPoints.getMirrorGroupId())
                        .flowPort(mirrorPort)
                        .mirrorConfigDataSet(mirrorConfigDataSet)
                        .build();
            }
        }

        return mirrorConfig;
    }
}

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

package org.openkilda.wfm.topology.applications.service;

import static java.lang.String.format;

import org.openkilda.applications.command.apps.CreateExclusion;
import org.openkilda.applications.command.apps.RemoveExclusion;
import org.openkilda.applications.info.apps.CreateExclusionResult;
import org.openkilda.applications.info.apps.FlowApplicationCreated;
import org.openkilda.applications.info.apps.FlowApplicationRemoved;
import org.openkilda.applications.info.apps.RemoveExclusionResult;
import org.openkilda.applications.model.Exclusion;
import org.openkilda.messaging.command.apps.FlowAddAppRequest;
import org.openkilda.messaging.command.apps.FlowRemoveAppRequest;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.switches.InstallExclusionRequest;
import org.openkilda.messaging.command.switches.InstallTelescopeRuleRequest;
import org.openkilda.messaging.command.switches.RemoveExclusionRequest;
import org.openkilda.messaging.command.switches.RemoveTelescopeRuleRequest;
import org.openkilda.messaging.info.apps.AppsEntry;
import org.openkilda.messaging.info.apps.FlowAppsResponse;
import org.openkilda.model.ApplicationRule;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Metadata;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.ApplicationRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.SwitchPropertiesNotFoundException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.applications.AppsManagerCarrier;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class AppsManagerService {

    private static final FlowEncapsulationType ONE_SWITCH_FLOW_FLOW_ENCAPSULATION_TYPE = FlowEncapsulationType.VXLAN;

    private final AppsManagerCarrier carrier;

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final ApplicationRepository applicationRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final TransactionManager transactionManager;

    private final FlowResourcesManager flowResourcesManager;
    private final FlowCommandFactory flowCommandFactory = new FlowCommandFactory();

    public AppsManagerService(AppsManagerCarrier carrier,
                              PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.carrier = carrier;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.applicationRepository = persistenceManager.getRepositoryFactory().createApplicationRepository();
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.transactionManager = persistenceManager.getTransactionManager();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
    }

    /**
     * Process getting applications for flow.
     */
    public void getEnabledFlowApplications(String flowId) throws FlowNotFoundException {
        Flow flow = getFlow(flowId);
        sendResponse(flow);
    }

    /**
     * Process adding application for flow endpoint or endpoints.
     */
    public void addFlowApplication(FlowAddAppRequest payload)
            throws FlowNotFoundException, SwitchPropertiesNotFoundException {

        FlowApplication application = convertApplicationFromString(payload.getApplication());
        Flow flow = getFlow(payload.getFlowId());

        switch (application) {
            case TELESCOPE:
                addTelescopeForFlow(flow);
                break;
            default:
                throw new UnsupportedOperationException(
                        format("%s application adding has not yet been implemented.", application));
        }

        sendResponse(flow);
    }

    private void addTelescopeForFlow(Flow flow) throws SwitchPropertiesNotFoundException {
        persistTelescope(flow);
        EncapsulationResources encapsulationResources = checkOneSwitchFlowAndGetFlowEncapsulationResources(flow);

        sendSpeakerUpdateFlowEndpointRulesCommands(flow, encapsulationResources);
        sendSpeakerInstallTelescopeRuleCommands(flow.getSrcSwitch().getSwitchId(), flow, encapsulationResources);
        sendAppCreateNotification(flow.getFlowId(), FlowApplication.TELESCOPE);
    }

    @VisibleForTesting
    void persistTelescope(Flow flow) {
        transactionManager.doInTransaction(() -> {
            addAppToFlowPath(flow.getForwardPath(), FlowApplication.TELESCOPE);
            addAppToFlowPath(flow.getReversePath(), FlowApplication.TELESCOPE);
        });
    }

    private void addAppToFlowPath(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.add(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
    }

    @VisibleForTesting
    EncapsulationResources checkOneSwitchFlowAndGetFlowEncapsulationResources(Flow flow) {
        return transactionManager.doInTransaction(() -> {
            if (flow.isOneSwitchFlow()) {
                flow.setEncapsulationType(ONE_SWITCH_FLOW_FLOW_ENCAPSULATION_TYPE);
                flowRepository.createOrUpdate(flow);
                flowResourcesManager.allocateEncapsulationResources(flow, flow.getEncapsulationType());
            }
            return getEncapsulationResources(flow, flow.getForwardPath());
        });
    }

    private void sendAppCreateNotification(String flowId, FlowApplication application) {
        carrier.emitNotification(FlowApplicationCreated.builder()
                .flowId(flowId)
                .application(application.toString().toLowerCase())
                .build());
    }

    /**
     * Process removing application for flow endpoint or endpoints.
     */
    public void removeFlowApplication(FlowRemoveAppRequest payload) throws FlowNotFoundException {
        FlowApplication application = convertApplicationFromString(payload.getApplication());
        Flow flow = getFlow(payload.getFlowId());

        switch (application) {
            case TELESCOPE:
                removeTelescopeForFlow(flow);
                break;
            default:
                throw new UnsupportedOperationException(
                        format("%s application removing has not yet been implemented.", application));
        }

        sendResponse(flow);
    }

    private void removeTelescopeForFlow(Flow flow) {
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        removeTelescopeFromFlowPaths(flow);
        Collection<ApplicationRule> applicationRules = removeFlowExclusions(flow.getFlowId());

        sendRemoveExclusionCommandsAndNotifications(flow.getFlowId(), applicationRules);
        sendSpeakerUpdateFlowEndpointRulesCommands(flow, encapsulationResources);
        sendSpeakerRemoveTelescopeRuleCommands(flow, encapsulationResources);
        sendAppRemoveNotification(flow.getFlowId(), FlowApplication.TELESCOPE);

        if (flow.isOneSwitchFlow()) {
            flowResourcesManager.deallocateEncapsulationResources(flow.getForwardPathId(),
                    ONE_SWITCH_FLOW_FLOW_ENCAPSULATION_TYPE);
        }
    }

    @VisibleForTesting
    void removeTelescopeFromFlowPaths(Flow flow) {
        transactionManager.doInTransaction(() -> {
            removeAppFromFlowPath(flow.getForwardPath(), FlowApplication.TELESCOPE);
            removeAppFromFlowPath(flow.getReversePath(), FlowApplication.TELESCOPE);
        });
    }

    private void removeAppFromFlowPath(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.remove(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
    }

    @VisibleForTesting
    Collection<ApplicationRule> removeFlowExclusions(String flowId) {
        Collection<ApplicationRule> applicationRules = applicationRepository.findByFlowId(flowId);
        applicationRules.forEach(this::deleteExclusion);
        return applicationRules;
    }

    private void sendRemoveExclusionCommandsAndNotifications(String flowId,
                                                             Collection<ApplicationRule> applicationRules) {
        Set<Exclusion> exclusions = new HashSet<>();

        applicationRules.forEach(rule -> {
            sendSpeakerRemoveExclusionCommand(rule);

            Exclusion exclusion = exclusionFromRule(rule);
            if (exclusions.contains(exclusion)) {
                exclusions.remove(exclusion);
                sendRemoveExclusionNotification(flowId, FlowApplication.TELESCOPE.toString().toLowerCase(),
                        exclusion, true);
            } else {
                exclusions.add(exclusion);
            }
        });

        exclusions.forEach(exclusion -> sendRemoveExclusionNotification(flowId,
                FlowApplication.TELESCOPE.toString().toLowerCase(), exclusion, false));

    }

    private Exclusion exclusionFromRule(ApplicationRule rule) {
        return Exclusion.builder()
                .srcIp(rule.getSrcIp())
                .srcPort(rule.getSrcPort())
                .dstIp(rule.getDstIp())
                .dstPort(rule.getDstPort())
                .ethType(rule.getEthType())
                .proto(rule.getProto())
                .build();
    }

    private void sendAppRemoveNotification(String flowId, FlowApplication application) {
        carrier.emitNotification(FlowApplicationRemoved.builder()
                .flowId(flowId)
                .application(application.toString().toLowerCase())
                .build());
    }

    private void sendResponse(Flow flow) {
        carrier.emitNorthboundResponse(FlowAppsResponse.builder()
                .flowId(flow.getFlowId())
                .srcApps(AppsEntry.builder()
                        .endpointSwitch(flow.getForwardPath().getSrcSwitch().getSwitchId())
                        .applications(Optional
                                .ofNullable(flow.getForwardPath().getApplications()).orElse(new HashSet<>()))
                        .build())
                .dstApps(AppsEntry.builder()
                        .endpointSwitch(flow.getReversePath().getSrcSwitch().getSwitchId())
                        .applications(Optional
                                .ofNullable(flow.getReversePath().getApplications()).orElse(new HashSet<>()))
                        .build())
                .build());
    }

    private Flow getFlow(String flowId) throws FlowNotFoundException {
        return flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    private FlowApplication convertApplicationFromString(String application) {
        return FlowApplication.valueOf(application.toUpperCase());
    }

    private void sendSpeakerUpdateFlowEndpointRulesCommands(Flow flow, EncapsulationResources encapsulationResources) {
        if (!flow.isOneSwitchFlow()) {
            updateFlowEndpointRules(flow, encapsulationResources);
        } else {
            updateOneSwitchFlowRules(flow, encapsulationResources);
        }
    }

    private void updateFlowEndpointRules(Flow flow, EncapsulationResources encapsulationResources) {
        carrier.emitSpeakerCommand(buildIngressRuleCommand(flow, flow.getForwardPath(), encapsulationResources));
        carrier.emitSpeakerCommand(buildEgressRuleCommand(flow, flow.getReversePath(), encapsulationResources));
    }

    private void updateOneSwitchFlowRules(Flow flow, EncapsulationResources encapsulationResources) {
        carrier.emitSpeakerCommand(
                flowCommandFactory.makeOneSwitchRule(flow, flow.getForwardPath(), encapsulationResources));
        carrier.emitSpeakerCommand(
                flowCommandFactory.makeOneSwitchRule(flow, flow.getReversePath(), encapsulationResources));
    }

    private void sendSpeakerInstallTelescopeRuleCommands(SwitchId switchId, Flow flow,
                                                         EncapsulationResources encapsulationResources)
            throws SwitchPropertiesNotFoundException {

        SwitchProperties switchProperties = switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new SwitchPropertiesNotFoundException(switchId));
        Objects.requireNonNull(switchProperties.getTelescopePort(),
                format("Telescope port for switch '%s' is not set", switchId));

        installTelescopeRuleCommand(switchId, flow, flow.getForwardPath(), encapsulationResources, switchProperties);
        installTelescopeRuleCommand(switchId, flow, flow.getReversePath(), encapsulationResources, switchProperties);
    }

    private void installTelescopeRuleCommand(SwitchId switchId, Flow flow, FlowPath flowPath,
                                             EncapsulationResources encapsulationResources,
                                             SwitchProperties switchProperties) {

        boolean isForward = flow.isForward(flowPath);
        Cookie cookie = Cookie.buildTelescopeCookie(flowPath.getCookie().getUnmaskedValue(), isForward);
        Metadata metadata = Metadata.builder().encapsulationId(encapsulationResources.getTransitEncapsulationId())
                .forward(isForward).build();
        carrier.emitSpeakerCommand(InstallTelescopeRuleRequest.builder()
                .switchId(switchId)
                .metadata(metadata)
                .telescopeCookie(cookie.getValue())
                .telescopePort(switchProperties.getTelescopePort())
                .telescopeVlan(switchProperties.getTelescopeEgressVlan())
                .build());
    }

    private void sendSpeakerRemoveTelescopeRuleCommands(Flow flow, EncapsulationResources encapsulationResources) {
        removeTelescopeRule(flow.getSrcSwitch().getSwitchId(), flow, flow.getForwardPath(), encapsulationResources);
        removeTelescopeRule(flow.getSrcSwitch().getSwitchId(), flow, flow.getReversePath(), encapsulationResources);
    }

    private void removeTelescopeRule(SwitchId switchId, Flow flow, FlowPath flowPath,
                                     EncapsulationResources encapsulationResources) {
        boolean isForward = flow.isForward(flowPath);
        Cookie cookie = Cookie.buildTelescopeCookie(flowPath.getCookie().getUnmaskedValue(), isForward);
        Metadata metadata = Metadata.builder()
                .encapsulationId(encapsulationResources.getTransitEncapsulationId())
                .forward(isForward)
                .build();
        carrier.emitSpeakerCommand(new RemoveTelescopeRuleRequest(switchId, cookie.getValue(), metadata));
    }

    private InstallIngressFlow buildIngressRuleCommand(Flow flow, FlowPath flowPath,
                                                       EncapsulationResources encapsulationResources) {
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }

        return flowCommandFactory.buildInstallIngressFlow(flow, flowPath, ingressSegment.getSrcPort(),
                encapsulationResources, ingressSegment.isSrcWithMultiTable());
    }

    private InstallEgressFlow buildEgressRuleCommand(Flow flow, FlowPath flowPath,
                                                     EncapsulationResources encapsulationResources) {
        List<PathSegment> segments = flowPath.getSegments();
        requireSegments(segments);

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }

        return flowCommandFactory.buildInstallEgressFlow(flowPath, egressSegment.getDestPort(), encapsulationResources,
                egressSegment.isDestWithMultiTable());
    }

    private EncapsulationResources getEncapsulationResources(Flow flow, FlowPath flowPath) {
        return flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                flow.getOppositePathId(flowPath.getPathId())
                        .orElseThrow(() -> new IllegalStateException(format("Flow %s does not have reverse path for %s",
                                flow.getFlowId(), flowPath.getPathId()))),
                flow.getEncapsulationType()).orElseThrow(() -> new IllegalStateException(
                        format("Encapsulation resources are not found for path %s", flowPath)));
    }

    private void requireSegments(List<PathSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Neither one switch flow nor path segments provided");
        }
    }

    /**
     * Create exclusion for the flow.
     */
    public void processCreateExclusion(CreateExclusion payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());

        checkTelescopeAppInstallation(flow);

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        int expirationTimeout = Optional.ofNullable(payload.getExpirationTimeout()).orElse(0);
        int exclusionId = flowResourcesManager.allocateExclusionIdResources(flow.getFlowId());
        Optional<ApplicationRule> forward = persistApplicationRule(flow, flow.getForwardPath(), payload.getExclusion(),
                exclusionId, expirationTimeout, encapsulationResources);
        forward.ifPresent(rule -> sendSpeakerInstallExclusionCommand(rule, expirationTimeout));
        Optional<ApplicationRule> reverse = persistApplicationRule(flow, flow.getReversePath(), payload.getExclusion(),
                exclusionId, expirationTimeout, encapsulationResources);
        reverse.ifPresent(rule -> sendSpeakerInstallExclusionCommand(rule, expirationTimeout));

        sendInstallExclusionNotification(payload.getFlowId(), payload.getApplication(), payload.getExclusion(),
                expirationTimeout, forward.isPresent() && reverse.isPresent());

    }

    @VisibleForTesting
    Optional<ApplicationRule> persistApplicationRule(Flow flow, FlowPath flowPath, Exclusion exclusion,
                                                     int exclusionId, int expirationTimeout,
                                                     EncapsulationResources encapsulationResources) {
        boolean isForward = flow.isForward(flowPath);
        Metadata metadata = Metadata.builder().encapsulationId(encapsulationResources.getTransitEncapsulationId())
                .forward(isForward).build();

        Optional<ApplicationRule> optionalApplicationRule = lookupApplicationRule(flow, metadata, exclusion);

        if (optionalApplicationRule.isPresent()) {
            return Optional.empty();
        }

        Cookie cookie = Cookie.buildExclusionCookie(flowPath.getCookie().getUnmaskedValue(), exclusionId, isForward);
        ApplicationRule rule = ApplicationRule.builder()
                .flowId(flow.getFlowId())
                .switchId(flow.getSrcSwitch().getSwitchId())
                .cookie(cookie)
                .srcIp(exclusion.getSrcIp())
                .srcPort(exclusion.getSrcPort())
                .dstIp(exclusion.getDstIp())
                .dstPort(exclusion.getDstPort())
                .proto(exclusion.getProto())
                .ethType(exclusion.getEthType())
                .timeCreate(Instant.now())
                .expirationTimeout(expirationTimeout)
                .metadata(metadata)
                .build();

        applicationRepository.createOrUpdate(rule);

        return Optional.of(rule);
    }

    private void sendSpeakerInstallExclusionCommand(ApplicationRule rule, int expirationTimeout) {
        carrier.emitSpeakerCommand(InstallExclusionRequest.builder()
                .switchId(rule.getSwitchId())
                .cookie(rule.getCookie().getValue())
                .metadata(rule.getMetadata())
                .srcIp(rule.getSrcIp())
                .srcPort(rule.getSrcPort())
                .dstIp(rule.getDstIp())
                .dstPort(rule.getDstPort())
                .proto(rule.getProto())
                .ethType(rule.getEthType())
                .expirationTimeout(expirationTimeout)
                .build());
    }

    private void sendInstallExclusionNotification(String flowId, String application, Exclusion exclusion,
                                                  int expirationTimeout, boolean success) {

        carrier.emitNotification(CreateExclusionResult.builder()
                .flowId(flowId)
                .application(application)
                .expirationTimeout(expirationTimeout)
                .exclusion(exclusion)
                .success(success)
                .build());
    }

    /**
     * Remove exclusion by match and cookie.
     */
    public void processRemoveExclusion(RemoveExclusionRequest payload) {
        applicationRepository.lookupRuleByMatchAndCookie(payload.getSwitchId(), new Cookie(payload.getCookie()),
                payload.getSrcIp(), payload.getSrcPort(), payload.getDstIp(), payload.getDstPort(), payload.getProto(),
                payload.getEthType(), payload.getMetadata()).ifPresent(this::deleteExclusion);
    }

    /**
     * Remove exclusion for the flow.
     */
    public void processRemoveExclusion(RemoveExclusion payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());

        checkTelescopeAppInstallation(flow);

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        Optional<ApplicationRule> forward = removeApplicationRule(flow, flow.getForwardPath(), payload.getExclusion(),
                encapsulationResources);
        forward.ifPresent(this::sendSpeakerRemoveExclusionCommand);
        Optional<ApplicationRule> reverse = removeApplicationRule(flow, flow.getReversePath(), payload.getExclusion(),
                encapsulationResources);
        reverse.ifPresent(this::sendSpeakerRemoveExclusionCommand);

        sendRemoveExclusionNotification(payload.getFlowId(), payload.getApplication(), payload.getExclusion(),
                forward.isPresent() && reverse.isPresent());
    }

    @VisibleForTesting
    Optional<ApplicationRule> removeApplicationRule(Flow flow, FlowPath flowPath, Exclusion exclusion,
                                                    EncapsulationResources encapsulationResources) {
        boolean isForward = flow.isForward(flowPath);
        Metadata metadata = Metadata.builder().encapsulationId(encapsulationResources.getTransitEncapsulationId())
                .forward(isForward).build();

        Optional<ApplicationRule> ruleOptional = lookupApplicationRule(flow, metadata, exclusion);
        ruleOptional.ifPresent(this::deleteExclusion);
        return ruleOptional;
    }

    private Optional<ApplicationRule> lookupApplicationRule(Flow flow, Metadata metadata, Exclusion exclusion) {
        Optional<ApplicationRule> ruleOptional = applicationRepository.lookupRuleByMatchAndFlow(
                flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), exclusion.getSrcIp(), exclusion.getSrcPort(),
                exclusion.getDstIp(), exclusion.getDstPort(), exclusion.getProto(), exclusion.getEthType(),
                metadata);
        if (!ruleOptional.isPresent()) {
            log.warn("{} not found with metadata {}", exclusion, metadata);
        }
        return ruleOptional;
    }

    private void deleteExclusion(ApplicationRule rule) {
        applicationRepository.delete(rule);
        flowResourcesManager.deallocateExclusionIdResources(rule.getFlowId(), rule.getCookie().getTypeMetadata());
    }

    private void sendSpeakerRemoveExclusionCommand(ApplicationRule rule) {
        carrier.emitSpeakerCommand(RemoveExclusionRequest.builder()
                .switchId(rule.getSwitchId())
                .cookie(rule.getCookie().getValue())
                .metadata(rule.getMetadata())
                .srcIp(rule.getSrcIp())
                .srcPort(rule.getSrcPort())
                .dstIp(rule.getDstIp())
                .dstPort(rule.getDstPort())
                .proto(rule.getProto())
                .ethType(rule.getEthType())
                .build());
    }

    private void sendRemoveExclusionNotification(String flowId, String application, Exclusion exclusion,
                                                 boolean success) {
        carrier.emitNotification(RemoveExclusionResult.builder()
                .flowId(flowId)
                .application(application)
                .exclusion(exclusion)
                .success(success)
                .build());
    }

    private void checkTelescopeAppInstallation(Flow flow) {
        checkFlowAppInstallation(flow.getForwardPath(), FlowApplication.TELESCOPE);
        checkFlowAppInstallation(flow.getReversePath(), FlowApplication.TELESCOPE);
    }

    private void checkFlowAppInstallation(FlowPath flowPath, FlowApplication application) {
        if (flowPath.getApplications() == null || !flowPath.getApplications().contains(application)) {
            throw new IllegalArgumentException(format("Flow application \"%s\" is not installed for the flow %s",
                    application, flowPath.getFlow().getFlowId()));
        }
    }
}

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
import org.openkilda.applications.error.ErrorAppType;
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
import org.openkilda.wfm.share.flow.resources.ExclusionIdPool;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.applications.AppsManagerCarrier;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class AppsManagerService {

    private final AppsManagerCarrier carrier;

    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final ApplicationRepository applicationRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final TransactionManager transactionManager;

    private final FlowResourcesManager flowResourcesManager;
    private final FlowCommandFactory flowCommandFactory = new FlowCommandFactory();
    private final ExclusionIdPool exclusionIdPool;

    public AppsManagerService(AppsManagerCarrier carrier,
                              PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.carrier = carrier;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.applicationRepository = persistenceManager.getRepositoryFactory().createApplicationRepository();
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.transactionManager = persistenceManager.getTransactionManager();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        this.exclusionIdPool = new ExclusionIdPool(persistenceManager);
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
        Flow updatedFlow = transactionManager.doInTransaction(() -> {
            addAppToFlowPath(flow.getForwardPath(), FlowApplication.TELESCOPE);
            addAppToFlowPath(flow.getReversePath(), FlowApplication.TELESCOPE);
            return flow;
        });
        sendUpdateFlowEndpointRulesCommands(updatedFlow);
        installTelescopeRule(updatedFlow.getSrcSwitch().getSwitchId(), updatedFlow);
        sendAppCreateNotification(updatedFlow.getFlowId(), FlowApplication.TELESCOPE);
    }

    private void addAppToFlowPath(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.add(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
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
        Flow updatedFlow = transactionManager.doInTransaction(() -> {
            removeAppFromFlowPath(flow.getForwardPath(), FlowApplication.TELESCOPE);
            removeAppFromFlowPath(flow.getReversePath(), FlowApplication.TELESCOPE);
            return flow;
        });
        sendUpdateFlowEndpointRulesCommands(updatedFlow);
        removeTelescopeRule(updatedFlow.getSrcSwitch().getSwitchId(), updatedFlow, updatedFlow.getForwardPath());
        removeTelescopeRule(updatedFlow.getSrcSwitch().getSwitchId(), updatedFlow, updatedFlow.getReversePath());
        sendAppRemoveNotification(updatedFlow.getFlowId(), FlowApplication.TELESCOPE);
    }

    private void removeAppFromFlowPath(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.remove(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
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

    private void sendUpdateFlowEndpointRulesCommands(Flow flow) {
        if (!flow.isOneSwitchFlow()) {
            updateFlowEndpointRules(flow);
        } else {
            updateOneSwitchFlowRules(flow);
        }
    }

    private void updateFlowEndpointRules(Flow flow) {
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        carrier.emitSpeakerCommand(buildIngressRuleCommand(flow, flow.getForwardPath(), encapsulationResources));
        carrier.emitSpeakerCommand(buildEgressRuleCommand(flow, flow.getReversePath(), encapsulationResources));
    }

    private void updateOneSwitchFlowRules(Flow flow) {
        carrier.emitSpeakerCommand(flowCommandFactory.makeOneSwitchRule(flow, flow.getForwardPath()));
        carrier.emitSpeakerCommand(flowCommandFactory.makeOneSwitchRule(flow, flow.getReversePath()));
    }

    private void installTelescopeRule(SwitchId switchId, Flow flow) throws SwitchPropertiesNotFoundException {
        SwitchProperties switchProperties = switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new SwitchPropertiesNotFoundException(switchId));
        Objects.requireNonNull(switchProperties.getTelescopePort(),
                format("Telescope port for switch '%s' is not set", switchId));

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flow.getForwardPath());
        Cookie ingressTelescopeCookie =
                Cookie.buildTelescopeCookie(flow.getForwardPath().getCookie().getUnmaskedValue(), true);
        carrier.emitSpeakerCommand(InstallTelescopeRuleRequest.builder()
                .switchId(switchId)
                .metadata(Metadata.buildMetadata(encapsulationResources.getTransitEncapsulationId(), true).getValue())
                .telescopeCookie(ingressTelescopeCookie.getValue())
                .telescopePort(switchProperties.getTelescopePort())
                .telescopeVlan(switchProperties.getTelescopeIngressVlan())
                .build());

        Cookie egressTelescopeCookie =
                Cookie.buildTelescopeCookie(flow.getReversePath().getCookie().getUnmaskedValue(), false);
        carrier.emitSpeakerCommand(InstallTelescopeRuleRequest.builder()
                .switchId(switchId)
                .metadata(Metadata.buildMetadata(encapsulationResources.getTransitEncapsulationId(), false).getValue())
                .telescopeCookie(egressTelescopeCookie.getValue())
                .telescopePort(switchProperties.getTelescopePort())
                .telescopeVlan(switchProperties.getTelescopeEgressVlan())
                .build());
    }

    private void removeTelescopeRule(SwitchId switchId, Flow flow, FlowPath flowPath) {
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);
        boolean isForward = flow.isForward(flowPath);
        Cookie cookie = Cookie.buildTelescopeCookie(flowPath.getCookie().getUnmaskedValue(), isForward);
        Metadata metadata = Metadata.buildMetadata(encapsulationResources.getTransitEncapsulationId(), isForward);
        carrier.emitSpeakerCommand(new RemoveTelescopeRuleRequest(switchId, cookie.getValue(), metadata.getValue()));
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
                encapsulationResources);
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

        return flowCommandFactory.buildInstallEgressFlow(flowPath, egressSegment.getDestPort(), encapsulationResources);
    }

    private EncapsulationResources getEncapsulationResources(Flow flow, FlowPath flowPath) {
        return flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                flow.getOppositePathId(flowPath.getPathId()),
                flow.getEncapsulationType())
                .orElseThrow(() -> new IllegalStateException(
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

        int expirationTimeout = Optional.ofNullable(payload.getExpirationTimeout()).orElse(0);
        int exclusionId = exclusionIdPool.allocate(flow.getFlowId());
        boolean forwardSuccess =
                createApplicationRule(flow, flow.getForwardPath(), payload, exclusionId, expirationTimeout);
        boolean reverseSuccess =
                createApplicationRule(flow, flow.getReversePath(), payload, exclusionId, expirationTimeout);

        carrier.emitNotification(CreateExclusionResult.builder()
                .flowId(payload.getFlowId())
                .application(payload.getApplication())
                .expirationTimeout(expirationTimeout)
                .exclusion(payload.getExclusion())
                .success(forwardSuccess && reverseSuccess)
                .build());
    }

    private boolean createApplicationRule(Flow flow, FlowPath flowPath, CreateExclusion payload,
                                          int exclusionId, int expirationTimeout) {
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);
        Exclusion exclusion = payload.getExclusion();
        boolean isForward = flow.isForward(flowPath);
        Metadata metadata = Metadata.buildMetadata(encapsulationResources.getTransitEncapsulationId(), isForward);
        Optional<ApplicationRule> ruleOptional = applicationRepository.lookupRuleByMatchAndFlow(
                flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), exclusion.getSrcIp(), exclusion.getSrcPort(),
                exclusion.getDstIp(), exclusion.getDstPort(), exclusion.getProto(), exclusion.getEthType(),
                metadata);
        if (ruleOptional.isPresent()) {
            String message = String.format("%s already exists", exclusion);
            log.warn(message);
            carrier.emitAppError(ErrorAppType.ALREADY_EXISTS, message);
            return false;
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

        carrier.emitSpeakerCommand(InstallExclusionRequest.builder()
                .switchId(flow.getSrcSwitch().getSwitchId())
                .cookie(cookie.getValue())
                .metadata(metadata.getValue())
                .srcIp(exclusion.getSrcIp())
                .srcPort(exclusion.getSrcPort())
                .dstIp(exclusion.getDstIp())
                .dstPort(exclusion.getDstPort())
                .proto(exclusion.getProto())
                .ethType(exclusion.getEthType())
                .expirationTimeout(expirationTimeout)
                .build());

        return true;
    }

    /**
     * Remove exclusion by match and cookie.
     */
    public void processRemoveExclusion(RemoveExclusionRequest payload) {
        Optional<ApplicationRule> ruleOptional = applicationRepository.lookupRuleByMatchAndCookie(
                payload.getSwitchId(), payload.getCookie(), payload.getSrcIp(), payload.getSrcPort(),
                payload.getDstIp(), payload.getDstPort(), payload.getProto(), payload.getEthType(),
                new Metadata(payload.getMetadata()));
        if (!ruleOptional.isPresent()) {
            return;
        }
        ApplicationRule rule = ruleOptional.get();
        applicationRepository.delete(rule);
        exclusionIdPool.deallocate(rule.getFlowId(), rule.getCookie().getTypeMetadata());
    }

    /**
     * Remove exclusion for the flow.
     */
    public void processRemoveExclusion(RemoveExclusion payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());

        checkTelescopeAppInstallation(flow);

        boolean forwardSuccess = removeApplicationRule(flow, flow.getForwardPath(), payload);
        boolean reverseSuccess = removeApplicationRule(flow, flow.getReversePath(), payload);

        carrier.emitNotification(RemoveExclusionResult.builder()
                .flowId(payload.getFlowId())
                .application(payload.getApplication())
                .exclusion(payload.getExclusion())
                .success(forwardSuccess && reverseSuccess)
                .build());
    }

    private boolean removeApplicationRule(Flow flow, FlowPath flowPath, RemoveExclusion payload) {
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);
        Exclusion exclusion = payload.getExclusion();
        boolean isForward = flow.isForward(flowPath);
        Metadata metadata = Metadata.buildMetadata(encapsulationResources.getTransitEncapsulationId(), isForward);
        Optional<ApplicationRule> ruleOptional = applicationRepository.lookupRuleByMatchAndFlow(
                flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), exclusion.getSrcIp(), exclusion.getSrcPort(),
                exclusion.getDstIp(), exclusion.getDstPort(), exclusion.getProto(), exclusion.getEthType(),
                metadata);
        if (!ruleOptional.isPresent()) {
            String message = String.format("%s not found", exclusion);
            log.warn(message);
            carrier.emitAppError(ErrorAppType.NOT_FOUND, message);
            return false;
        }
        ApplicationRule rule = ruleOptional.get();
        applicationRepository.delete(rule);
        exclusionIdPool.deallocate(flow.getFlowId(), rule.getCookie().getTypeMetadata());
        carrier.emitSpeakerCommand(RemoveExclusionRequest.builder()
                .switchId(flow.getSrcSwitch().getSwitchId())
                .cookie(rule.getCookie().getValue())
                .metadata(metadata.getValue())
                .srcIp(payload.getExclusion().getSrcIp())
                .srcPort(payload.getExclusion().getSrcPort())
                .dstIp(payload.getExclusion().getDstIp())
                .dstPort(payload.getExclusion().getDstPort())
                .proto(payload.getExclusion().getProto())
                .ethType(payload.getExclusion().getEthType())
                .build());
        return true;
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

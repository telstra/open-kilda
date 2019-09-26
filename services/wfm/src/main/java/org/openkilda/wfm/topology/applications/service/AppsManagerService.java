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
import org.openkilda.applications.model.Endpoint;
import org.openkilda.messaging.command.apps.FlowAddAppRequest;
import org.openkilda.messaging.command.apps.FlowRemoveAppRequest;
import org.openkilda.messaging.command.flow.UpdateIngressFlow;
import org.openkilda.messaging.command.switches.InstallExclusionRequest;
import org.openkilda.messaging.command.switches.RemoveExclusionRequest;
import org.openkilda.messaging.info.apps.AppsEntry;
import org.openkilda.messaging.info.apps.FlowAppsResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.applications.AppsManagerCarrier;

import lombok.extern.slf4j.Slf4j;

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
    private final FlowCommandFactory flowCommandFactory = new FlowCommandFactory();
    private final FlowResourcesManager flowResourcesManager;

    public AppsManagerService(AppsManagerCarrier carrier,
                              PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.carrier = carrier;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
    }

    /**
     * Process getting applications for flow.
     */
    public void getEnabledFlowApplications(String flowId) throws FlowNotFoundException {
        Flow flow = getFlow(flowId);
        sendNbResponse(flow);
    }

    /**
     * Process adding application for flow endpoint or endpoints.
     */
    public void addFlowApplication(FlowAddAppRequest payload) throws FlowNotFoundException {
        SwitchId switchId = payload.getSwitchId();
        Integer port = payload.getPortNumber();
        Integer vlan = payload.getVlanId();
        FlowApplication application = convertApplicationFromString(payload.getApplication());
        Flow flow = getFlow(payload.getFlowId());

        if (processBothEndpoints(switchId, port, vlan)) {
            addAppForFlowPath(flow, flow.getForwardPath(), application);
            addAppForFlowPath(flow, flow.getReversePath(), application);
        } else if (endpointForForwardPath(flow, switchId, port, vlan)) {
            addAppForFlowPath(flow, flow.getForwardPath(), application);
        } else if (endpointForReversePath(flow, switchId, port, vlan)) {
            addAppForFlowPath(flow, flow.getReversePath(), application);
        } else {
            throw new IllegalArgumentException(
                    format("Endpoint {switch_id = %s, port_number = %d, vlan_id = %d} is not a flow endpoint.",
                            switchId, port, vlan));
        }

        if (processBothEndpoints(switchId, port, vlan)) {
            sendFlowCreateNotification(flow.getFlowId(), payload.getApplication(), flow.getSrcSwitch().getSwitchId(),
                    flow.getSrcPort(), flow.getSrcVlan());
            sendFlowCreateNotification(flow.getFlowId(), payload.getApplication(), flow.getDestSwitch().getSwitchId(),
                    flow.getDestPort(), flow.getDestVlan());
        } else {
            sendFlowCreateNotification(flow.getFlowId(), payload.getApplication(), switchId, port, vlan);
        }

        sendNbResponse(flow);
    }

    private void addAppForFlowPath(Flow flow, FlowPath flowPath, FlowApplication application) {
        persistApplication(flowPath, application);
        carrier.emitSpeakerCommand(buildUpdateIngressRuleCommand(flow, flowPath));
    }

    private void persistApplication(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.add(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
    }

    private void sendFlowCreateNotification(String flowId, String app, SwitchId switchId, Integer port, Integer vlan) {
        carrier.emitNotification(FlowApplicationCreated.builder()
                .flowId(flowId)
                .application(app)
                .endpoint(Endpoint.builder()
                        .switchId(switchId.toString())
                        .portNumber(port)
                        .vlanId(vlan)
                        .build())
                .build());
    }

    /**
     * Process removing application for flow endpoint or endpoints.
     */
    public void removeFlowApplication(FlowRemoveAppRequest payload) throws FlowNotFoundException {
        SwitchId switchId = payload.getSwitchId();
        Integer port = payload.getPortNumber();
        Integer vlan = payload.getVlanId();
        FlowApplication application = convertApplicationFromString(payload.getApplication());
        Flow flow = getFlow(payload.getFlowId());

        if (processBothEndpoints(switchId, port, vlan)) {
            removeAppForFlowPath(flow, flow.getForwardPath(), application);
            removeAppForFlowPath(flow, flow.getReversePath(), application);
        } else if (endpointForForwardPath(flow, switchId, port, vlan)) {
            removeAppForFlowPath(flow, flow.getForwardPath(), application);
        } else if (endpointForReversePath(flow, switchId, port, vlan)) {
            removeAppForFlowPath(flow, flow.getReversePath(), application);
        } else {
            throw new IllegalArgumentException(
                    format("Endpoint {switch_id = %s, port_number = %d, vlan_id = %d} is not a flow endpoint.",
                            switchId, port, vlan));
        }

        if (processBothEndpoints(switchId, port, vlan)) {
            sendFlowRemoveNotification(flow.getFlowId(), payload.getApplication(), flow.getSrcSwitch().getSwitchId(),
                    flow.getSrcPort(), flow.getSrcVlan());
            sendFlowRemoveNotification(flow.getFlowId(), payload.getApplication(), flow.getDestSwitch().getSwitchId(),
                    flow.getDestPort(), flow.getDestVlan());
        } else {
            sendFlowRemoveNotification(flow.getFlowId(), payload.getApplication(), switchId, port, vlan);
        }

        sendNbResponse(flow);
    }

    private void removeAppForFlowPath(Flow flow, FlowPath flowPath, FlowApplication application) {
        removeFromDatabase(flowPath, application);
        carrier.emitSpeakerCommand(buildUpdateIngressRuleCommand(flow, flowPath));
    }

    private void removeFromDatabase(FlowPath flowPath, FlowApplication application) {
        Set<FlowApplication> applications = Optional.ofNullable(flowPath.getApplications()).orElse(new HashSet<>());
        applications.remove(application);
        flowPath.setApplications(applications);
        flowPathRepository.createOrUpdate(flowPath);
    }

    private void sendFlowRemoveNotification(String flowId, String app, SwitchId switchId, Integer port, Integer vlan) {
        carrier.emitNotification(FlowApplicationRemoved.builder()
                .flowId(flowId)
                .application(app)
                .endpoint(Endpoint.builder()
                        .switchId(switchId.toString())
                        .portNumber(port)
                        .vlanId(vlan)
                        .build())
                .build());
    }

    private void sendNbResponse(Flow flow) {
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

    private boolean processBothEndpoints(SwitchId switchId, Integer port, Integer vlan) {
        return switchId == null && port == null && vlan == null;
    }

    private boolean endpointForForwardPath(Flow flow, SwitchId switchId, Integer port, Integer vlan) {
        return flow.getSrcSwitch().getSwitchId().equals(switchId)
                && Objects.equals(flow.getSrcPort(), port)
                && Objects.equals(flow.getSrcVlan(), vlan);
    }

    private boolean endpointForReversePath(Flow flow, SwitchId switchId, Integer port, Integer vlan) {
        return flow.getDestSwitch().getSwitchId().equals(switchId)
                && Objects.equals(flow.getDestPort(), port)
                && Objects.equals(flow.getDestVlan(), vlan);
    }

    private FlowApplication convertApplicationFromString(String application) {
        return FlowApplication.valueOf(application.toUpperCase());
    }

    private UpdateIngressFlow buildUpdateIngressRuleCommand(Flow flow, FlowPath flowPath) {
        List<PathSegment> segments = flowPath.getSegments();
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Neither one switch flow nor path segments provided");
        }

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitch().getSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flowId: %s", flow.getFlowId()));
        }
        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);
        return new UpdateIngressFlow(flowCommandFactory.buildInstallIngressFlow(flow, flowPath,
                ingressSegment.getSrcPort(), encapsulationResources));
    }

    private EncapsulationResources getEncapsulationResources(Flow flow, FlowPath flowPath) {
        return flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                flow.getOppositePathId(flowPath.getPathId()),
                flow.getEncapsulationType())
                .orElseThrow(() -> new IllegalStateException(
                        format("Encapsulation resources are not found for path %s", flowPath)));
    }

    /**
     * Create exclusion for the flow.
     */
    public void processCreateExclusion(CreateExclusion payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());

        SwitchId switchId = new SwitchId(payload.getEndpoint().getSwitchId());
        int port = payload.getEndpoint().getPortNumber();
        int vlan = payload.getEndpoint().getVlanId();

        FlowPath flowPath = getFlowPathByEndpoint(flow, switchId, port, vlan);
        checkFlowAppInstallation(flowPath, payload.getApplication());

        boolean flowPathIsForward = flow.isForward(flowPath);
        Cookie cookie = Cookie.buildExclusionCookie(flowPath.getCookie().getUnmaskedValue(), flowPathIsForward);

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);
        carrier.emitSpeakerCommand(InstallExclusionRequest.builder()
                .switchId(switchId)
                .cookie(cookie.getValue())
                .tunnelId(encapsulationResources.getTransitEncapsulationId())
                .srcIp(payload.getExclusion().getSrcIp())
                .srcPort(payload.getExclusion().getSrcPort())
                .dstIp(payload.getExclusion().getDstIp())
                .dstPort(payload.getExclusion().getDstPort())
                .proto(payload.getExclusion().getProto())
                .ethType(payload.getExclusion().getEthType())
                .build());
        carrier.emitNotification(CreateExclusionResult.builder()
                .flowId(payload.getFlowId())
                .endpoint(payload.getEndpoint())
                .application(payload.getApplication())
                .exclusion(payload.getExclusion())
                .success(true)
                .build());
    }

    /**
     * Remove exclusion for the flow.
     */
    public void processRemoveExclusion(RemoveExclusion payload) throws FlowNotFoundException {
        Flow flow = getFlow(payload.getFlowId());

        SwitchId switchId = new SwitchId(payload.getEndpoint().getSwitchId());
        int port = payload.getEndpoint().getPortNumber();
        int vlan = payload.getEndpoint().getVlanId();

        FlowPath flowPath = getFlowPathByEndpoint(flow, switchId, port, vlan);
        checkFlowAppInstallation(flowPath, payload.getApplication());

        boolean flowPathIsForward = flow.isForward(flowPath);
        Cookie cookie = Cookie.buildExclusionCookie(flowPath.getCookie().getUnmaskedValue(), flowPathIsForward);

        EncapsulationResources encapsulationResources = getEncapsulationResources(flow, flowPath);
        carrier.emitSpeakerCommand(RemoveExclusionRequest.builder()
                .switchId(switchId)
                .cookie(cookie.getValue())
                .tunnelId(encapsulationResources.getTransitEncapsulationId())
                .srcIp(payload.getExclusion().getSrcIp())
                .srcPort(payload.getExclusion().getSrcPort())
                .dstIp(payload.getExclusion().getDstIp())
                .dstPort(payload.getExclusion().getDstPort())
                .proto(payload.getExclusion().getProto())
                .ethType(payload.getExclusion().getEthType())
                .build());
        carrier.emitNotification(RemoveExclusionResult.builder()
                .flowId(payload.getFlowId())
                .endpoint(payload.getEndpoint())
                .application(payload.getApplication())
                .exclusion(payload.getExclusion())
                .success(true)
                .build());
    }

    private FlowPath getFlowPathByEndpoint(Flow flow, SwitchId switchId, Integer port, Integer vlan) {
        if (endpointForForwardPath(flow, switchId, port, vlan)) {
            return flow.getForwardPath();
        } else if (endpointForReversePath(flow, switchId, port, vlan)) {
            return flow.getReversePath();
        }

        throw new IllegalArgumentException(
                format("Endpoint {switch_id = %s, port_number = %d, vlan_id = %d} is not a flow endpoint.",
                switchId, port, vlan));
    }

    private void checkFlowAppInstallation(FlowPath flowPath, String application) {
        FlowApplication flowApplication = convertApplicationFromString(application);
        if (flowPath.getApplications() == null || !flowPath.getApplications().contains(flowApplication)) {
            throw new IllegalArgumentException(format("Flow application \"%s\" is not installed for the flow %s",
                    application, flowPath.getFlow().getFlowId()));
        }
    }
}

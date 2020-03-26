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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.model.FlowPathSpeakerView;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class FlowProcessingAction<T extends FlowProcessingFsm<T, S, E, C>, S, E, C>
        extends AnonymousAction<T, S, E, C> {

    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    protected final PersistenceManager persistenceManager;
    protected final FlowRepository flowRepository;
    protected final FlowPathRepository flowPathRepository;
    protected final SwitchPropertiesRepository switchPropertiesRepository;

    public FlowProcessingAction(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
    }

    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        try {
            perform(from, to, event, context, stateMachine);
        } catch (Exception ex) {
            String errorMessage = format("%s failed: %s", getClass().getSimpleName(), ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);
        }
    }

    protected abstract void perform(S from, S to, E event, C context, T stateMachine);

    protected Flow getFlow(String flowId) {
        return flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    protected Flow getFlow(String flowId, FetchStrategy fetchStrategy) {
        return flowRepository.findById(flowId, fetchStrategy)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    protected FlowPathSpeakerView getFlowPath(FlowPathSpeakerView pathSnapshot) {
        return pathSnapshot.toBuilder()
                .path(getFlowPath(pathSnapshot.getPath().getPathId()))
                .build();
    }

    protected FlowPathSpeakerView getFlowPath(Flow flow, FlowPathSpeakerView pathSnapshot) {
        return pathSnapshot.toBuilder()
                .path(getFlowPath(flow, pathSnapshot.getPath().getPathId()))
                .build();
    }

    protected FlowPath getFlowPath(Flow flow, PathId pathId) {
        return flow.getPath(pathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow path %s not found", pathId)));
    }

    protected FlowPath getFlowPath(PathId pathId) {
        return flowPathRepository.findById(pathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow path %s not found", pathId)));
    }

    protected Set<String> findFlowsIdsByEndpointWithMultiTable(SwitchId switchId, int port) {
        return new HashSet<>(flowRepository.findFlowsIdsByEndpointWithMultiTableSupport(switchId, port));
    }

    protected FlowPathSpeakerView makeFlowPathNewView(Flow flow, FlowPath path, PathResources resources)
            throws ResourceAllocationException {
        FlowPathSpeakerView.FlowPathSpeakerViewBuilder pathSnapshot = FlowPathSpeakerView.builder(path)
                .resources(resources);
        return pathSnapshot.build();
    }

    protected FlowPathSpeakerView makeFlowPathOldView(
            Flow flow, PathId pathId, PathResources resources) {
        FlowPath path = getFlowPath(pathId);  // need to ensure that all relations are loaded
        FlowPathSpeakerView.FlowPathSpeakerViewBuilder pathView = FlowPathSpeakerView.builder(path)
                .resources(resources);

        FlowEndpoint ingressEndpoint = FlowSideAdapter.makeIngressAdapter(flow, path).getEndpoint();
        SwitchProperties switchProperties = getSwitchProperties(ingressEndpoint.getSwitchId());
        pathView.removeCustomerPortRule(
                checkIfSharedCustomerPortForwardingFlowCanBeRemoved(flow.getFlowId(), ingressEndpoint));
        pathView.removeCustomerPortLldpRule(
                checkIfSharedLldpPortRuleCanBeRemoved(flow.getFlowId(), ingressEndpoint, switchProperties));
        pathView.removeCustomerPortArpRule(
                checkIfSharedArpPortRuleCanBeRemoved(flow.getFlowId(), ingressEndpoint, switchProperties));
        return pathView.build();
    }

    private boolean checkIfSharedCustomerPortForwardingFlowCanBeRemoved(String flowId, FlowEndpoint endpoint) {
        Collection<Flow> occupiedByFlows = flowRepository.findByEndpointWithMultiTableSupport(
                endpoint.getSwitchId(), endpoint.getPortNumber());
        long refsCount = filterOutFlow(occupiedByFlows.stream(), flowId).count();
        return refsCount == 0;
    }

    protected boolean checkIfSharedLldpPortRuleCanBeRemoved(
            String flowId, FlowEndpoint endpoint, SwitchProperties switchProperties) {
        int portNumber = endpoint.getPortNumber();
        Stream<Flow> flows = filterOutFlow(
                flowRepository.findByEndpoint(endpoint.getSwitchId(), portNumber).stream(),
                flowId);

        if (switchProperties.isSwitchLldp()) {
            return flows.count() == 0;
        }

        return flows.noneMatch(
                f -> f.getSrcPort() == portNumber && f.getDetectConnectedDevices().isSrcLldp()
                        || f.getDestPort() == portNumber && f.getDetectConnectedDevices().isDstLldp());
    }

    protected boolean checkIfSharedArpPortRuleCanBeRemoved(
            String flowId, FlowEndpoint endpoint, SwitchProperties switchProperties) {
        int portNumber = endpoint.getPortNumber();
        Stream<Flow> flows = filterOutFlow(
                flowRepository.findByEndpoint(endpoint.getSwitchId(), portNumber).stream(),
                flowId);

        if (switchProperties.isSwitchArp()) {
            return flows.count() == 0;
        }

        return flows
                .noneMatch(f -> f.getSrcPort() == portNumber && f.getDetectConnectedDevices().isSrcArp()
                        || f.getDestPort() == portNumber && f.getDetectConnectedDevices().isDstArp());
    }

    private SwitchProperties getSwitchProperties(SwitchId switchId) {
        return switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Properties for switch %s not found", switchId)));
    }

    protected Set<String> getDiverseWithFlowIds(Flow flow) {
        return flow.getGroupId() == null ? Collections.emptySet() :
                flowRepository.findFlowsIdByGroupId(flow.getGroupId()).stream()
                        .filter(flowId -> !flowId.equals(flow.getFlowId()))
                        .collect(Collectors.toSet());
    }

    private Stream<Flow> filterOutFlow(Stream<Flow> stream, String flowId) {
        return stream.filter(entry -> !entry.getFlowId().equals(flowId));
    }
}

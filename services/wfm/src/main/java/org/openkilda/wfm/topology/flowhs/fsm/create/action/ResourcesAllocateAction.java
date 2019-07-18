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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowEventData.Initiator;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ResourcesAllocateAction extends NbTrackableAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final PathComputer pathComputer;
    private final TransactionManager transactionManager;
    private final FlowResourcesManager resourcesManager;
    private final FlowRepository flowRepository;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;
    private final FlowPathRepository flowPathRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ResourcesAllocateAction(PathComputer pathComputer, PersistenceManager persistenceManager,
                                   FlowResourcesManager resourcesManager,
                                   FlowOperationsDashboardLogger dashboardLogger) {
        this.pathComputer = pathComputer;
        this.transactionManager = persistenceManager.getTransactionManager();
        this.resourcesManager = resourcesManager;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> perform(State from, State to, FlowCreateFsm.Event event, FlowCreateContext context,
                                        FlowCreateFsm stateMachine) throws FlowProcessingException {
        log.debug("Allocation resources has been started");

        Flow flow = RequestedFlowMapper.INSTANCE.toFlow(context.getFlowDetails());
        CommandContext commandContext = stateMachine.getCommandContext();
        flow.setStatus(FlowStatus.IN_PROGRESS);
        flow.setSrcSwitch(switchRepository.reload(flow.getSrcSwitch()));
        flow.setDestSwitch(switchRepository.reload(flow.getDestSwitch()));

        dashboardLogger.onFlowCreate(flow);

        PathPair pathPair = findPath(flow);

        try {
            allocateResourcesForFlow(flow, pathPair);
            saveHistory(flow, stateMachine);
            stateMachine.setFlowId(flow.getFlowId());

            InfoData flowData = new FlowResponse(FlowMapper.INSTANCE.map(flow));
            Message response = new InfoMessage(flowData, commandContext.getCreateTime(),
                    commandContext.getCorrelationId());

            if (flow.isOneSwitchFlow()) {
                stateMachine.fire(Event.SKIP_NON_INGRESS_RULES_INSTALL);
            } else {
                stateMachine.fireNext(context);
            }

            return Optional.of(response);
        } catch (ResourceAllocationException e) {
            log.error("Failed to allocate resources", e);
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR, "Could not create flow", e.getMessage());
        }
    }

    private PathPair findPath(Flow flow) throws FlowProcessingException {
        try {
            return pathComputer.getPath(flow);
        } catch (UnroutableFlowException | RecoverableException e) {
            log.debug("Can't find a path for flow {}", flow);

            throw new FlowProcessingException(ErrorType.NOT_FOUND, "Could not create flow",
                    "Not enough bandwidth found or path not found : " + e.getMessage());
        }
    }

    private void allocateResourcesForFlow(Flow flow, PathPair pathPair) throws ResourceAllocationException {
        flow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        FlowResources flowResources = resourcesManager.allocateFlowResources(flow);

        long cookie = flowResources.getUnmaskedCookie();
        flow.setForwardPath(buildFlowPath(flow, flowResources.getForward(), pathPair.getForward(),
                Cookie.buildForwardCookie(cookie)));
        flow.setReversePath(buildFlowPath(flow, flowResources.getReverse(), pathPair.getReverse(),
                Cookie.buildReverseCookie(cookie)));

        transactionManager.doInTransaction(() -> {
            lockSwitchesForFlow(flow);
            flowRepository.createOrUpdate(flow);
            updateIslsForFlowPath(flow.getForwardPath());
            updateIslsForFlowPath(flow.getReversePath());
        });
        log.debug("Resources allocated successfully for the flow {}", flow.getFlowId());
    }

    private FlowPath buildFlowPath(Flow flow, PathResources pathResources, Path path, Cookie cookie) {
        FlowPath result = FlowPath.builder()
                .flow(flow)
                .pathId(pathResources.getPathId())
                .srcSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getSrcSwitchId()).build()))
                .destSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getDestSwitchId()).build()))
                .meterId(pathResources.getMeterId())
                .bandwidth(flow.getBandwidth())
                .cookie(cookie)
                .build();

        result.setSegments(path.getSegments().stream()
                .map(segment -> PathSegment.builder()
                        .srcSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getSrcSwitchId()).build()))
                        .srcPort(segment.getSrcPort())
                        .destSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getDestSwitchId()).build()))
                        .destPort(segment.getDestPort())
                        .latency(segment.getLatency())
                        .build())
                .collect(Collectors.toList()));

        return result;
    }

    private void updateIslsForFlowPath(FlowPath path) {
        path.getSegments().forEach(pathSegment -> {
            log.debug("Updating ISL for the path segment: {}", pathSegment);

            updateAvailableBandwidth(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                    pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
        });
    }

    private void updateAvailableBandwidth(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(srcSwitch, srcPort,
                dstSwitch, dstPort);

        Optional<Isl> matchedIsl = islRepository.findByEndpoints(srcSwitch, srcPort, dstSwitch, dstPort);
        matchedIsl.ifPresent(isl -> {
            isl.setAvailableBandwidth(isl.getMaxBandwidth() - usedBandwidth);
            islRepository.createOrUpdate(isl);
        });
    }

    private void lockSwitchesForFlow(Flow flow) {
        List<PathSegment> forwardSegments = flow.getForwardPath().getSegments();
        List<PathSegment> reverseSegments = flow.getReversePath().getSegments();
        Switch[] switches = ListUtils.union(forwardSegments, reverseSegments).stream()
                .map(PathSegment::getSrcSwitch)
                .toArray(Switch[]::new);

        switchRepository.lockSwitches(switches);
    }

    private void saveHistory(Flow flow, FlowCreateFsm stateMachine) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Resources allocated")
                        .time(Instant.now())
                        .flowId(flow.getFlowId())
                        .build())
                .flowEventData(FlowEventData.builder()
                        .initiator(Initiator.NB)
                        .flowId(flow.getFlowId())
                        .event(FlowEventData.Event.CREATE)
                        .time(Instant.now())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}

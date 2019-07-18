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

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowAlreadyExistException;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowEventData.Initiator;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.v1.exceptions.TransientException;

import java.time.Instant;
import java.util.Optional;

@Slf4j
public class ResourcesAllocationAction extends NbTrackableAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private static final int MAX_TRANSACTION_RETRY_COUNT = 3;

    private final PathComputer pathComputer;
    private final TransactionManager transactionManager;
    private final FlowResourcesManager resourcesManager;
    private final FlowRepository flowRepository;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;
    private final FlowPathRepository flowPathRepository;
    private final FlowPathBuilder flowPathBuilder;

    public ResourcesAllocationAction(PathComputer pathComputer, PersistenceManager persistenceManager,
                                     FlowResourcesManager resourcesManager) {
        this.pathComputer = pathComputer;
        this.transactionManager = persistenceManager.getTransactionManager();
        this.resourcesManager = resourcesManager;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();

        this.flowPathBuilder = new FlowPathBuilder(switchRepository);
    }

    @Override
    protected Optional<Message> perform(State from, State to, FlowCreateFsm.Event event, FlowCreateContext context,
                                        FlowCreateFsm stateMachine) throws FlowProcessingException {
        log.debug("Allocation resources has been started");

        Flow flow = RequestedFlowMapper.INSTANCE.toFlow(context.getTargetFlow());
        flow.setStatus(FlowStatus.IN_PROGRESS);
        flow.setSrcSwitch(switchRepository.reload(flow.getSrcSwitch()));
        flow.setDestSwitch(switchRepository.reload(flow.getDestSwitch()));

        try {
            createFlowWithPaths(stateMachine, flow, context.getTargetFlow().getDiverseFlowId());
        } catch (UnroutableFlowException e) {
            String message = "Not enough bandwidth found or path not found : " + e.getMessage();
            log.debug("{}: {}", getGenericErrorMessage(), message);
            throw new FlowProcessingException(ErrorType.NOT_FOUND, getGenericErrorMessage(), message);
        } catch (ResourceAllocationException e) {
            log.error("{}: Failed to allocate flow resources", getGenericErrorMessage(), e);
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR, getGenericErrorMessage(), e.getMessage());
        } catch (FlowNotFoundException e) {
            log.debug("{}: {}", getGenericErrorMessage(), e.getMessage());
            throw new FlowProcessingException(ErrorType.NOT_FOUND, getGenericErrorMessage(),
                    "Can't find the diverse flow: " + e.getMessage());
        } catch (FlowAlreadyExistException e) {
            log.info(e.getMessage(), e);
            if (!stateMachine.retryIfAllowed()) {
                stateMachine.fire(Event.RETRY);
                throw new FlowProcessingException(ErrorType.INTERNAL_ERROR, getGenericErrorMessage(), e.getMessage());
            }
        }

        saveHistory(flow, stateMachine);
        CommandContext commandContext = stateMachine.getCommandContext();
        InfoData flowData = new FlowResponse(FlowMapper.INSTANCE.map(flow));
        Message response = new InfoMessage(flowData, commandContext.getCreateTime(),
                commandContext.getCorrelationId());

        if (flow.isOneSwitchFlow()) {
            stateMachine.fire(Event.SKIP_NON_INGRESS_RULES_INSTALL);
        } else {
            stateMachine.fireNext(context);
        }
        return Optional.of(response);
    }

    private void createFlowWithPaths(FlowCreateFsm fsm, Flow flow, String diverseFlowId) throws UnroutableFlowException,
            ResourceAllocationException, FlowNotFoundException, FlowAlreadyExistException {
        try {
            Failsafe.with(new RetryPolicy()
                    .retryOn(RecoverableException.class)
                    .retryOn(ResourceAllocationException.class)
                    .retryOn(TransientException.class)
                    .withMaxRetries(MAX_TRANSACTION_RETRY_COUNT))
                    .onRetry(e -> log.warn("Retrying transaction for resource allocation finished with exception", e))
                    .onRetriesExceeded(e -> log.warn("TX retry attempts exceed with error", e))
                    .run(() -> transactionManager.doInTransaction(() -> {
                        allocateMainPath(fsm, flow, diverseFlowId);
                        if (flow.isAllocateProtectedPath()) {
                            allocateProtectedPath(fsm, flow);
                        }
                    }));
        } catch (FailsafeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof UnroutableFlowException) {
                throw (UnroutableFlowException) cause;
            } else if (cause instanceof ResourceAllocationException) {
                throw (ResourceAllocationException) cause;
            } else if (cause instanceof FlowNotFoundException) {
                throw (FlowNotFoundException) cause;
            } else {
                throw ex;
            }
        } catch (ConstraintViolationException e) {
            throw new FlowAlreadyExistException(format("Failed to save flow with id %s", flow.getFlowId()), e);
        }
        log.debug("Resources allocated successfully for the flow {}", flow.getFlowId());
    }

    private void allocateMainPath(FlowCreateFsm fsm, Flow flow, String diverseFlowId) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException, FlowNotFoundException {
        if (StringUtils.isNotBlank(diverseFlowId)) {
            flow.setGroupId(getGroupId(diverseFlowId));
        }

        PathPair pathPair = pathComputer.getPath(flow);
        FlowResources flowResources = resourcesManager.allocateFlowResources(flow);

        long cookie = flowResources.getUnmaskedCookie();
        FlowPath forward = flowPathBuilder.buildFlowPath(flow, flowResources.getForward(),
                pathPair.getForward(), Cookie.buildForwardCookie(cookie));
        forward.setStatus(FlowPathStatus.IN_PROGRESS);
        flow.setForwardPath(forward);

        FlowPath reverse = flowPathBuilder.buildFlowPath(flow, flowResources.getReverse(),
                pathPair.getReverse(), Cookie.buildReverseCookie(cookie));
        reverse.setStatus(FlowPathStatus.IN_PROGRESS);
        flow.setReversePath(reverse);

        flowPathRepository.lockInvolvedSwitches(forward, reverse);
        flowRepository.createOrUpdate(flow);

        updateIslsForFlowPath(forward);
        updateIslsForFlowPath(reverse);

        fsm.setForwardPathId(forward.getPathId());
        fsm.setReversePathId(reverse.getPathId());
        log.debug("Allocated resources for the flow {}: {}", flow.getFlowId(), flowResources);

        fsm.getFlowResources().clear();
        fsm.getFlowResources().add(flowResources);
    }

    private void allocateProtectedPath(FlowCreateFsm fsm, Flow flow) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException, FlowNotFoundException {
        flow.setGroupId(getGroupId(flow.getFlowId()));
        PathPair protectedPath = pathComputer.getPath(flow);

        boolean overlappingProtectedPathFound =
                flowPathBuilder.arePathsOverlapped(protectedPath.getForward(), flow.getForwardPath())
                        || flowPathBuilder.arePathsOverlapped(protectedPath.getReverse(), flow.getReversePath());
        if (overlappingProtectedPathFound) {
            log.info("Couldn't find non overlapping protected path. Result flow state: {}", flow);
            throw new UnroutableFlowException("Couldn't find non overlapping protected path", flow.getFlowId());
        }

        log.debug("Creating the protected path {} for flow {}", protectedPath, flow);

        FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
        Cookie forwardCookie = Cookie.buildForwardCookie(flowResources.getUnmaskedCookie());

        FlowPath forward = flowPathBuilder.buildFlowPath(flow, flowResources.getForward(),
                protectedPath.getForward(), forwardCookie);
        forward.setStatus(FlowPathStatus.IN_PROGRESS);
        flow.setProtectedForwardPath(forward);
        fsm.setProtectedForwardPathId(forward.getPathId());

        Cookie reverseCookie = Cookie.buildReverseCookie(flowResources.getUnmaskedCookie());
        FlowPath reverse = flowPathBuilder.buildFlowPath(flow, flowResources.getReverse(),
                protectedPath.getReverse(), reverseCookie);
        reverse.setStatus(FlowPathStatus.IN_PROGRESS);
        flow.setProtectedReversePath(reverse);
        fsm.setProtectedReversePathId(reverse.getPathId());

        flowPathRepository.lockInvolvedSwitches(forward, reverse);
        flowRepository.createOrUpdate(flow);

        updateIslsForFlowPath(forward);
        updateIslsForFlowPath(reverse);
        fsm.getFlowResources().add(flowResources);
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

    private String getGroupId(String flowId) throws FlowNotFoundException {
        return flowRepository.getOrCreateFlowGroupId(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));
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

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow";
    }
}

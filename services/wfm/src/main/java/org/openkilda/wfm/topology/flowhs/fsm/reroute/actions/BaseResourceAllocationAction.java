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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A base for action classes that allocate resources for flow paths.
 */
@Slf4j
abstract class BaseResourceAllocationAction extends
        NbTrackableAction<FlowRerouteFsm, FlowRerouteFsm.State, Event, FlowRerouteContext> {
    private static final int MAX_TRANSACTION_RETRY_COUNT = 3;

    protected final TransactionManager transactionManager;

    protected final FlowRepository flowRepository;
    protected final FlowPathRepository flowPathRepository;
    protected final IslRepository islRepository;

    protected final PathComputer pathComputer;
    protected final FlowResourcesManager resourcesManager;
    protected final FlowPathBuilder flowPathBuilder;

    BaseResourceAllocationAction(PersistenceManager persistenceManager, PathComputer pathComputer,
                                 FlowResourcesManager resourcesManager) {
        this.transactionManager = persistenceManager.getTransactionManager();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        this.pathComputer = pathComputer;
        this.resourcesManager = resourcesManager;

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowPathBuilder = new FlowPathBuilder(switchRepository);
    }

    @Override
    protected final Optional<Message> perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                                              Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        if (!isAllocationRequired(context, stateMachine)) {
            return Optional.empty();
        }

        try {
            allocateInTransaction(context, stateMachine);

            return Optional.empty();
        } catch (UnroutableFlowException | RecoverableException e) {
            String errorDescription = format("Not enough bandwidth or no path found for flow %s: %s",
                    stateMachine.getFlowId(), e.getMessage());
            log.debug(getGenericErrorMessage() + ": " + errorDescription);

            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(), errorDescription);

            stateMachine.fire(Event.NO_PATH_FOUND);

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.NOT_FOUND,
                    getGenericErrorMessage(), errorDescription));
        } catch (ResourceAllocationException e) {
            String errorDescription = format("Failed to allocate resources for flow %s: %s",
                    stateMachine.getFlowId(), e.getMessage());
            log.warn(getGenericErrorMessage() + ": " + errorDescription);

            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(), errorDescription);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.INTERNAL_ERROR,
                    getGenericErrorMessage(), errorDescription));
        } catch (Exception e) {
            String errorDescription = format("Failed to create a new path for flow %s: %s",
                    stateMachine.getFlowId(), e.getMessage());
            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(), errorDescription);

            throw e;
        }
    }

    /**
     * Check whether allocation is required, otherwise it's being skipped.
     */
    protected abstract boolean isAllocationRequired(FlowRerouteContext context, FlowRerouteFsm stateMachine);

    /**
     * Perform resource allocation, returns the allocated resources.
     */
    protected abstract void allocate(FlowRerouteContext context, FlowRerouteFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException;

    /**
     * Perform resource allocation in transactions, returns the allocated resources.
     */
    @SneakyThrows
    private void allocateInTransaction(FlowRerouteContext context, FlowRerouteFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverableException.class)
                .retryOn(ResourceAllocationException.class)
                .retryOn(RecoverablePersistenceException.class)
                .withMaxRetries(MAX_TRANSACTION_RETRY_COUNT);

        try {
            transactionManager.doInTransaction(retryPolicy, () -> allocate(context, stateMachine));
        } catch (FailsafeException e) {
            throw e.getCause();
        }
    }

    protected PathPair findPath(Flow flow) throws RecoverableException, UnroutableFlowException {
        return pathComputer.getPath(flow,
                Stream.of(flow.getForwardPathId(), flow.getReversePathId(),
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    protected boolean isNotSamePath(PathPair pathPair, FlowPath forwardPath, FlowPath reversePath) {
        return !flowPathBuilder.isSamePath(pathPair.getForward(), forwardPath)
                || !flowPathBuilder.isSamePath(pathPair.getReverse(), reversePath);
    }

    protected FlowPathPair createFlowPathPair(Flow flow, PathPair pathPair, FlowResources flowResources) {
        long cookie = flowResources.getUnmaskedCookie();
        FlowPath newForwardPath = flowPathBuilder.buildFlowPath(flow, flowResources.getForward(),
                pathPair.getForward(), Cookie.buildForwardCookie(cookie));
        newForwardPath.setStatus(FlowPathStatus.IN_PROGRESS);
        FlowPath newReversePath = flowPathBuilder.buildFlowPath(flow, flowResources.getReverse(),
                pathPair.getReverse(), Cookie.buildReverseCookie(cookie));
        newReversePath.setStatus(FlowPathStatus.IN_PROGRESS);
        FlowPathPair newFlowPaths = FlowPathPair.builder().forward(newForwardPath).reverse(newReversePath).build();

        log.debug("Persisting the paths {}", newFlowPaths);

        transactionManager.doInTransaction(() -> {
            flowPathRepository.lockInvolvedSwitches(newForwardPath, newReversePath);

            flowPathRepository.createOrUpdate(newForwardPath);
            flowPathRepository.createOrUpdate(newReversePath);

            updateIslsForFlowPath(newForwardPath, newReversePath);
        });

        return newFlowPaths;
    }

    private void updateIslsForFlowPath(FlowPath... paths) {
        for (FlowPath path : paths) {
            path.getSegments().forEach(pathSegment -> {
                log.debug("Updating ISL for the path segment: {}", pathSegment);

                updateAvailableBandwidth(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                        pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
            });
        }
    }

    private void updateAvailableBandwidth(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(srcSwitch, srcPort,
                dstSwitch, dstPort);
        log.debug("Updating ISL {}_{}-{}_{} with used bandwidth {}", srcSwitch, srcPort, dstSwitch, dstPort,
                usedBandwidth);
        long islAvailableBandwidth =
                islRepository.updateAvailableBandwidth(srcSwitch, srcPort, dstSwitch, dstPort, usedBandwidth);
        if (islAvailableBandwidth < 0) {
            throw new RecoverablePersistenceException(format("ISL %s_%d-%s_%d was overprovisioned",
                    srcSwitch, srcPort, dstSwitch, dstPort));
        }
    }

    protected void saveHistory(Flow flow, FlowPathPair oldFlowPaths, FlowPathPair newFlowPaths,
                               FlowRerouteFsm stateMachine) {
        Instant timestamp = Instant.now();
        FlowDumpData oldDumpData = HistoryMapper.INSTANCE.map(flow,
                oldFlowPaths.getForward(), oldFlowPaths.getReverse());
        oldDumpData.setDumpType(DumpType.STATE_BEFORE);

        FlowDumpData newDumpData = HistoryMapper.INSTANCE.map(flow,
                newFlowPaths.getForward(), newFlowPaths.getReverse());
        newDumpData.setDumpType(DumpType.STATE_AFTER);

        Stream.of(oldDumpData, newDumpData).forEach(dumpData -> {
            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(stateMachine.getCommandContext().getCorrelationId())
                    .flowDumpData(dumpData)
                    .flowHistoryData(FlowHistoryData.builder()
                            .action("New paths were created (with allocated resources)")
                            .time(timestamp)
                            .flowId(flow.getFlowId())
                            .build())
                    .flowEventData(FlowEventData.builder()
                            .flowId(flow.getFlowId())
                            .event(FlowEventData.Event.REROUTE)
                            .time(timestamp)
                            .build())
                    .build();
            stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
        });
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }
}

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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static java.lang.String.format;
import static org.openkilda.model.FlowPathDirection.FORWARD;
import static org.openkilda.model.FlowPathDirection.REVERSE;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.IslRepository.IslEndpoints;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

@Slf4j
public class ResourceAllocationAction
        extends NbTrackableWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event,
        FlowMirrorPointCreateContext> {

    private final int pathAllocationRetriesLimit;
    private final int pathAllocationRetryDelay;
    private final int resourceAllocationRetriesLimit;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final FlowMirrorRepository flowMirrorRepository;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final FlowResourcesManager resourcesManager;
    private final PathComputer pathComputer;
    private final FlowPathBuilder flowPathBuilder;


    public ResourceAllocationAction(PersistenceManager persistenceManager,
                                    int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                    int resourceAllocationRetriesLimit,
                                    PathComputer pathComputer, FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.pathAllocationRetriesLimit = pathAllocationRetriesLimit;
        this.pathAllocationRetryDelay = pathAllocationRetryDelay;
        this.resourceAllocationRetriesLimit = resourceAllocationRetriesLimit;
        this.flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
        this.flowMirrorRepository = persistenceManager.getRepositoryFactory().createFlowMirrorRepository();
        this.flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
        this.resourcesManager = resourcesManager;
        this.pathComputer = pathComputer;
        this.flowPathBuilder = new FlowPathBuilder(switchPropertiesRepository,
                persistenceManager.getRepositoryFactory().createKildaConfigurationRepository());
    }

    @Override
    protected Optional<Message> performWithResponse(
            State from, State to, Event event, FlowMirrorPointCreateContext context,
            FlowMirrorPointCreateFsm stateMachine) {
        try {
            allocate(stateMachine);
            return Optional.empty();
        } catch (UnroutableFlowException | RecoverableException e) {
            throw new FlowProcessingException(ErrorType.NOT_FOUND,
                    "Not enough bandwidth or no path found. " + e.getMessage(), e);
        } catch (ResourceAllocationException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    "Failed to allocate flow mirror resources. " + e.getMessage(), e);
        }
    }

    private void allocate(FlowMirrorPointCreateFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        RequestedFlowMirrorPoint mirrorPoint = stateMachine.getRequestedFlowMirrorPoint();

        Flow flow = transactionManager.doInTransaction(() -> {
            Flow foundFlow = getFlow(mirrorPoint.getFlowId());
            FlowPath flowPath = getFlowPath(mirrorPoint, foundFlow);
            stateMachine.setFlowPathId(flowPath.getPathId());

            Optional<FlowMirrorPoints> foundFlowMirrorPoints = flowMirrorPointsRepository
                    .findByPathIdAndSwitchId(flowPath.getPathId(), mirrorPoint.getMirrorPointSwitchId());

            FlowMirrorPoints flowMirrorPoints;

            if (foundFlowMirrorPoints.isPresent()) {
                flowMirrorPoints = foundFlowMirrorPoints.get();
                stateMachine.setAddNewGroup(false);
            } else {
                flowMirrorPoints = createFlowMirrorPoints(mirrorPoint, flowPath);
                stateMachine.setAddNewGroup(true);
            }

            FlowMirror flowMirror = createFlowMirror(stateMachine, mirrorPoint, flowMirrorPoints);
            flowMirrorPoints.addFlowMirrors(flowMirror);
            return foundFlow;
        });

        allocatePath(stateMachine, flow);
        stateMachine.saveActionToHistory("New mirror path was created",
                format("The flow mirror path %s was created (with allocated resources)",
                        stateMachine.getFlowMirrorId()));
    }

    private FlowMirrorPath createFlowMirrorPath(
            Flow flow, Switch mirrorSwitch, Switch sinkSwitch, FlowSegmentCookie cookie, PathResources resources,
            Path pathForSegments, String flowMirrorId, boolean dummy) {

        FlowMirrorPath flowMirrorPath = FlowMirrorPath.builder()
                .mirrorPathId(resources.getPathId())
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(sinkSwitch)
                .cookie(cookie)
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .status(FlowPathStatus.IN_PROGRESS)
                .egressWithMultiTable(true)
                .dummy(dummy) //TODO set meter ID in patch with reverse mirror
                .segments(flowPathBuilder.buildPathSegments(
                        resources.getPathId(), pathForSegments, flow.getBandwidth(),
                        flow.isIgnoreBandwidth(), flowMirrorId))
                .build();

        flowMirrorPathRepository.add(flowMirrorPath);
        return flowMirrorPath;
    }

    private FlowMirror createFlowMirror(
            FlowMirrorPointCreateFsm stateMachine, RequestedFlowMirrorPoint mirrorPoint,
            FlowMirrorPoints flowMirrorPoints) {
        Switch sinkSwitch = getSwitch(mirrorPoint.getSinkEndpoint().getSwitchId());
        FlowMirror flowMirror = FlowMirror.builder()
                .flowMirrorId(stateMachine.getFlowMirrorId())
                .mirrorSwitch(flowMirrorPoints.getMirrorSwitch())
                .egressSwitch(sinkSwitch)
                .egressPort(mirrorPoint.getSinkEndpoint().getPortNumber())
                .egressOuterVlan(mirrorPoint.getSinkEndpoint().getOuterVlanId())
                .egressInnerVlan(mirrorPoint.getSinkEndpoint().getInnerVlanId())
                .status(FlowPathStatus.IN_PROGRESS)
                .build();
        flowMirrorRepository.add(flowMirror);
        return flowMirror;
    }

    private FlowPath getFlowPath(RequestedFlowMirrorPoint mirrorPoint, Flow flow) {
        switch (mirrorPoint.getMirrorPointDirection()) {
            case FORWARD:
                return flow.getForwardPath();
            case REVERSE:
                return flow.getReversePath();
            default:
                throw new IllegalArgumentException(format("Flow mirror points direction %s is not supported",
                        mirrorPoint.getMirrorPointDirection()));
        }
    }

    private FlowMirrorPoints createFlowMirrorPoints(RequestedFlowMirrorPoint mirrorPoint, FlowPath flowPath)
            throws ResourceAllocationException {
        Switch mirrorSwitch = switchRepository.findById(mirrorPoint.getMirrorPointSwitchId())
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Switch %s not found", mirrorPoint.getMirrorPointSwitchId())));

        MirrorDirection direction = mirrorPoint.getMirrorPointSwitchId().equals(flowPath.getSrcSwitchId())
                ? MirrorDirection.INGRESS : MirrorDirection.EGRESS;
        MirrorGroup mirrorGroup = resourcesManager
                .getAllocatedMirrorGroup(mirrorPoint.getMirrorPointSwitchId(), mirrorPoint.getFlowId(),
                        flowPath.getPathId(), MirrorGroupType.TRAFFIC_INTEGRITY, direction);

        FlowMirrorPoints flowMirrorPoints = FlowMirrorPoints.builder()
                .mirrorSwitch(mirrorSwitch)
                .mirrorGroup(mirrorGroup)
                .build();

        flowMirrorPointsRepository.add(flowMirrorPoints);
        flowPath.addFlowMirrorPoints(flowMirrorPoints);

        return flowMirrorPoints;
    }

    private void allocatePath(FlowMirrorPointCreateFsm stateMachine, Flow flow)
            throws UnroutableFlowException, RecoverableException, ResourceAllocationException {
        GetPathsResult paths = findPaths(stateMachine);
        stateMachine.setBackUpPathComputationWayUsed(paths.isBackUpPathComputationWayUsed());
        FlowResources resources = allocateMirrorResources(flow, stateMachine.getRequestedFlowMirrorPoint());

        log.debug("Creating the primary path {} for flow {}", paths, stateMachine.getFlowId());

        transactionManager.doInTransaction(() -> {
            RequestedFlowMirrorPoint mirrorPoint = stateMachine.getRequestedFlowMirrorPoint();
            final FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                    .flowEffectiveId(resources.getUnmaskedCookie()).mirror(true);

            Switch mirrorSwitch = getSwitch(mirrorPoint.getMirrorPointSwitchId());
            Switch sinkSwitch = getSwitch(mirrorPoint.getSinkEndpoint().getSwitchId());
            FlowMirror flowMirror = getFlowMirror(stateMachine.getFlowMirrorId());

            FlowMirrorPath forwardFlowMirrorPath = createFlowMirrorPath(
                    flow, mirrorSwitch, sinkSwitch, cookieBuilder.direction(FORWARD).build(),
                    resources.getForward(), paths.getForward(), flowMirror.getFlowMirrorId(), false);
            flowMirror.setForwardPath(forwardFlowMirrorPath);

            FlowMirrorPath reverseFlowMirrorPath = createFlowMirrorPath(
                    flow, mirrorSwitch, sinkSwitch, cookieBuilder.direction(REVERSE).build(), resources.getReverse(),
                    paths.getReverse(), flowMirror.getFlowMirrorId(), true);
            flowMirror.setReversePath(reverseFlowMirrorPath);

            if (!flow.isIgnoreBandwidth()) {
                updateIslsForFlowPath(forwardFlowMirrorPath.getMirrorPathId());
                updateIslsForFlowPath(reverseFlowMirrorPath.getMirrorPathId());
            }

            stateMachine.setForwardMirrorPathId(forwardFlowMirrorPath.getMirrorPathId());
            stateMachine.setReverseMirrorPathId(reverseFlowMirrorPath.getMirrorPathId());
            log.debug("Allocated resources for the mirror paths {} and {} of flow mirror {}: {}",
                    forwardFlowMirrorPath.getMirrorPathId(), reverseFlowMirrorPath.getMirrorPathId(),
                    flowMirror.getFlowMirrorId(), resources);
            stateMachine.setFlowResources(resources);
        });
    }

    @SneakyThrows
    private GetPathsResult findPaths(FlowMirrorPointCreateFsm stateMachine)
            throws UnroutableFlowException, RecoverableException, ResourceAllocationException, FlowProcessingException {
        FlowMirror flowMirror = getFlowMirror(stateMachine.getFlowMirrorId());
        RetryPolicy<GetPathsResult> pathAllocationRetryPolicy = new RetryPolicy<GetPathsResult>()
                .handle(RecoverableException.class)
                .handle(ResourceAllocationException.class)
                .handle(UnroutableFlowException.class)
                .handle(PersistenceException.class)
                .onRetry(e -> log.warn("Failure in resource allocation. Retrying #{}...", e.getAttemptCount(),
                        e.getLastFailure()))
                .onRetriesExceeded(e -> log.warn("Failure in resource allocation. No more retries", e.getFailure()))
                .withMaxRetries(pathAllocationRetriesLimit);
        if (pathAllocationRetryDelay > 0) {
            pathAllocationRetryPolicy.withDelay(Duration.ofMillis(pathAllocationRetryDelay));
        }
        try {
            GetPathsResult result = Failsafe.with(pathAllocationRetryPolicy).get(() -> pathComputer.getPath(
                    getFlow(stateMachine.getFlowId()), flowMirror));
            stateMachine.setBackUpPathComputationWayUsed(result.isBackUpPathComputationWayUsed());
            return result;
        } catch (FailsafeException ex) {
            throw ex.getCause();
        }
    }


    @SneakyThrows
    protected FlowResources allocateMirrorResources(Flow flow, RequestedFlowMirrorPoint mirrorPoint)
            throws ResourceAllocationException {
        RetryPolicy<FlowResources> resourceAllocationRetryPolicy =
                transactionManager.<FlowResources>getDefaultRetryPolicy()
                        .handle(ResourceAllocationException.class)
                        .handle(ConstraintViolationException.class)
                        .onRetry(e -> log.warn("Failure in resource allocation. Retrying #{}...", e.getAttemptCount(),
                                e.getLastFailure()))
                        .onRetriesExceeded(e -> log.warn("Failure in resource allocation. No more retries",
                                e.getFailure()))
                        .withMaxRetries(resourceAllocationRetriesLimit);
        FlowResources resources = transactionManager.doInTransaction(resourceAllocationRetryPolicy,
                () -> resourcesManager.allocateFlowMirrorResources(
                        flow, mirrorPoint.getMirrorPointId(), mirrorPoint.getMirrorPointSwitchId(),
                        mirrorPoint.getSinkEndpoint().getSwitchId(), false));
        //TODO use bidirectional in next part
        log.debug("Flow mirror resources have been allocated: {}", resources);
        return resources;
    }

    private void updateIslsForFlowPath(PathId pathId) throws ResourceAllocationException {
        Map<IslEndpoints, Long> updatedIsls = islRepository.updateAvailableBandwidthOnIslsOccupiedByPath(pathId);
        for (Entry<IslEndpoints, Long> entry : updatedIsls.entrySet()) {
            IslEndpoints isl = entry.getKey();
            if (entry.getValue() < 0) {
                throw new ResourceAllocationException(format("ISL %s_%d-%s_%d was over-provisioned",
                        isl.getSrcSwitch(), isl.getSrcPort(), isl.getDestSwitch(), isl.getDestPort()));
            }
        }
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow mirror point";
    }
}

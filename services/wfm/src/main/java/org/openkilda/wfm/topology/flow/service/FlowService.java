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

package org.openkilda.wfm.topology.flow.service;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.ListUtils.union;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.flow.model.FlowPairWithSegments;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowPairWithSegments;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class FlowService extends BaseFlowService {
    private SwitchRepository switchRepository;
    private FlowSegmentRepository flowSegmentRepository;
    private IslRepository islRepository;
    private PathComputerFactory pathComputerFactory;
    private FlowResourcesManager flowResourcesManager;
    private FlowValidator flowValidator;

    public FlowService(PersistenceManager persistenceManager, PathComputerFactory pathComputerFactory,
                       FlowResourcesManager flowResourcesManager, FlowValidator flowValidator) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
        islRepository = repositoryFactory.createIslRepository();
        this.pathComputerFactory = requireNonNull(pathComputerFactory);
        this.flowResourcesManager = requireNonNull(flowResourcesManager);
        this.flowValidator = requireNonNull(flowValidator);
    }

    /**
     * Creates a flow by allocating a path and resources. Stores the flow entities into DB, and
     * invokes flow rules installation via the command sender.
     * <p/>
     * The flow is created with IN_PROGRESS status.
     *
     * @param flow   the flow to be created.
     * @param diverseFlowId the flow id to build diverse group.
     * @param sender the command sender for flow rules installation.
     * @return the created flow with the path and resources set.
     */
    public FlowPair createFlow(Flow flow, String diverseFlowId, FlowCommandSender sender) throws RecoverableException,
            UnroutableFlowException, FlowAlreadyExistException, FlowValidationException, SwitchValidationException,
            FlowNotFoundException {
        flowValidator.validate(flow);

        if (doesFlowExist(flow.getFlowId())) {
            throw new FlowAlreadyExistException(flow.getFlowId());
        }

        if (diverseFlowId != null) {
            checkDiverseFlow(flow, diverseFlowId);
            flow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
        }

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(flow);

        flow.setStatus(FlowStatus.IN_PROGRESS);

        log.info("Creating the flow {} with path: {}", flow, pathPair);

        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            FlowPair flowPair = flowResourcesManager.allocateFlow(buildFlowPair(flow, pathPair));
            flowPair.setTimeCreate(Instant.now());

            List<FlowSegment> forwardSegments = buildFlowSegments(flowPair.getForward());
            List<FlowSegment> reverseSegments = buildFlowSegments(flowPair.getReverse());
            List<FlowSegment> flowSegments = union(forwardSegments, reverseSegments);

            lockSwitches(flowSegments);

            flowRepository.createOrUpdate(flowPair);
            createFlowSegments(flowSegments);

            return new FlowPairWithSegments(flowPair, forwardSegments, reverseSegments);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Stores a flow and related entities into DB, and invokes flow rules installation via the command sender.
     *
     * @param flowPair the flow to be saved.
     * @param sender   the command sender for flow rules installation.
     */
    public void saveFlow(FlowPair flowPair, FlowCommandSender sender) throws FlowAlreadyExistException {
        Flow forward = flowPair.getForward();
        Flow reverse = flowPair.getReverse();

        if (doesFlowExist(forward.getFlowId())) {
            throw new FlowAlreadyExistException(forward.getFlowId());
        }

        log.info("Saving (pushing) the flow: {}", flowPair);

        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            List<FlowSegment> forwardSegments = buildFlowSegments(forward);
            List<FlowSegment> reverseSegments = buildFlowSegments(reverse);
            List<FlowSegment> flowSegments = union(forwardSegments, reverseSegments);

            lockSwitches(flowSegments);

            flowResourcesManager.registerUsedByFlow(flowPair);

            //TODO(siakovenko): flow needs to be validated (existence of switches, same end-points, etc.)
            forward.setSrcSwitch(switchRepository.reload(forward.getSrcSwitch()));
            forward.setDestSwitch(switchRepository.reload(forward.getDestSwitch()));
            reverse.setSrcSwitch(switchRepository.reload(reverse.getSrcSwitch()));
            reverse.setDestSwitch(switchRepository.reload(reverse.getDestSwitch()));

            flowPair.setTimeCreate(Instant.now());

            flowRepository.createOrUpdate(flowPair);
            createFlowSegments(flowSegments);

            return new FlowPairWithSegments(flowPair, forwardSegments, reverseSegments);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(result);
    }

    /**
     * Deletes a flow and its segments from DB. Deallocates a path and resources.
     * Invokes flow rules deletion via the command sender.
     *
     * @param flowId the flow to be deleted.
     * @param sender the command sender for flow rules deletion.
     * @return the deleted flow.
     */
    public FlowPair deleteFlow(String flowId, FlowCommandSender sender) throws FlowNotFoundException {
        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            Optional<FlowPair> foundFlowPair = getFlowPair(flowId);
            if (!foundFlowPair.isPresent()) {
                return Optional.<FlowPairWithSegments>empty();
            }

            FlowPair flowPair = foundFlowPair.get();

            List<FlowSegment> forwardSegments = getFlowSegments(flowPair.getForward());
            List<FlowSegment> reverseSegments = getFlowSegments(flowPair.getReverse());
            List<FlowSegment> flowSegments = union(forwardSegments, reverseSegments);

            lockSwitches(flowSegments);

            log.info("Deleting the flow: {}", flowPair);

            flowRepository.delete(flowPair);
            deleteFlowSegments(flowSegments);

            flowResourcesManager.deallocateFlow(flowPair);

            return Optional.of(new FlowPairWithSegments(flowPair, forwardSegments, reverseSegments));
        }).orElseThrow(() -> new FlowNotFoundException(flowId));

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendRemoveRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Replaces a flow with the new one. Allocates a path and resources.
     * Stores the flow entities into DB, and invokes flow rules installation and deletion via the command sender.
     * <p/>
     * The updated flow has IN_PROGRESS status.
     *
     * @param updatingFlow  the flow to be updated.
     * @param diverseFlowId the flow id to build diverse group.
     * @param sender        the command sender for flow rules installation and deletion.
     * @return the updated flow with the path and resources set.
     */
    public FlowPair updateFlow(Flow updatingFlow, String diverseFlowId, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException, FlowValidationException,
            SwitchValidationException {
        flowValidator.validate(updatingFlow);

        updatingFlow.setStatus(FlowStatus.IN_PROGRESS);

        UpdatedFlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            FlowPair currentFlow = getFlowPair(updatingFlow.getFlowId())
                    .orElseThrow(() -> new FlowNotFoundException(updatingFlow.getFlowId()));

            if (diverseFlowId == null) {
                updatingFlow.setGroupId(null);
            } else {
                checkDiverseFlow(updatingFlow, diverseFlowId);
                updatingFlow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
            }

            PathComputer pathComputer = pathComputerFactory.getPathComputer();
            PathPair pathPair = pathComputer.getPath(updatingFlow, true);

            List<FlowSegment> forwardSegments = getFlowSegments(currentFlow.getForward());
            List<FlowSegment> reverseSegments = getFlowSegments(currentFlow.getReverse());
            List<FlowSegment> flowSegments = union(forwardSegments, reverseSegments);

            log.info("Updating the flow with {} and path: {}", updatingFlow, pathPair);

            FlowPair newFlowWithResources = flowResourcesManager.allocateFlow(buildFlowPair(updatingFlow, pathPair));
            newFlowWithResources.setTimeCreate(currentFlow.getForward().getTimeCreate());

            List<FlowSegment> newForwardSegments = buildFlowSegments(newFlowWithResources.getForward());
            List<FlowSegment> newReverseSegments = buildFlowSegments(newFlowWithResources.getReverse());
            List<FlowSegment> newFlowSegments = union(newForwardSegments, newReverseSegments);

            lockSwitches(union(flowSegments, newFlowSegments));

            flowRepository.delete(currentFlow);
            deleteFlowSegments(flowSegments);

            flowRepository.createOrUpdate(newFlowWithResources);
            createFlowSegments(newFlowSegments);

            flowResourcesManager.deallocateFlow(currentFlow);

            return UpdatedFlowPairWithSegments.builder()
                    .oldFlowPair(currentFlow).oldForwardSegments(forwardSegments).oldReverseSegments(reverseSegments)
                    .flowPair(newFlowWithResources).forwardSegments(newForwardSegments)
                    .reverseSegments(newReverseSegments).build();
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendUpdateRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Reroutes a flow via a new path. Deallocates old and allocates new path and resources.
     * Stores the flow entities into DB, and invokes flow rules installation and deletion via the command sender.
     * <p/>
     * The rerouted flow has IN_PROGRESS status.
     *
     * @param flowId         the flow to be rerouted.
     * @param forceToReroute if true the flow will be recreated even there's no better path found.
     * @param sender         the command sender for flow rules installation and deletion.
     */
    public ReroutedFlow rerouteFlow(String flowId, boolean forceToReroute, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException {
        FlowPair currentFlow = getFlowPair(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        log.warn("Origin flow {} path: {}", flowId, currentFlow.getForward().getFlowPath());

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(currentFlow.getForward(), true);

        log.warn("Potential New Path for flow {} with LEFT path: {}, RIGHT path: {}",
                flowId, pathPair.getForward(), pathPair.getReverse());

        boolean isFoundNewPath = !pathPair.getForward().equals(currentFlow.getForward().getFlowPath())
                || !pathPair.getReverse().equals(currentFlow.getReverse().getFlowPath());
        //no need to emit changes if path wasn't changed and flow is active.
        //force means to update flow even if path is not changed.
        if (!isFoundNewPath && currentFlow.isActive() && !forceToReroute) {
            log.warn("Reroute {} is unsuccessful: can't find new path.", flowId);

            return new ReroutedFlow(currentFlow, null);
        }

        UpdatedFlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            FlowPair newFlow = flowResourcesManager.allocateFlow(buildFlowPair(currentFlow.getForward(), pathPair));
            newFlow.setStatus(FlowStatus.IN_PROGRESS);
            newFlow.setTimeCreate(currentFlow.getForward().getTimeCreate());

            List<FlowSegment> forwardSegments = getFlowSegments(currentFlow.getForward());
            List<FlowSegment> reverseSegments = getFlowSegments(currentFlow.getReverse());
            List<FlowSegment> flowSegments = union(forwardSegments, reverseSegments);

            List<FlowSegment> newForwardSegments = buildFlowSegments(newFlow.getForward());
            List<FlowSegment> newReverseSegments = buildFlowSegments(newFlow.getReverse());
            List<FlowSegment> newFlowSegments = union(newForwardSegments, newReverseSegments);

            lockSwitches(union(flowSegments, newFlowSegments));

            // No need to re-read currentFlow as it's going to be removed.
            flowRepository.delete(currentFlow);
            deleteFlowSegments(flowSegments);

            flowRepository.createOrUpdate(newFlow);
            createFlowSegments(newFlowSegments);

            flowResourcesManager.deallocateFlow(currentFlow);

            return UpdatedFlowPairWithSegments.builder()
                    .oldFlowPair(currentFlow).oldForwardSegments(forwardSegments).oldReverseSegments(reverseSegments)
                    .flowPair(newFlow).forwardSegments(newForwardSegments)
                    .reverseSegments(newReverseSegments).build();
        });

        log.warn("Rerouted flow with new path: {}", result.getFlowPair());

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendUpdateRulesCommand(result);

        return new ReroutedFlow(currentFlow, result.getFlowPair());
    }

    private FlowPair buildFlowPair(Flow flow, PathPair pathPair) {
        Instant timestamp = Instant.now();

        Flow forward = flow.toBuilder()
                .srcSwitch(switchRepository.reload(flow.getSrcSwitch()))
                .destSwitch(switchRepository.reload(flow.getDestSwitch()))
                .timeModify(timestamp)
                .flowPath(pathPair.getForward())
                .build();
        Flow reverse = flow.toBuilder()
                .timeModify(timestamp)
                .srcSwitch(forward.getDestSwitch())
                .srcPort(flow.getDestPort())
                .srcVlan(flow.getDestVlan())
                .destSwitch(forward.getSrcSwitch())
                .destPort(flow.getSrcPort())
                .destVlan(flow.getSrcVlan())
                .flowPath(pathPair.getReverse())
                .build();
        return FlowPair.builder().forward(forward).reverse(reverse).build();
    }

    private List<FlowSegment> buildFlowSegments(Flow flow) {
        List<FlowSegment> segments = new ArrayList<>();

        List<FlowPath.Node> nodes = flow.getFlowPath().getNodes();
        for (int i = 0; i < nodes.size(); i += 2) {
            FlowPath.Node src = nodes.get(i);
            FlowPath.Node dst = nodes.get(i + 1);

            FlowSegment segment = FlowSegment.builder()
                    .flowId(flow.getFlowId())
                    .srcSwitch(switchRepository.reload(Switch.builder().switchId(src.getSwitchId()).build()))
                    .srcPort(src.getPortNo())
                    .seqId(src.getSeqId())
                    .latency(src.getSegmentLatency())
                    .destSwitch(switchRepository.reload(Switch.builder().switchId(dst.getSwitchId()).build()))
                    .destPort(dst.getPortNo())
                    .bandwidth(flow.getBandwidth())
                    .cookie(flow.getCookie())
                    .ignoreBandwidth(flow.isIgnoreBandwidth())
                    .build();

            segments.add(segment);
        }

        return segments;
    }

    private void createFlowSegments(List<FlowSegment> flowSegments) {
        flowSegments.forEach(flowSegment -> {
            log.debug("Creating the flow segment: {}", flowSegment);

            flowSegmentRepository.createOrUpdate(flowSegment);

            updateIslAvailableBandwidth(flowSegment.getSrcSwitch().getSwitchId(), flowSegment.getSrcPort(),
                    flowSegment.getDestSwitch().getSwitchId(), flowSegment.getDestPort());
        });
    }

    private void updateIslAvailableBandwidth(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        long usedBandwidth = flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                srcSwitchId, srcPort, dstSwitchId, dstPort);

        islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                .ifPresent(isl -> {
                    isl.setAvailableBandwidth(isl.getMaxBandwidth() - usedBandwidth);

                    islRepository.createOrUpdate(isl);
                });
    }

    private List<FlowSegment> getFlowSegments(Flow flow) {
        return Lists.newArrayList(flowSegmentRepository.findByFlowIdAndCookie(flow.getFlowId(), flow.getCookie()));
    }

    private void deleteFlowSegments(List<FlowSegment> flowSegments) {
        flowSegments.forEach(segment -> {
            log.debug("Deleting the flow segment: {}", segment);

            flowSegmentRepository.delete(segment);

            updateIslAvailableBandwidth(segment.getSrcSwitch().getSwitchId(), segment.getSrcPort(),
                    segment.getDestSwitch().getSwitchId(), segment.getDestPort());
        });
    }

    private void lockSwitches(List<FlowSegment> flowSegments) {
        Set<Switch> switches = new HashSet<>();
        flowSegments.forEach(flowSegment -> switches.add(flowSegment.getSrcSwitch()));
        switchRepository.lockSwitches(switches.toArray(new Switch[0]));
    }

    private String getOrCreateFlowGroupId(String flowId) throws FlowNotFoundException {
        log.info("Getting flow group for flow with id ", flowId);
        return flowRepository.getOrCreateFlowGroupId(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    private void checkDiverseFlow(Flow targetFlow, String flowId) throws FlowNotFoundException,
            FlowValidationException {
        if (targetFlow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't add one-switch flow into diverse group",
                    ErrorType.NOT_IMPLEMENTED);
        }

        FlowPair diverseFlow = flowRepository.findFlowPairById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlow.getForward().isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't create diverse group with one-switch flow",
                    ErrorType.NOT_IMPLEMENTED);
        }
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReroutedFlow {
        FlowPair oldFlow;
        FlowPair newFlow;
    }
}

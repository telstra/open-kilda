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
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ResourceAllocationAction
        extends NbTrackableWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event,
        FlowMirrorPointCreateContext> {

    private final int pathAllocationRetriesLimit; // Will be used in long mirrors
    private final int pathAllocationRetryDelay; // Will be used in long mirrors
    private final int resourceAllocationRetriesLimit; // Will be used in long mirrors
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final FlowMirrorRepository flowMirrorRepository;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final FlowResourcesManager resourcesManager;
    private final PathComputer pathComputer; // Will be used in long mirrors


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
        this.kildaConfigurationRepository
                = persistenceManager.getRepositoryFactory().createKildaConfigurationRepository();
        this.resourcesManager = resourcesManager;
        this.pathComputer = pathComputer;
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

        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(mirrorPoint.getFlowId());
            FlowPath flowPath = getFlowPath(mirrorPoint, flow);
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

            Switch sinkSwitch = getSwitch(mirrorPoint.getSinkEndpoint().getSwitchId());
            stateMachine.setUnmaskedCookie(resourcesManager.allocateCookie(flow.getFlowId()));

            FlowSegmentCookie cookie = FlowSegmentCookie.builder()
                    .flowEffectiveId(stateMachine.getUnmaskedCookie()).mirror(true).build();

            FlowMirror flowMirror = createFlowMirror(stateMachine, mirrorPoint, flowMirrorPoints, sinkSwitch);
            FlowMirrorPath forwardPath = createFlowMirrorPath(stateMachine, flow, flowMirrorPoints, sinkSwitch, cookie);
            stateMachine.setForwardMirrorPathId(forwardPath.getMirrorPathId());

            flowMirror.setForwardPath(forwardPath);
            flowMirrorPoints.addFlowMirrors(flowMirror);

            //TODO: add path allocation in case when src switch is not equal to dst switch
        });

        stateMachine.saveActionToHistory("New mirror path was created",
                format("The flow mirror path %s was created (with allocated resources)",
                        stateMachine.getFlowMirrorId()));
    }

    private FlowMirrorPath createFlowMirrorPath(
            FlowMirrorPointCreateFsm stateMachine, Flow flow, FlowMirrorPoints flowMirrorPoints,
            Switch sinkSwitch, FlowSegmentCookie cookie) {
        SwitchProperties switchProperties = getSwitchProperties(sinkSwitch.getSwitchId());
        boolean dstWithMultiTable = switchProperties != null
                ? switchProperties.isMultiTable() : kildaConfigurationRepository.getOrDefault().getUseMultiTable();

        PathId forwardPathId = resourcesManager.generateMirrorPathId(
                flow.getFlowId(), stateMachine.getFlowMirrorId());
        FlowMirrorPath flowMirrorPath = FlowMirrorPath.builder()
                .mirrorPathId(forwardPathId)
                .mirrorSwitch(flowMirrorPoints.getMirrorSwitch())
                .egressSwitch(sinkSwitch)
                .cookie(cookie)
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .status(FlowPathStatus.IN_PROGRESS)
                .egressWithMultiTable(dstWithMultiTable)
                .dummy(false)
                .build();

        flowMirrorPathRepository.add(flowMirrorPath);
        return flowMirrorPath;
    }

    private FlowMirror createFlowMirror(
            FlowMirrorPointCreateFsm stateMachine, RequestedFlowMirrorPoint mirrorPoint,
            FlowMirrorPoints flowMirrorPoints, Switch sinkSwitch) {
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

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow mirror point";
    }
}

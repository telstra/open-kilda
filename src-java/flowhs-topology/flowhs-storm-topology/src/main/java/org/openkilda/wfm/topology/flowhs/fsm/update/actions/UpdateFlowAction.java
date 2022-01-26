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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.EndpointUpdate;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.FlowLoopOperation;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.shade.com.google.common.base.Objects;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public class UpdateFlowAction extends
        NbTrackableWithHistorySupportAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final SwitchRepository switchRepository;
    private final YFlowRepository yFlowRepository;

    public UpdateFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowUpdateContext context,
                                                    FlowUpdateFsm stateMachine) {
        RequestedFlow targetFlow = stateMachine.getTargetFlow();
        String flowId = targetFlow.getFlowId();

        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(flowId);

            log.debug("Updating the flow {} with properties: {}", flowId, targetFlow);

            RequestedFlow originalFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(flow);
            stateMachine.setOldTargetPathComputationStrategy(flow.getTargetPathComputationStrategy());
            stateMachine.setOriginalFlow(originalFlow);

            stateMachine.setOriginalDiverseFlowGroup(flow.getDiverseGroupId());
            stateMachine.setOriginalAffinityFlowGroup(flow.getAffinityGroupId());

            // Complete target flow in FSM with values from original flow
            stateMachine.setTargetFlow(updateFlow(flow, targetFlow));

            EndpointUpdate endpointUpdate = updateEndpointRulesOnly(originalFlow, targetFlow,
                    stateMachine.getOriginalDiverseFlowGroup(), flow.getDiverseGroupId(),
                    stateMachine.getOriginalAffinityFlowGroup(), flow.getAffinityGroupId());
            stateMachine.setEndpointUpdate(endpointUpdate);

            if (endpointUpdate.isPartialUpdate()) {
                FlowLoopOperation flowLoopOperation = detectFlowLoopOperation(originalFlow, targetFlow);
                stateMachine.setFlowLoopOperation(flowLoopOperation);

                stateMachine.setNewPrimaryForwardPath(flow.getForwardPathId());
                stateMachine.setNewPrimaryReversePath(flow.getReversePathId());
                stateMachine.setNewProtectedForwardPath(flow.getProtectedForwardPathId());
                stateMachine.setNewProtectedReversePath(flow.getProtectedReversePathId());
                FlowDumpData dumpData = HistoryMapper.INSTANCE.map(flow, flow.getForwardPath(), flow.getReversePath(),
                        DumpType.STATE_AFTER);
                stateMachine.saveActionWithDumpToHistory("New endpoints were stored for flow",
                        format("The flow endpoints were updated for: %s / %s",
                                flow.getSrcSwitch(), flow.getDestSwitch()),
                        dumpData);
            }
        });

        stateMachine.saveActionToHistory("The flow properties were updated");

        if (stateMachine.getEndpointUpdate().isPartialUpdate()) {
            stateMachine.saveActionToHistory("Skip paths and resources allocation");
            stateMachine.fire(Event.UPDATE_ENDPOINT_RULES_ONLY);
        }

        return Optional.empty();
    }

    private RequestedFlow updateFlow(Flow flow, RequestedFlow targetFlow) {
        if (targetFlow.getDiverseFlowId() != null) {
            if (targetFlow.getDiverseFlowId().isEmpty()) {
                flow.setDiverseGroupId(null);
            } else {
                flow.setDiverseGroupId(getOrCreateDiverseFlowGroupId(targetFlow.getDiverseFlowId()));
            }
        } else if (targetFlow.isAllocateProtectedPath()) {
            if (flow.getDiverseGroupId() == null) {
                flow.setDiverseGroupId(getOrCreateDiverseFlowGroupId(flow.getFlowId()));
            }
        }

        if (targetFlow.getAffinityFlowId() != null) {
            if (targetFlow.getAffinityFlowId().isEmpty()) {
                flow.setAffinityGroupId(null);
            } else {
                flow.setAffinityGroupId(getOrCreateAffinityFlowGroupId(targetFlow.getAffinityFlowId()));
            }
        }

        Switch srcSwitch = switchRepository.findById(targetFlow.getSrcSwitch())
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Switch %s not found", targetFlow.getSrcSwitch())));
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(targetFlow.getSrcPort());
        flow.setSrcVlan(targetFlow.getSrcVlan());
        flow.setSrcInnerVlan(targetFlow.getSrcInnerVlan());
        DetectConnectedDevices.DetectConnectedDevicesBuilder detectConnectedDevices
                = flow.getDetectConnectedDevices().toBuilder();
        detectConnectedDevices.srcLldp(targetFlow.getDetectConnectedDevices().isSrcLldp());
        detectConnectedDevices.srcArp(targetFlow.getDetectConnectedDevices().isSrcArp());
        Switch destSwitch = switchRepository.findById(targetFlow.getDestSwitch())
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Switch %s not found", targetFlow.getDestSwitch())));
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(targetFlow.getDestPort());
        flow.setDestVlan(targetFlow.getDestVlan());
        flow.setDestInnerVlan(targetFlow.getDestInnerVlan());
        detectConnectedDevices.dstLldp(targetFlow.getDetectConnectedDevices().isDstLldp());
        detectConnectedDevices.dstArp(targetFlow.getDetectConnectedDevices().isDstArp());
        flow.setDetectConnectedDevices(detectConnectedDevices.build());

        if (targetFlow.getPriority() != null) {
            flow.setPriority(targetFlow.getPriority());
        }
        flow.setPinned(targetFlow.isPinned());
        flow.setAllocateProtectedPath(targetFlow.isAllocateProtectedPath());
        if (targetFlow.getDescription() != null) {
            flow.setDescription(targetFlow.getDescription());
        }
        flow.setBandwidth(targetFlow.getBandwidth());
        flow.setIgnoreBandwidth(targetFlow.isIgnoreBandwidth());
        flow.setStrictBandwidth(targetFlow.isStrictBandwidth());
        if (targetFlow.getMaxLatency() != null) {
            flow.setMaxLatency(targetFlow.getMaxLatency());
        }
        if (targetFlow.getMaxLatencyTier2() != null) {
            flow.setMaxLatencyTier2(targetFlow.getMaxLatencyTier2());
        }
        flow.setPeriodicPings(targetFlow.isPeriodicPings());
        if (targetFlow.getFlowEncapsulationType() != null) {
            flow.setEncapsulationType(targetFlow.getFlowEncapsulationType());
        } else {
            targetFlow.setFlowEncapsulationType(flow.getEncapsulationType());
        }
        if (targetFlow.getPathComputationStrategy() != null) {
            flow.setPathComputationStrategy(targetFlow.getPathComputationStrategy());
            flow.setTargetPathComputationStrategy(null);
        } else {
            if (flow.getTargetPathComputationStrategy() != null) {
                targetFlow.setPathComputationStrategy(flow.getTargetPathComputationStrategy());
                flow.setPathComputationStrategy(flow.getTargetPathComputationStrategy());
                flow.setTargetPathComputationStrategy(null);
            } else {
                targetFlow.setPathComputationStrategy(flow.getPathComputationStrategy());
            }
        }
        flow.setLoopSwitchId(targetFlow.getLoopSwitchId());
        return targetFlow;
    }

    private String getOrCreateDiverseFlowGroupId(String diverseFlowId) throws FlowProcessingException {
        log.debug("Getting flow diverse group for flow with id {}", diverseFlowId);
        String flowId = yFlowRepository.findById(diverseFlowId).map(Stream::of).orElseGet(Stream::empty)
                .map(YFlow::getSubFlows)
                .flatMap(Collection::stream)
                .map(YSubFlow::getFlow)
                .filter(flow -> flow.getFlowId().equals(flow.getAffinityGroupId()))
                .map(Flow::getFlowId)
                .findFirst()
                .orElse(diverseFlowId);
        return flowRepository.getOrCreateDiverseFlowGroupId(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    private String getOrCreateAffinityFlowGroupId(String flowId) throws FlowProcessingException {
        log.debug("Getting flow affinity group for flow with id {}", flowId);
        return flowRepository.getOrCreateAffinityFlowGroupId(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }

    private FlowUpdateFsm.EndpointUpdate updateEndpointRulesOnly(RequestedFlow originalFlow, RequestedFlow targetFlow,
                                                                 String originalDiverseGroupId,
                                                                 String targetDiverseGroupId,
                                                                 String originalAffinityGroupId,
                                                                 String targetAffinityGroupId) {
        boolean updateEndpointOnly = originalFlow.getSrcSwitch().equals(targetFlow.getSrcSwitch());
        updateEndpointOnly &= originalFlow.getDestSwitch().equals(targetFlow.getDestSwitch());

        updateEndpointOnly &= originalFlow.isAllocateProtectedPath() == targetFlow.isAllocateProtectedPath();
        updateEndpointOnly &= originalFlow.getBandwidth() == targetFlow.getBandwidth();
        updateEndpointOnly &= originalFlow.isIgnoreBandwidth() == targetFlow.isIgnoreBandwidth();
        updateEndpointOnly &= originalFlow.isStrictBandwidth() == targetFlow.isStrictBandwidth();

        updateEndpointOnly &= Objects.equal(originalFlow.getMaxLatency(), targetFlow.getMaxLatency());
        updateEndpointOnly &= Objects.equal(originalFlow.getFlowEncapsulationType(),
                targetFlow.getFlowEncapsulationType());
        updateEndpointOnly &= Objects.equal(originalFlow.getPathComputationStrategy(),
                targetFlow.getPathComputationStrategy());

        updateEndpointOnly &= Objects.equal(originalDiverseGroupId, targetDiverseGroupId);
        updateEndpointOnly &= Objects.equal(originalAffinityGroupId, targetAffinityGroupId);

        // TODO(tdurakov): check connected devices as well
        boolean srcEndpointChanged = originalFlow.getSrcPort() != targetFlow.getSrcPort();
        srcEndpointChanged |= originalFlow.getSrcVlan() != targetFlow.getSrcVlan();
        srcEndpointChanged |= originalFlow.getSrcInnerVlan() != targetFlow.getSrcInnerVlan();

        // TODO(tdurakov): check connected devices as well
        boolean dstEndpointChanged = originalFlow.getDestPort() != targetFlow.getDestPort();
        dstEndpointChanged |= originalFlow.getDestVlan() != targetFlow.getDestVlan();
        dstEndpointChanged |= originalFlow.getDestInnerVlan() != targetFlow.getDestInnerVlan();

        if (originalFlow.getLoopSwitchId() != targetFlow.getLoopSwitchId()) {
            srcEndpointChanged |= originalFlow.getSrcSwitch().equals(originalFlow.getLoopSwitchId());
            srcEndpointChanged |= originalFlow.getSrcSwitch().equals(targetFlow.getLoopSwitchId());
            dstEndpointChanged |= originalFlow.getDestSwitch().equals(originalFlow.getLoopSwitchId());
            dstEndpointChanged |= originalFlow.getDestSwitch().equals(targetFlow.getLoopSwitchId());
        }

        if (updateEndpointOnly) {
            if (srcEndpointChanged && dstEndpointChanged) {
                return EndpointUpdate.BOTH;
            } else if (srcEndpointChanged) {
                return EndpointUpdate.SOURCE;
            } else if (dstEndpointChanged) {
                return EndpointUpdate.DESTINATION;
            } else {
                return EndpointUpdate.NONE;
            }
        }
        return EndpointUpdate.NONE;
    }

    private FlowLoopOperation detectFlowLoopOperation(RequestedFlow originalFlow, RequestedFlow targetFlow) {
        if (originalFlow.getLoopSwitchId() == null && targetFlow.getLoopSwitchId() == null) {
            return FlowLoopOperation.NONE;
        }
        if (targetFlow.getLoopSwitchId() == null) {
            return FlowLoopOperation.DELETE;
        } else {
            return FlowLoopOperation.CREATE;
        }
    }

    protected String getGenericErrorMessage() {
        return "Could not update flow";
    }
}

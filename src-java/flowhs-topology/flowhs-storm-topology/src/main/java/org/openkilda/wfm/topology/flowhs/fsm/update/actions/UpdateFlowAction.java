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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.neo4j.driver.v1.exceptions.ClientException;

import java.util.Optional;

@Slf4j
public class UpdateFlowAction extends NbTrackableAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final int transactionRetriesLimit;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;

    public UpdateFlowAction(PersistenceManager persistenceManager, int transactionRetriesLimit) {
        super(persistenceManager);
        this.transactionRetriesLimit = transactionRetriesLimit;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        islRepository = repositoryFactory.createIslRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowUpdateContext context,
                                                    FlowUpdateFsm stateMachine) {
        RequestedFlow targetFlow = stateMachine.getTargetFlow();
        String flowId = targetFlow.getFlowId();

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(RecoverablePersistenceException.class)
                .retryOn(ClientException.class)
                .withMaxRetries(transactionRetriesLimit);

        persistenceManager.getTransactionManager().doInTransaction(retryPolicy, () -> {
            Flow flow = getFlow(flowId);

            log.debug("Updating the flow {} with properties: {}", flowId, targetFlow);

            // Complete target flow in FSM with values from original flow
            stateMachine.setTargetFlow(updateFlow(flow, targetFlow, stateMachine));
            flowRepository.createOrUpdate(flow);
        });

        stateMachine.saveActionToHistory("The flow properties were updated");

        return Optional.empty();
    }

    private RequestedFlow updateFlow(Flow flow, RequestedFlow targetFlow, FlowUpdateFsm stateMachine) {
        RequestedFlow originalFlow = RequestedFlowMapper.INSTANCE.toRequestedFlow(flow);
        stateMachine.setOriginalFlow(originalFlow);

        stateMachine.setOriginalFlowGroup(flow.getGroupId());
        if (targetFlow.getDiverseFlowId() != null) {
            flow.setGroupId(getOrCreateFlowGroupId(targetFlow.getDiverseFlowId()));
        } else if (targetFlow.isAllocateProtectedPath()) {
            if (flow.getGroupId() == null) {
                flow.setGroupId(getOrCreateFlowGroupId(flow.getFlowId()));
            }
        } else {
            flow.setGroupId(null);
        }

        Switch srcSwitch = switchRepository.findById(targetFlow.getSrcSwitch())
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Switch %s not found", targetFlow.getSrcSwitch())));
        flow.setSrcSwitch(srcSwitch);
        flow.setSrcPort(targetFlow.getSrcPort());
        flow.setSrcVlan(targetFlow.getSrcVlan());
        flow.getDetectConnectedDevices().setSrcLldp(targetFlow.getDetectConnectedDevices().isSrcLldp());
        flow.getDetectConnectedDevices().setSrcArp(targetFlow.getDetectConnectedDevices().isSrcArp());
        Switch destSwitch = switchRepository.findById(targetFlow.getDestSwitch())
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Switch %s not found", targetFlow.getDestSwitch())));
        flow.setDestSwitch(destSwitch);
        flow.setDestPort(targetFlow.getDestPort());
        flow.setDestVlan(targetFlow.getDestVlan());
        flow.getDetectConnectedDevices().setDstLldp(targetFlow.getDetectConnectedDevices().isDstLldp());
        flow.getDetectConnectedDevices().setDstArp(targetFlow.getDetectConnectedDevices().isDstArp());

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
        if (targetFlow.getMaxLatency() != null) {
            flow.setMaxLatency(targetFlow.getMaxLatency());
        }
        flow.setPeriodicPings(targetFlow.isPeriodicPings());
        if (targetFlow.getFlowEncapsulationType() != null) {
            flow.setEncapsulationType(targetFlow.getFlowEncapsulationType());
        } else {
            targetFlow.setFlowEncapsulationType(flow.getEncapsulationType());
        }
        if (targetFlow.getPathComputationStrategy() != null) {
            flow.setPathComputationStrategy(targetFlow.getPathComputationStrategy());
        } else {
            targetFlow.setPathComputationStrategy(flow.getPathComputationStrategy());
        }
        return targetFlow;
    }

    private String getOrCreateFlowGroupId(String flowId) throws FlowProcessingException {
        log.debug("Getting flow group for flow with id {}", flowId);
        return flowRepository.getOrCreateFlowGroupId(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow %s not found", flowId)));
    }


    @Override
    protected String getGenericErrorMessage() {
        return "Could not update flow";
    }
}

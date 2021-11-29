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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.flow.RemoveFlowCommand;
import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowPathDtoBuilder;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class NotifyFlowMonitorAction<T extends FlowProcessingFsm<T, S, E, C, ?>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {

    private FlowGenericCarrier carrier;

    public NotifyFlowMonitorAction(PersistenceManager persistenceManager, FlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        carrier.sendNotifyFlowMonitor(getFlowInfo(stateMachine.getFlowId()));
    }

    private CommandData getFlowInfo(String flowId) {
        Optional<Flow> flow = flowRepository.findById(flowId);
        if (!flow.isPresent() || flow.get().isOneSwitchFlow()) {
            return new RemoveFlowCommand(flowId);
        }

        FlowPathDto flowPathDto = toFlowPathDtoBuilder(flow.get()).build();

        return new UpdateFlowCommand(flowId, flowPathDto, flow.get().getMaxLatency(), flow.get().getMaxLatencyTier2());
    }

    private FlowPathDtoBuilder toFlowPathDtoBuilder(Flow flow) {
        return FlowPathDto.builder()
                .id(flow.getFlowId())
                .forwardPath(buildPathFromFlow(flow, flow.getForwardPath()))
                .reversePath(buildPathFromFlow(flow, flow.getReversePath()));
    }

    private List<PathNodePayload> buildPathFromFlow(Flow flow, FlowPath flowPath) {
        return flowPath != null ? FlowPathMapper.INSTANCE.mapToPathNodes(flow, flowPath) : Collections.emptyList();
    }
}

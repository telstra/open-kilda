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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;

@Slf4j
public class CompleteFlowCreateAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final FlowRepository flowRepository;

    public CompleteFlowCreateAction(PersistenceManager persistenceManager) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        String flowId = stateMachine.getFlow().getFlowId();
        flowRepository.findById(flowId).ifPresent(
                flow -> {
                    FlowPath newForward = flow.getForwardPath();
                    newForward.setStatus(FlowPathStatus.ACTIVE);
                    FlowPath newReverse = flow.getReversePath();
                    newReverse.setStatus(FlowPathStatus.ACTIVE);

                    flow.setStatus(FlowStatus.UP);

                    flowRepository.createOrUpdate(flow);
                });
        log.info("Flow {} successfully created", stateMachine.getFlow().getFlowId());
        saveHistory(stateMachine);
    }


    private void saveHistory(FlowCreateFsm stateMachine) {
        Flow flow = stateMachine.getFlow();

        FlowDumpData flowDumpData = HistoryMapper.INSTANCE.map(flow);
        flowDumpData.setDumpType(DumpType.STATE_AFTER);
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowDumpData(flowDumpData)
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Created successfully")
                        .time(Instant.now())
                        .description(format("Flow %s has been created successfully",
                                stateMachine.getFlow().getFlowId()))
                        .flowId(flow.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }

}

/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class RevertYFlowStatusAction extends
        YFlowProcessingWithHistorySupportAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
    public RevertYFlowStatusAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowPathSwapContext context,
                           YFlowPathSwapFsm stateMachine) {
        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            Optional<YFlow> foundYFlow = yFlowRepository.findById(stateMachine.getYFlowId());
            return foundYFlow.map(yFlow -> {
                yFlow.setStatus(stateMachine.getOriginalYFlowStatus());
                return yFlow.getStatus();
            }).orElse(null);
        });

        stateMachine.saveActionToHistory(format("The y-flow was reverted. The status %s", flowStatus));
    }
}

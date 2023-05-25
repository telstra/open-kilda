/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.model.HaFlow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class RevertPathsSwapAction extends
        FlowProcessingWithHistorySupportAction<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {
    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, HaFlowPathSwapContext context,
                           HaFlowPathSwapFsm stateMachine) {
        Pair<PathId, PathId> primaryPathIds = transactionManager.doInTransaction(() -> {
            String haFlowId = stateMachine.getHaFlowId();
            HaFlow haFlow = getHaFlow(haFlowId);

            log.debug("Reverting primary and protected paths swap for HA-flow {}", haFlowId);
            haFlow.swapPathIds();
            return Pair.of(haFlow.getForwardPathId(), haFlow.getReversePathId());
        });

        stateMachine.saveActionToHistory("HA-flow was reverted to old paths",
                format("The flow %s was updated with paths %s / %s",
                        stateMachine.getHaFlowId(), primaryPathIds.getLeft(), primaryPathIds.getRight()));
    }
}

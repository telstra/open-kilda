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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CompleteFlowPathRemovalAction extends
        BaseFlowPathRemovalAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {

    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        // Iterate to remove each path in a dedicated transaction.
        flow.getPathIds().forEach(pathId -> {
            Optional<FlowPath> deletedPath = flowPathRepository.remove(pathId);
            deletedPath.ifPresent(path -> {
                updateIslsForFlowPath(path);
                saveRemovalActionWithDumpToHistory(stateMachine, flow, new FlowPathPair(path, path));
            });
        });
    }
}

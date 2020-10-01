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

import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SkipPathsAndResourcesDeallocationAction
        extends HistoryRecordingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {

    private FlowPathRepository flowPathRepository;

    public SkipPathsAndResourcesDeallocationAction(PersistenceManager persistenceManager) {
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    public void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        if (stateMachine.getEndpointUpdate().isPartialUpdate()) {
            FlowDumpData dumpData = HistoryMapper.INSTANCE.map(
                    RequestedFlowMapper.INSTANCE.toFlow(stateMachine.getOriginalFlow()),
                    getFlowPath(stateMachine.getNewPrimaryForwardPath()),
                    getFlowPath(stateMachine.getNewPrimaryReversePath()),
                    DumpType.STATE_BEFORE);
            stateMachine.saveActionWithDumpToHistory("New endpoints were stored for flow",
                    format("The flow endpoints were updated for: %s / %s",
                            stateMachine.getTargetFlow().getSrcSwitch(),
                            stateMachine.getTargetFlow().getDestSwitch()),
                    dumpData);
            stateMachine.fire(Event.UPDATE_ENDPOINT_RULES_ONLY);
        }
    }

    private FlowPath getFlowPath(PathId pathId) {
        return flowPathRepository.findById(pathId).orElse(null);
    }
}

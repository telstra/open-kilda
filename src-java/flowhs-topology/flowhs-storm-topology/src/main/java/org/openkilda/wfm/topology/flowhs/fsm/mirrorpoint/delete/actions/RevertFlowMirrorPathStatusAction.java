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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertFlowMirrorPathStatusAction
        extends FlowProcessingAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {

    private final FlowMirrorPathRepository flowMirrorPathRepository;

    public RevertFlowMirrorPathStatusAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                           FlowMirrorPointDeleteFsm stateMachine) {
        PathId mirrorPathId = stateMachine.getMirrorPathId();
        FlowPathStatus originalStatus = stateMachine.getOriginalFlowMirrorPathStatus();

        if (originalStatus != null) {
            log.debug("Reverting the flow mirror path status of {} to {}", mirrorPathId, originalStatus);

            flowMirrorPathRepository.updateStatus(mirrorPathId, FlowPathStatus.ACTIVE);

            stateMachine.saveActionToHistory(format("The flow mirror path status was reverted to %s", originalStatus));
        }
    }
}

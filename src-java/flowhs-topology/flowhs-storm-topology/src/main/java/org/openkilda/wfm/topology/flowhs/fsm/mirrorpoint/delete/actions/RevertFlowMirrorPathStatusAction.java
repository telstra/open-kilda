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
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertFlowMirrorPathStatusAction extends
        FlowProcessingWithHistorySupportAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {

    //TODO use it
    private final FlowMirrorRepository flowMirrorRepository;

    public RevertFlowMirrorPathStatusAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        flowMirrorRepository = persistenceManager.getRepositoryFactory().createFlowMirrorRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                           FlowMirrorPointDeleteFsm stateMachine) {
        String mirrorMirrorId = stateMachine.getFlowMirrorId();
        FlowPathStatus originalStatus = stateMachine.getOriginalFlowMirrorStatus();

        if (originalStatus != null) {
            log.debug("Reverting the flow mirror path status of {} to {}", mirrorMirrorId, originalStatus);

            flowMirrorRepository.updateStatus(mirrorMirrorId, FlowPathStatus.ACTIVE);

            stateMachine.saveActionToHistory(format("The flow mirror path status was reverted to %s", originalStatus));
        }
    }
}

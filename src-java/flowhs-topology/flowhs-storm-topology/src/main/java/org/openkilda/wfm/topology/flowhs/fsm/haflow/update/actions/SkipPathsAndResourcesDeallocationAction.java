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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SkipPathsAndResourcesDeallocationAction
        extends FlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public SkipPathsAndResourcesDeallocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    public void perform(State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        if (!stateMachine.getPartialUpdateEndpoints().isEmpty()) {

            HaFlow originalHaFlow = stateMachine.getOriginalHaFlow();
            // TODO save originalHaFlow to history https://github.com/telstra/open-kilda/issues/5169
            // example org.openkilda.wfm.topology.flowhs.fsm.update.actions.SkipPathsAndResourcesDeallocationAction
            stateMachine.fire(Event.UPDATE_ENDPOINT_RULES_ONLY);
        }
    }
}

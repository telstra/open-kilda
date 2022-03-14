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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CompleteYFlowSwappingWithErrorAction extends
        NbTrackableWithHistorySupportAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {

    public CompleteYFlowSwappingWithErrorAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowPathSwapContext context,
                                                    YFlowPathSwapFsm stateMachine) {
        if (stateMachine.getErrorReason() != null) {
            Message message = stateMachine.buildErrorMessage(ErrorType.INTERNAL_ERROR,
                    format("Could not swap paths for y-flow %s", stateMachine.getYFlowId()),
                    stateMachine.getErrorReason());
            return Optional.of(message);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap y-flow paths";
    }
}

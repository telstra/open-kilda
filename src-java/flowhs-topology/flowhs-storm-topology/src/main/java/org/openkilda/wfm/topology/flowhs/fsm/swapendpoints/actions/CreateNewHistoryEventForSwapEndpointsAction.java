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

package org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions;

import static java.lang.String.format;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsContext;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm;


public class CreateNewHistoryEventForSwapEndpointsAction extends FlowProcessingWithHistorySupportAction
        <FlowSwapEndpointsFsm, FlowSwapEndpointsFsm.State, FlowSwapEndpointsFsm.Event, FlowSwapEndpointsContext> {

    public CreateNewHistoryEventForSwapEndpointsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(FlowSwapEndpointsFsm.State from,
                           FlowSwapEndpointsFsm.State to,
                           FlowSwapEndpointsFsm.Event event,
                           FlowSwapEndpointsContext context,
                           FlowSwapEndpointsFsm stateMachine) {
        stateMachine.saveNewEventToHistory(stateMachine.getFirstFlowId(),
                format("Current flow and flow %s were validated successfully", stateMachine.getSecondFlowId()),
                FlowEventData.Event.SWAP_ENDPOINTS);
        stateMachine.saveNewEventToHistory(stateMachine.getSecondFlowId(),
                format("Current flow and flow %s were validated successfully", stateMachine.getFirstFlowId()),
                FlowEventData.Event.SWAP_ENDPOINTS);
    }
}

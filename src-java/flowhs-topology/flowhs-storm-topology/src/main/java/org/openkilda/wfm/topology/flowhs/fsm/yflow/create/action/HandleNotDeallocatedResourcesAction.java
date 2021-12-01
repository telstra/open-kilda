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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotDeallocatedResourcesAction extends
        HistoryRecordingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    protected void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlowResources newResources = stateMachine.getNewResources();
        if (newResources == null) {
            log.debug("No resources were allocated for y-flow {}", yFlowId);
            return;
        }

        EndpointResources sharedEndpointResources = newResources.getSharedEndpointResources();
        if (sharedEndpointResources != null) {
            stateMachine.saveErrorToHistory("Failed to deallocate resources",
                    format("Failed to deallocate resources %s of y-flow %s (shared endpoint): %s",
                            sharedEndpointResources, stateMachine.getYFlowId(), context.getError()));
        }

        EndpointResources mainResources = newResources.getMainPathYPointResources();
        if (mainResources != null) {
            stateMachine.saveErrorToHistory("Failed to deallocate resources",
                    format("Failed to deallocate resources %s of y-flow %s (main paths): %s",
                            mainResources, stateMachine.getYFlowId(), context.getError()));
        }

        EndpointResources protectedResources = newResources.getProtectedPathYPointResources();
        if (protectedResources != null) {
            stateMachine.saveErrorToHistory("Failed to deallocate resources",
                    format("Failed to deallocate resources %s of y-flow %s (protected paths): %s",
                            mainResources, stateMachine.getYFlowId(), context.getError()));
        }
    }
}

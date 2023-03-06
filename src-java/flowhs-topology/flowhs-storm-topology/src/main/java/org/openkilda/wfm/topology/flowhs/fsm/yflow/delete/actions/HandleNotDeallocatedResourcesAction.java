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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions;

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class HandleNotDeallocatedResourcesAction extends
        HistoryRecordingAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {
    @Override
    public void perform(State from, State to, Event event, YFlowDeleteContext context, YFlowDeleteFsm stateMachine) {
        Optional<YFlowResources> oldResources = Optional.ofNullable(stateMachine.getOldResources());
        Optional<EndpointResources> sharedEndpointResources =
                oldResources.map(YFlowResources::getSharedEndpointResources);
        if (sharedEndpointResources.isPresent()) {
            stateMachine.saveErrorToHistory("Failed to deallocate resources",
                    format("Failed to deallocate resources: %s", sharedEndpointResources.get()));
        }

        Optional<EndpointResources> mainResources = oldResources.map(YFlowResources::getMainPathYPointResources);
        if (mainResources.isPresent()) {
            stateMachine.saveErrorToHistory("Failed to deallocate resources",
                    format("Failed to deallocate resources: %s", mainResources.get()));
        }

        Optional<EndpointResources> protectedResources =
                oldResources.map(YFlowResources::getProtectedPathYPointResources);
        if (protectedResources.isPresent()) {
            stateMachine.saveErrorToHistory("Failed to deallocate resources",
                    format("Failed to deallocate resources: %s", protectedResources.get()));
        }
    }
}

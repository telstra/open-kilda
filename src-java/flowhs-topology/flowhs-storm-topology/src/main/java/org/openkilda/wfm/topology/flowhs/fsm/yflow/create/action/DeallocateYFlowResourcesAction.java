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

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.YPointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class DeallocateYFlowResourcesAction extends
        YFlowProcessingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final FlowResourcesManager resourcesManager;

    public DeallocateYFlowResourcesAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        Optional<YFlowResources> newResources = Optional.ofNullable(stateMachine.getNewResources());
        Optional<YPointResources> mainResources = newResources.map(YFlowResources::getMainPathResources);
        Optional<MeterId> mainMeterId = mainResources.map(YPointResources::getMeterId);
        if (mainMeterId.isPresent()) {
            MeterId meterId = mainMeterId.get();
            SwitchId yPoint = mainResources.get().getYPoint();
            resourcesManager.deallocateMeter(yPoint, meterId);
            stateMachine.saveActionToHistory("The meter was deallocated",
                    format("The meter %s / %s was deallocated", yPoint, meterId));
        } else {
            log.debug("No meter was allocated for y-flow {} (main paths)", yFlowId);
        }

        Optional<YPointResources> protectedResources = newResources.map(YFlowResources::getProtectedPathResources);
        Optional<MeterId> protectedMeterId = protectedResources.map(YPointResources::getMeterId);
        if (protectedMeterId.isPresent()) {
            MeterId meterId = protectedMeterId.get();
            SwitchId yPoint = protectedResources.get().getYPoint();
            resourcesManager.deallocateMeter(yPoint, meterId);
            stateMachine.saveActionToHistory("The meter was deallocated",
                    format("The meter %s / %s was deallocated", yPoint, meterId));
        } else {
            log.debug("No meter was allocated for y-flow {} (main paths)", yFlowId);
        }
    }
}

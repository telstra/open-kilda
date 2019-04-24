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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class ResourcesDeallocateAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private FlowResourcesManager resourcesManager;
    private FlowRepository flowRepository;
    private SwitchRepository switchRepository;
    private IslRepository islRepository;

    public ResourcesDeallocateAction(FlowResourcesConfig resourcesConfig, PersistenceManager persistenceManager) {
        this.resourcesManager = new FlowResourcesManager(persistenceManager, resourcesConfig);
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Flow flow = stateMachine.getFlow();
        resourcesManager.deallocatePathResources(flow.getForwardPathId(),
                flow.getForwardPath().getCookie().getUnmaskedValue(), flow.getEncapsulationType());
        resourcesManager.deallocatePathResources(flow.getReversePathId(),
                flow.getReversePath().getCookie().getUnmaskedValue(), flow.getEncapsulationType());
        log.debug("Flow resources have been deallocated for flow {}", flow.getFlowId());

        stateMachine.fire(Event.NEXT);
    }
}

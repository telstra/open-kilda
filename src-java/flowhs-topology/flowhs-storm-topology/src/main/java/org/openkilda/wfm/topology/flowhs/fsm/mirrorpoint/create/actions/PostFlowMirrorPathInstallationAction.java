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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostFlowMirrorPathInstallationAction
        extends FlowProcessingAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {

    private final FlowMirrorPathRepository flowMirrorPathRepository;

    public PostFlowMirrorPathInstallationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointCreateContext context,
                           FlowMirrorPointCreateFsm stateMachine) {
        PathId mirrorPath = stateMachine.getMirrorPathId();

        log.debug("Completing installation of the flow mirror path {}", mirrorPath);

        flowMirrorPathRepository.updateStatus(mirrorPath, FlowPathStatus.ACTIVE);

        stateMachine.saveActionToHistory("Flow mirror path was installed",
                format("The flow path %s was installed", mirrorPath));
    }
}

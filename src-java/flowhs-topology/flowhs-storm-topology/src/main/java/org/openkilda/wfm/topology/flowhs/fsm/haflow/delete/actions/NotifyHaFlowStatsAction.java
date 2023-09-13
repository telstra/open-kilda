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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.buildRemoveHaFlowPathInfo;

import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowGenericCarrier;

public class NotifyHaFlowStatsAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> {
    private final HaFlowGenericCarrier carrier;

    public NotifyHaFlowStatsAction(PersistenceManager persistenceManager, HaFlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowDeleteContext context, HaFlowDeleteFsm stateMachine) {
        haFlowRepository.findById(stateMachine.getHaFlowId())
                .ifPresent(haFlow -> haFlow.getPaths()
                        .forEach(haPath -> haPath.getSubPaths()
                                .forEach(subPath -> sendRemoveHaFlowPathInfo(haFlow, subPath))));
    }

    private void sendRemoveHaFlowPathInfo(HaFlow haFlow, FlowPath subPath) {
        carrier.sendNotifyFlowStats(buildRemoveHaFlowPathInfo(subPath, haFlow));
    }
}

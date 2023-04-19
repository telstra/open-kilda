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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

public class NotifyHaFlowMonitorAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E, C>
        extends FlowProcessingWithHistorySupportAction<T, S, E, C> {

    public NotifyHaFlowMonitorAction(PersistenceManager persistenceManager, FlowGenericCarrier carrier) {
        super(persistenceManager);
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        //TODO
    }
}

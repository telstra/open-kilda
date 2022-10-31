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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;
import org.openkilda.wfm.topology.flowhs.utils.YFlowRuleManagerAdapter;

public class EmitYRulesSyncRequestsAction
        extends YFlowProcessingAction<YFlowSyncFsm, State, Event, FlowSyncContext> {
    private final YFlowRuleManagerAdapter ruleManagerAdapter;

    public EmitYRulesSyncRequestsAction(
            PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);

        ruleManagerAdapter = new YFlowRuleManagerAdapter(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowSyncContext context, YFlowSyncFsm stateMachine) {
        YFlow yFlow = getYFlow(stateMachine.getYFlowId());
        CommandContext commandContext = stateMachine.getCommandContext();
        FlowSyncCarrier carrier = stateMachine.getCarrier();
        for (InstallSpeakerCommandsRequest origin : ruleManagerAdapter.buildInstallRequests(yFlow, commandContext)) {
            InstallSpeakerCommandsRequest request = origin.toBuilder().failIfExists(false).build();
            carrier.sendSpeakerRequest(request);
        }
    }
}

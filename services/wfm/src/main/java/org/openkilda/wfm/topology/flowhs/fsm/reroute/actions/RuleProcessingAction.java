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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;
import java.util.UUID;

@Slf4j
public abstract class RuleProcessingAction
        extends AnonymousAction<FlowRerouteFsm, FlowRerouteFsm.State, FlowRerouteFsm.Event, FlowRerouteContext> {

    @Override
    public final void execute(FlowRerouteFsm.State from, FlowRerouteFsm.State to, FlowRerouteFsm.Event event,
                              FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        try {
            perform(from, to, event, context, stateMachine);
        } catch (Exception e) {
            log.error("Flow processing failure", e);

            stateMachine.fireError();
        }
    }

    protected abstract void perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to, FlowRerouteFsm.Event event,
                                    FlowRerouteContext context, FlowRerouteFsm stateMachine);

    protected long getCookieForCommand(FlowRerouteFsm stateMachine, UUID commandId) {
        long cookie;
        if (stateMachine.getNonIngressCommands().containsKey(commandId)) {
            InstallTransitRule installRule = stateMachine.getNonIngressCommands().get(commandId);
            cookie = installRule.getCookie().getValue();
        } else if (stateMachine.getIngressCommands().containsKey(commandId)) {
            InstallIngressRule installRule = stateMachine.getIngressCommands().get(commandId);
            cookie = installRule.getCookie().getValue();
        } else if (stateMachine.getRemoveCommands().containsKey(commandId)) {
            RemoveRule removeRule = stateMachine.getRemoveCommands().get(commandId);
            cookie = removeRule.getCookie().getValue();
        } else {
            throw new IllegalStateException(format("Failed to find install/remove rule command with id %s", commandId));
        }
        return cookie;
    }

    protected void sendHistoryUpdate(FlowRerouteFsm stateMachine, String action, String description) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .description(description)
                        .time(Instant.now())
                        .flowId(stateMachine.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}

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

import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.TransitVlanCommandFactory;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class InstallNonIngressRulesAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private TransitVlanCommandFactory commandFactory;

    public InstallNonIngressRulesAction(PersistenceManager persistenceManager) {
        this.commandFactory =
                new TransitVlanCommandFactory(persistenceManager.getRepositoryFactory().createTransitVlanRepository());
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        List<InstallTransitRule> commands =
                commandFactory.createInstallNonIngressRules(stateMachine.getCommandContext(), stateMachine.getFlow());

        if (commands.isEmpty()) {
            log.debug("No need to install non ingress rules for one switch flow");
        } else {
            commands.forEach(command -> stateMachine.getCarrier().sendSpeakerRequest(command));

            stateMachine.setNonIngressCommands(commands.stream()
                    .collect(Collectors.toMap(InstallTransitRule::getCommandId, Function.identity())));
            Set<UUID> commandIds = commands.stream()
                    .map(FlowRequest::getCommandId)
                    .collect(Collectors.toSet());
            stateMachine.setPendingCommands(commandIds);
            log.debug("Commands for installing non ingress rules have been sent");
            saveHistory(stateMachine);
        }
    }

    private void saveHistory(FlowCreateFsm stateMachine) {
        Flow flow = stateMachine.getFlow();

        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Install non ingress commands have been sent.")
                        .time(Instant.now())
                        .flowId(flow.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}

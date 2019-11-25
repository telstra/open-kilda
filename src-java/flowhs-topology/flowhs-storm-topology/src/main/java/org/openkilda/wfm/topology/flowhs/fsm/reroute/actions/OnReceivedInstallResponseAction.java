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
import static org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.REROUTE_RETRY_LIMIT;

import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnReceivedInstallResponseAction
        extends org.openkilda.wfm.topology.flowhs.fsm.common.actions.OnReceivedInstallResponseAction
        <FlowRerouteFsm, FlowRerouteFsm.State, FlowRerouteFsm.Event, FlowRerouteContext> {
    private FlowRepository flowRepository;
    private FlowRerouteHubCarrier carrier;

    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    public OnReceivedInstallResponseAction(int speakerCommandRetriesLimit, FlowRerouteFsm.Event completeEvent,
                                           PersistenceManager persistenceManager, FlowRerouteHubCarrier carrier) {
        super(speakerCommandRetriesLimit, completeEvent);
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.carrier = carrier;
    }

    @Override
    protected void onCompleteWithFailedCommands(FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalStateException(format("Flow %s not found", flowId)));
        boolean isTerminatingSwitchFailed = stateMachine.getFailedCommands().values().stream()
                .anyMatch(errorResponse -> errorResponse.getSwitchId().equals(flow.getSrcSwitch().getSwitchId())
                        || errorResponse.getSwitchId().equals(flow.getDestSwitch().getSwitchId()));
        int rerouteCounter = stateMachine.getRerouteCounter();
        if (!isTerminatingSwitchFailed && rerouteCounter < REROUTE_RETRY_LIMIT) {
            rerouteCounter += 1;
            String newReason = format("%s: retry #%d", stateMachine.getRerouteReason(), rerouteCounter);
            FlowRerouteFact flowRerouteFact = new FlowRerouteFact(commandIdGenerator.generate().toString(),
                    stateMachine.getCommandContext().fork(format("retry #%d", rerouteCounter)),
                    stateMachine.getFlowId(), stateMachine.getAffectedIsls(), stateMachine.isForceReroute(),
                    stateMachine.isEffectivelyDown(), newReason, rerouteCounter);
            carrier.injectRetry(flowRerouteFact);
            stateMachine.saveActionToHistory("Inject reroute retry",
                    format("Reroute counter %d", rerouteCounter));
        }

        super.onCompleteWithFailedCommands(stateMachine);
    }
}

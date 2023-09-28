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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;
import org.openkilda.wfm.topology.flowhs.utils.YFlowUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Optional;

@Slf4j
public class HandleNotReroutedSubFlowAction
        extends NbTrackableWithHistorySupportAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {

    private final YFlowUtils utils;
    private final FlowRerouteService flowRerouteService;

    public HandleNotReroutedSubFlowAction(PersistenceManager persistenceManager,
                                          FlowRerouteService flowRerouteService) {
        super(persistenceManager);
        utils = new YFlowUtils(persistenceManager);
        this.flowRerouteService = flowRerouteService;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowRerouteContext context,
                                                    YFlowRerouteFsm stateMachine) {
        String subFlowId = context.getSubFlowId();

        if (stateMachine.isFailedSubFlow(subFlowId)) {
            log.debug("Ignore an error event for already failed sub-flow " + subFlowId);

            return Optional.empty();
        }

        if (!stateMachine.isReroutingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.saveErrorToHistory("Failed to reroute sub-flow",
                format("Failed to reroute sub-flow %s of y-flow %s: %s", subFlowId, stateMachine.getYFlowId(),
                        context.getError()));

        // Send only a single error response on the first occurrence.
        final boolean isFirstError = stateMachine.getFailedSubFlows().isEmpty();

        stateMachine.addFailedSubFlow(subFlowId);
        stateMachine.removeReroutingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        stateMachine.setMainErrorAndDescription(context.getErrorType(), context.getError());

        FlowRerouteRequest secondRerouteRequest = getSecondRerouteRequestOrNull(stateMachine);

        if (isForthcomingSubflowRequestLeft(isFirstError, secondRerouteRequest, subFlowId)) {
            prepareAndSendSecondSubFlowRerouteRequest(secondRerouteRequest, stateMachine);
        }

        if (stateMachine.getReroutingSubFlows().isEmpty()) {
            if (stateMachine.getFailedSubFlows().containsAll(stateMachine.getSubFlows())) {
                stateMachine.fire(Event.YFLOW_REROUTE_SKIPPED);
                stateMachine.setErrorReason(format("Failed to reroute all sub-flows of y-flow %s",
                        stateMachine.getYFlowId()));
            } else {
                stateMachine.fire(Event.FAILED_TO_REROUTE_SUB_FLOWS);
                stateMachine.setErrorReason(format("Failed to reroute sub-flows %s of y-flow %s",
                        stateMachine.getFailedSubFlows(), stateMachine.getYFlowId()));
            }
        }

        if (isNoSuccessAndNoMoreRequests(isFirstError, stateMachine)) {
            return Optional.of(stateMachine.buildErrorMessage(stateMachine.getMainError(),
                    getGenericErrorMessage(), stateMachine.getMainErrorDescription()));
        }

        if (isAnySuccessAndNoForthcomingRequests(stateMachine)) {
            return Optional.of(utils.buildRerouteResponseMessage(stateMachine));
        }

        return Optional.empty();
    }

    private FlowRerouteRequest getSecondRerouteRequestOrNull(YFlowRerouteFsm stateMachine) {
        return stateMachine.getRerouteRequests().size() == 2
                ? ((ArrayList<FlowRerouteRequest>) stateMachine.getRerouteRequests()).get(1) : null;
    }

    private boolean isForthcomingSubflowRequestLeft(boolean isFirstError,
                                                    FlowRerouteRequest secondRerouteRequest,
                                                    String currentSubFlowId) {
        return isFirstError && secondRerouteRequest != null
                && !currentSubFlowId.equals(secondRerouteRequest.getFlowId());
    }

    private void prepareAndSendSecondSubFlowRerouteRequest(FlowRerouteRequest secondRerouteRequest,
                                                           YFlowRerouteFsm stateMachine) {
        secondRerouteRequest.getAffectedIsls().clear();
        stateMachine.addReroutingSubFlow(secondRerouteRequest.getFlowId());
        stateMachine.notifyEventListeners(listener -> listener.onSubFlowProcessingStart(stateMachine.getYFlowId(),
                secondRerouteRequest.getFlowId()));
        CommandContext flowContext = stateMachine.getCommandContext().fork(secondRerouteRequest.getFlowId());
        flowRerouteService.startFlowRerouting(secondRerouteRequest, flowContext, stateMachine.getYFlowId());
    }

    private boolean isNoSuccessAndNoMoreRequests(boolean isFirstError, YFlowRerouteFsm stateMachine) {
        return !isFirstError || (isEmpty(stateMachine.getAllocatedSubFlows())
                && stateMachine.getReroutingSubFlows().isEmpty());
    }

    private boolean isAnySuccessAndNoForthcomingRequests(YFlowRerouteFsm stateMachine) {
        return isNotEmpty(stateMachine.getAllocatedSubFlows()) && isEmpty(stateMachine.getReroutingSubFlows());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}

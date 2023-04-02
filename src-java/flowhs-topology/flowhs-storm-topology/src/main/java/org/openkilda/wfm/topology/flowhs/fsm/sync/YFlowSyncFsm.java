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

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.Message;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.UnknownKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
public class YFlowSyncFsm extends SyncFsmBase<YFlowSyncFsm, State, Event> {
    public static final FsmExecutor<YFlowSyncFsm, State, Event, FlowSyncContext> EXECUTOR = new FsmExecutor<>(
            Event.NEXT);

    @Getter
    private final String yFlowId;

    private final Set<UUID> pendingSpeakerCommands = new HashSet<>();

    private final FlowSyncCarrier decoratedCarrier;

    public YFlowSyncFsm(
            @NonNull CommandContext commandContext, @NonNull Supplier<FlowSyncCarrier> carrierSupplier,
            @NonNull String yFlowId) {
        super(commandContext, carrierSupplier.get(), Event.NEXT, Event.ERROR);
        this.yFlowId = yFlowId;
        decoratedCarrier = new CarrierDecorator(carrierSupplier.get());
    }

    public void handleSpeakerResponse(SpeakerCommandResponse response) {
        if (! pendingSpeakerCommands.remove(response.getCommandId())) {
            log.error(
                    "Got speaker response, but it's missing in pending requests list"
                            + " (commandId={}, pending-commands-set={})",
                    response.getCommandId(),
                    pendingSpeakerCommands.stream().sorted()
                            .map(UUID::toString)
                            .collect(Collectors.joining(", ", "{", "}")));
            return;
        }

        FlowSyncContext context = FlowSyncContext.builder()
                .speakerRuleResponse(response)
                .build();
        Event event = response.isSuccess() ? Event.SPEAKER_RESPONSE : Event.SPEAKER_ERROR_RESPONSE;
        log.debug(
                "Y-flow \"{}\" sync got {} speaker command with id={}",
                yFlowId, event == Event.SPEAKER_RESPONSE ? "success" : "failed", response.getCommandId());
        injectEvent(event, context);
    }

    @Override
    protected void injectPathOperationResultEvent(FlowSyncContext context) {
        injectEvent(Event.PATH_OPERATION_RESPONSE, context);
    }

    @Override
    public void injectTimeoutEvent(FlowSyncContext context) {
        injectEvent(Event.TIMEOUT, context);
    }

    @Override
    public FlowSyncCarrier getCarrier() {
        return decoratedCarrier;
    }

    // -- FSM actions --

    public void enterCancelAction(State from, State to, Event event, FlowSyncContext context) {
        cancelPendingPathOperations(context);
    }

    public void checkPendingPathOperationsAction(State from, State to, Event event, FlowSyncContext context) {
        continueIfNoPendingPathOperations(context);
    }

    public void processSuccessSpeakerResponseAction(State from, State to, Event event, FlowSyncContext context) {
        log.info(
                "Y-flow \"{}\" sync got success response on speaker command (id={})",
                yFlowId, context.getSpeakerRuleResponse().getCommandId());
        continueIfNoPendingSpeakerCommands(context);
    }

    public void processFailedSpeakerResponseAction(State from, State to, Event event, FlowSyncContext context) {
        SpeakerCommandResponse response = context.getSpeakerRuleResponse();
        String errors = response.getFailedCommandIds().entrySet().stream()
                .map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("),(", "(", ")"));
        log.error(
                "Y-flow \"{}\" sync got failed response on speaker command: {} {}",
                yFlowId, response.getCommandId(), errors);
        continueIfNoPendingSpeakerCommands(context);
    }

    public void reportGlobalTimeoutAction(State from, State to, Event event, FlowSyncContext context) {
        saveGlobalTimeoutToHistory();
    }

    // -- private/service methods --

    @Override
    void handlePathSyncFailure(FlowSyncContext context) {
        fire(Event.PATH_OPERATION_FAIL, context);
    }

    @Override
    protected void allPathOperationsIsOverProceedToTheNextStep(FlowSyncContext context) {
        fire(Event.GUARD_PASSED, context);
    }

    @Override
    protected void injectEvent(Event event, FlowSyncContext context) {
        EXECUTOR.fire(this, event, context);
    }

    private void continueIfNoPendingSpeakerCommands(FlowSyncContext context) {
        if (pendingSpeakerCommands.isEmpty()) {
            fire(Event.GUARD_PASSED, context);
        }
    }

    // only to satisfy parents requirements
    @Override
    public String getFlowId() {
        return yFlowId;
    }

    @Override
    protected String getCrudActionName() {
        return "y-sync";
    }

    @Override
    protected State getFinishedState() {
        return State.FINISHED;
    }

    private class CarrierDecorator implements FlowSyncCarrier {
        private final FlowSyncCarrier target;

        public CarrierDecorator(FlowSyncCarrier target) {
            this.target = target;
        }

        @Override
        public void sendSpeakerRequest(SpeakerRequest command) {
            pendingSpeakerCommands.add(command.getCommandId());
            target.sendSpeakerRequest(command);
        }

        @Override
        public void sendPeriodicPingNotification(String flowId, boolean enabled) {
            target.sendPeriodicPingNotification(flowId, enabled);
        }

        @Override
        public void sendHistoryUpdate(FlowHistoryHolder historyHolder) {
            target.sendHistoryUpdate(historyHolder);
        }

        @Override
        public void cancelTimeoutCallback(String key) {
            target.cancelTimeoutCallback(key);
        }

        @Override
        public void sendInactive() {
            target.sendInactive();
        }

        @Override
        public void sendNorthboundResponse(Message message) {
            target.sendNorthboundResponse(message);
        }

        @Override
        public void launchFlowPathInstallation(
                @NonNull FlowPathRequest request, @NonNull FlowPathOperationConfig config,
                @NonNull CommandContext commandContext) throws DuplicateKeyException {
            target.launchFlowPathInstallation(request, config, commandContext);
        }

        @Override
        public void cancelFlowPathOperation(PathId pathId) throws UnknownKeyException {
            target.cancelFlowPathOperation(pathId);
        }
    }

    // -- FSM definition --

    public static class Factory {
        private final StateMachineBuilder<YFlowSyncFsm, State, Event, FlowSyncContext> builder;

        public Factory(
                @NonNull PersistenceManager persistenceManager,
                @NonNull FlowResourcesManager resourcesManager,
                @NonNull RuleManager ruleManager, @NonNull FlowPathOperationConfig flowPathOperationConfig) {
            builder = StateMachineBuilderFactory.create(
                    YFlowSyncFsm.class, State.class, Event.class, FlowSyncContext.class,
                    CommandContext.class, Supplier.class, String.class);

            final FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);

            final PathOperationResponseAction<YFlowSyncFsm, State, Event> pathOperationResponseAction =
                    new PathOperationResponseAction<>(persistenceManager);
            final String reportGlobalTimeoutActionMethod = "reportGlobalTimeoutAction";
            final String checkPendingPathOperationsActionMethod = "checkPendingPathOperationsAction";
            final String processSuccessSpeakerResponseActionMethod = "processSuccessSpeakerResponseAction";
            final String processFailedSpeakerResponseActionMethod = "processFailedSpeakerResponseAction";

            // SETUP
            builder.onEntry(State.SETUP)
                    .perform(new YFlowSyncSetupAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.SETUP).to(State.SYNC).on(Event.NEXT);
            builder.transition().from(State.SETUP).to(State.COMMIT_ERROR).on(Event.ERROR);

            // SYNC
            builder.onEntry(State.SYNC)
                    .perform(new CreateSyncHandlersAction<>(
                            persistenceManager, resourcesManager, flowPathOperationConfig));
            builder.transition().from(State.SYNC).to(State.SYNC_FAIL).on(Event.PATH_OPERATION_FAIL);
            builder.transition().from(State.SYNC).to(State.SYNC_Y_RULES).on(Event.GUARD_PASSED);
            builder.transition().from(State.SYNC).to(State.CANCEL).on(Event.ERROR);
            builder.transition().from(State.SYNC).to(State.CANCEL).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);
            builder.internalTransition().within(State.SYNC).on(Event.PATH_OPERATION_RESPONSE)
                    .perform(pathOperationResponseAction);

            // SYNC_Y_RULES
            builder.onEntry(State.SYNC_Y_RULES)
                    .perform(new EmitYRulesSyncRequestsAction(persistenceManager, ruleManager));
            builder.transition().from(State.SYNC_Y_RULES).to(State.COMMIT_SUCCESS).on(Event.GUARD_PASSED);
            builder.transition().from(State.SYNC_Y_RULES).to(State.COMMIT_ERROR).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);
            builder.transition().from(State.SYNC_Y_RULES).to(State.SYNC_Y_RULES_FAIL).on(Event.SPEAKER_ERROR_RESPONSE);
            builder.internalTransition().within(State.SYNC_Y_RULES).on(Event.SPEAKER_RESPONSE)
                    .callMethod(processSuccessSpeakerResponseActionMethod);

            // CANCEL
            builder.onEntry(State.CANCEL)
                    .callMethod("enterCancelAction");
            builder.transition().from(State.CANCEL).to(State.COMMIT_ERROR).on(Event.GUARD_PASSED);
            builder.internalTransition().within(State.CANCEL).on(Event.PATH_OPERATION_RESPONSE)
                    .perform(pathOperationResponseAction);
            builder.internalTransition().within(State.CANCEL).on(Event.ERROR)
                    .callMethod(checkPendingPathOperationsActionMethod);
            builder.internalTransition().within(State.CANCEL).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);

            // SYNC_FAIL
            builder.onEntry(State.SYNC_FAIL)
                    .callMethod(checkPendingPathOperationsActionMethod);
            builder.transition().from(State.SYNC_FAIL).to(State.COMMIT_ERROR).on(Event.GUARD_PASSED);
            builder.transition().from(State.SYNC_FAIL).to(State.CANCEL).on(Event.ERROR);
            builder.transition().from(State.SYNC_FAIL).to(State.CANCEL).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);
            builder.internalTransition().within(State.SYNC_FAIL).on(Event.PATH_OPERATION_RESPONSE)
                    .perform(pathOperationResponseAction);

            // SYNC_Y_RULES_FAIL
            builder.onEntry(State.SYNC_Y_RULES_FAIL)
                    .callMethod(processFailedSpeakerResponseActionMethod);
            builder.transition().from(State.SYNC_Y_RULES_FAIL).to(State.COMMIT_ERROR).on(Event.GUARD_PASSED);
            builder.transition().from(State.SYNC_Y_RULES_FAIL).to(State.COMMIT_ERROR).on(Event.TIMEOUT)
                    .callMethod(reportGlobalTimeoutActionMethod);
            builder.internalTransition().within(State.SYNC_Y_RULES_FAIL).on(Event.SPEAKER_RESPONSE)
                    .callMethod(processSuccessSpeakerResponseActionMethod);
            builder.internalTransition().within(State.SYNC_Y_RULES_FAIL).on(Event.SPEAKER_ERROR_RESPONSE)
                    .callMethod(processFailedSpeakerResponseActionMethod);

            // COMMIT_SUCCESS
            builder.onEntry(State.COMMIT_SUCCESS)
                    .perform(new YFlowSyncSuccessCompleteAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.COMMIT_SUCCESS).to(State.FINISHED).on(Event.NEXT);
            builder.transition().from(State.COMMIT_SUCCESS).to(State.COMMIT_ERROR).on(Event.ERROR);

            // COMMIT_ERROR
            builder.onEntry(State.COMMIT_ERROR)
                    .perform(new YFlowFailedCompleteAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.COMMIT_ERROR).to(State.FINISHED_WITH_ERROR).on(Event.NEXT);

            // final
            builder.defineFinalState(State.FINISHED);
            builder.defineFinalState(State.FINISHED_WITH_ERROR);
        }

        public YFlowSyncFsm newInstance(
                Supplier<FlowGenericCarrier> carrierSupplier, String flowId, CommandContext commandContext) {
            return builder.newStateMachine(State.SETUP, commandContext, carrierSupplier, flowId);
        }
    }

    public enum State {
        SETUP,
        SYNC, SYNC_FAIL, SYNC_Y_RULES, SYNC_Y_RULES_FAIL, CANCEL,
        COMMIT_SUCCESS, COMMIT_ERROR,
        FINISHED, FINISHED_WITH_ERROR
    }

    public enum Event {
        NEXT, ERROR, TIMEOUT,
        PATH_OPERATION_RESPONSE, SPEAKER_RESPONSE, SPEAKER_ERROR_RESPONSE, GUARD_PASSED,
        PATH_OPERATION_FAIL
    }
}

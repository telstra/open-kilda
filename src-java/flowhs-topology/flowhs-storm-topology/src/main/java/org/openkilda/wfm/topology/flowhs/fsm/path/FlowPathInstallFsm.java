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

package org.openkilda.wfm.topology.flowhs.fsm.path;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import lombok.NonNull;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

public class FlowPathInstallFsm extends FlowPathFsmBase<FlowPathInstallFsm> {
    private static final FsmExecutor<FlowPathInstallFsm, State, Event, FlowPathContext> EXECUTOR
            = new FsmExecutor<>(Event.NEXT);

    public FlowPathInstallFsm(
            @NonNull FlowPathOperationConfig config, @NonNull FlowPathRequest subject,
            @NonNull FlowGenericCarrier carrier, @NonNull CommandContext commandContext) {
        super(config, subject, carrier, commandContext);
    }

    // -- FSM actions --

    public void enterInstallAction(State from, State to, Event event, FlowPathContext context) {
        historyFormatter = new FlowPathInstallHistoryFormatter(this);

        if (! swapChunk()) {
            handleEvent(Event.NO_MORE_CHUNKS, context);
            return;
        }

        log.info("Installing {} path chunk ({} entries)",
                currentChunk.getType(), currentChunk.getPayload().size());
        handleEvent(
                () -> processCurrentChunk(factory -> factory.makeInstallRequest(commandIdGenerator.generate())),
                context);
    }

    public void enterVerifyAction(State from, State to, Event event, FlowPathContext context) {
        historyFormatter = new FlowPathVerifyHistoryFormatter(this);
        log.info("Verifying installed {} path chunk ({} entries)",
                currentChunk.getType(), currentChunk.getPayload().size());

        handleEvent(
                () -> processCurrentChunk(factory -> factory.makeVerifyRequest(commandIdGenerator.generate())),
                context);
    }

    public void enterRevertAction(State from, State to, Event event, FlowPathContext context) {
        if (! subject.isCanRevertOnError()) {
            log.info("Do not perform path install revert operation (denied by request settings)");
            handleEvent(Event.CHUNK_COMPLETE, context);
            return;
        }

        historyFormatter = new FlowPathAbortInstallHistoryFormatter(this);

        currentChunk = buildRevertChunk();
        log.info("Reverting possible installed path segments ({} entries)", currentChunk.getPayload().size());

        handleEvent(
                () -> processCurrentChunk(factory -> factory.makeRemoveRequest(commandIdGenerator.generate())),
                context);
    }

    public void enterEndAction(State from, State to, Event event, FlowPathContext context) {
        setResultCode(FlowPathResultCode.SUCCESS);
    }

    public void speakerResponseAction(State from, State to, Event event, FlowPathContext context) {
        handleEvent(() -> processSpeakerResponse(context.getSpeakerResponse()), context);
    }

    public void markCanceledAction(State from, State to, Event event, FlowPathContext context) {
        setResultCode(FlowPathResultCode.CANCEL);
    }

    public void markSpeakerErrorAction(State from, State to, Event event, FlowPathContext context) {
        setResultCode(FlowPathResultCode.SPEAKER_ERROR);
    }

    // -- private/service methods --

    @Override
    protected void handleEvent(Event event, FlowPathContext context) {
        EXECUTOR.fire(this, event, context);
    }

    // -- FSM definition --

    public static class Factory {
        private final StateMachineBuilder<FlowPathInstallFsm, State, Event, FlowPathContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier) {
            builder = StateMachineBuilderFactory.create(
                    FlowPathInstallFsm.class, State.class, Event.class, FlowPathContext.class,
                    FlowPathOperationConfig.class, FlowPathRequest.class, FlowGenericCarrier.class,
                    CommandContext.class);
            this.carrier = carrier;

            final String speakerResponseActionMethod = "speakerResponseAction";
            final String markCanceledActionMethod = "markCanceledAction";
            final String markSpeakerErrorActionMethod = "markSpeakerErrorAction";

            // INSTALL
            builder.onEntry(State.INSTALL)
                    .callMethod("enterInstallAction");
            builder.transition().from(State.INSTALL).to(State.REVERT).on(Event.ERROR)
                    .callMethod(markSpeakerErrorActionMethod);
            builder.transition().from(State.INSTALL).to(State.REVERT_WAIT_PENDING).on(Event.CANCEL)
                    .callMethod(markCanceledActionMethod);
            builder.transition().from(State.INSTALL).to(State.VERIFY).on(Event.CHUNK_COMPLETE);
            builder.transition().from(State.INSTALL).to(State.END).on(Event.NO_MORE_CHUNKS);
            builder.internalTransition().within(State.INSTALL).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // VERIFY
            builder.onEntry(State.VERIFY)
                    .callMethod("enterVerifyAction");
            builder.transition().from(State.VERIFY).to(State.REVERT).on(Event.ERROR)
                    .callMethod(markSpeakerErrorActionMethod);
            builder.transition().from(State.VERIFY).to(State.REVERT_WAIT_PENDING).on(Event.CANCEL)
                    .callMethod(markCanceledActionMethod);
            builder.transition().from(State.VERIFY).to(State.INSTALL).on(Event.CHUNK_COMPLETE);
            builder.internalTransition().within(State.VERIFY).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // REVERT_WAIT_PENDING
            builder.transition().from(State.REVERT_WAIT_PENDING).to(State.END).on(Event.CHUNK_COMPLETE);
            builder.transition().from(State.REVERT_WAIT_PENDING).to(State.END).on(Event.ERROR);
            builder.internalTransition().within(State.REVERT_WAIT_PENDING).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // REVERT
            builder.onEntry(State.REVERT)
                    .callMethod("enterRevertAction");
            builder.transition().from(State.REVERT).to(State.END).on(Event.CHUNK_COMPLETE);
            builder.transition().from(State.REVERT).to(State.END).on(Event.ERROR);
            builder.internalTransition().within(State.REVERT).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // END
            builder.onEntry(State.END)
                    .callMethod("enterEndAction");

            builder.defineFinalState(State.END);
        }

        public FlowPathInstallFsm newInstance(
                @NonNull FlowPathOperationConfig config,
                @NonNull FlowPathRequest subject, @NonNull CommandContext commandContext) {
            FlowPathInstallFsm fsm = builder.newStateMachine(State.INSTALL, config, subject, carrier, commandContext);
            fsm.initFsm();
            return fsm;
        }
    }
}

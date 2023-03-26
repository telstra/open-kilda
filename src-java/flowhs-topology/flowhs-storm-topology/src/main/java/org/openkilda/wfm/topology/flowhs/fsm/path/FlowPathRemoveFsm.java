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

public class FlowPathRemoveFsm extends FlowPathFsmBase<FlowPathRemoveFsm> {
    private static final FsmExecutor<FlowPathRemoveFsm, State, Event, FlowPathContext> EXECUTOR
            = new FsmExecutor<>(Event.NEXT);

    public FlowPathRemoveFsm(
            @NonNull FlowPathOperationConfig config, @NonNull FlowPathRequest subject,
            @NonNull FlowGenericCarrier carrier, @NonNull CommandContext commandContext) {
        super(config, subject, carrier, commandContext);
    }

    // -- FSM actions --

    public void enterRemoveAction(State from, State to, Event event, FlowPathContext context) {
        historyFormatter = new FlowPathRemoveHistoryFormatter(this);

        if (!swapChunk()) {
            handleEvent(Event.NO_MORE_CHUNKS, context);
            return;
        }

        log.info("Removing {} path chunk ({} entries)",
                currentChunk.getType(), currentChunk.getPayload().size());
        handleEvent(
                processCurrentChunk(factory -> factory.makeRemoveRequest(commandIdGenerator.generate()))
                        .orElse(Event.NEXT),
                context);
    }

    public void enterRevertAction(State from, State to, Event event, FlowPathContext context) {
        if (! subject.isCanRevertOnError()) {
            log.info("Do not perform path remove revert operation (denied by request settings)");
            handleEvent(Event.CHUNK_COMPLETE, context);
            return;
        }

        historyFormatter = new FlowPathAbortRemoveHistoryFormatter(this);

        currentChunk = buildRevertChunk();
        log.info("Reverting possible removed path segments ({} entries)", currentChunk.getPayload().size());

        handleEvent(
                () -> processCurrentChunk(factory -> factory.makeInstallRequest(commandIdGenerator.generate())),
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
        private final StateMachineBuilder<FlowPathRemoveFsm, State, Event, FlowPathContext> builder;
        private final FlowGenericCarrier carrier;

        public Factory(@NonNull FlowGenericCarrier carrier) {
            builder = StateMachineBuilderFactory.create(
                    FlowPathRemoveFsm.class, State.class, Event.class, FlowPathContext.class,
                    FlowPathOperationConfig.class, FlowPathRequest.class, FlowGenericCarrier.class,
                    CommandContext.class);
            this.carrier = carrier;

            final String speakerResponseActionMethod = "speakerResponseAction";
            final String markCanceledActionMethod = "markCanceledAction";
            final String markSpeakerErrorActionMethod = "markSpeakerErrorAction";

            // DELETE
            builder.onEntry(State.DELETE)
                    .callMethod("enterRemoveAction");
            builder.transition().from(State.DELETE).to(State.WAIT_PENDING).on(Event.NEXT);
            builder.transition().from(State.DELETE).to(State.REVERT).on(Event.ERROR)
                    .callMethod(markSpeakerErrorActionMethod);
            builder.transition().from(State.DELETE).to(State.REVERT_WAIT_PENDING).on(Event.CANCEL)
                    .callMethod(markCanceledActionMethod);
            builder.transition().from(State.DELETE).to(State.END).on(Event.CHUNK_COMPLETE);
            builder.transition().from(State.DELETE).to(State.END).on(Event.NO_MORE_CHUNKS);

            // WAIT_PENDING
            builder.transition().from(State.WAIT_PENDING).to(State.REVERT).on(Event.ERROR)
                    .callMethod(markSpeakerErrorActionMethod);
            builder.transition().from(State.WAIT_PENDING).to(State.REVERT_WAIT_PENDING).on(Event.CANCEL)
                    .callMethod(markCanceledActionMethod);
            builder.transition().from(State.WAIT_PENDING).to(State.DELETE).on(Event.CHUNK_COMPLETE);
            builder.internalTransition().within(State.WAIT_PENDING).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // REVERT_WAIT_PENDING
            builder.transition().from(State.REVERT_WAIT_PENDING).to(State.REVERT).on(Event.ERROR);
            builder.transition().from(State.REVERT_WAIT_PENDING).to(State.REVERT).on(Event.CHUNK_COMPLETE);
            builder.internalTransition().within(State.REVERT_WAIT_PENDING).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // REVERT
            builder.onEntry(State.REVERT)
                    .callMethod("enterRevertAction");
            builder.transition().from(State.REVERT).to(State.END).on(Event.ERROR);
            builder.transition().from(State.REVERT).to(State.END).on(Event.CHUNK_COMPLETE);
            builder.internalTransition().within(State.REVERT).on(Event.SPEAKER_RESPONSE)
                    .callMethod(speakerResponseActionMethod);

            // END
            builder.onEntry(State.END)
                    .callMethod("enterEndAction");

            builder.defineFinalState(State.END);
        }

        public FlowPathRemoveFsm newInstance(
                @NonNull FlowPathOperationConfig config,
                @NonNull FlowPathRequest subject, @NonNull CommandContext commandContext) {
            FlowPathRemoveFsm fsm = builder.newStateMachine(State.DELETE, config, subject, carrier, commandContext);
            fsm.initFsm();
            return fsm;
        }
    }
}

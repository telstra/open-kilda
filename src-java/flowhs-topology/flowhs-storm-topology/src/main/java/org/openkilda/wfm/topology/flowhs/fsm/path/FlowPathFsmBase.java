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

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.FsmUtil;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.path.FlowPathFsmBase.Event;
import org.openkilda.wfm.topology.flowhs.fsm.path.FlowPathFsmBase.State;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathChunk;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResult;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.ProcessingEventListener;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.Getter;
import lombok.NonNull;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// TODO(surabujin): reconsider inheritance from FlowProcessingWithHistorySupportFsm
// TODO(surabujin): request round trip timer
public abstract class FlowPathFsmBase<
        T extends AbstractStateMachine<T, State, Event, FlowPathContext>>
        extends FlowProcessingWithHistorySupportFsm<
        T, FlowPathFsmBase.State, FlowPathFsmBase.Event, FlowPathContext, FlowGenericCarrier, ProcessingEventListener>
        implements FlowPathOperation {
    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    protected final FlowPathOperationConfig config;
    protected final FlowPathRequest subject;

    @Getter
    protected final CompletableFuture<FlowPathResult> resultFuture = new CompletableFuture<>();

    @Getter
    private FlowPathResultCode resultCode;

    protected FlowPathChunk currentChunk;
    protected final List<FlowPathChunk> chunksToProcess;
    protected final List<FlowPathChunk> completedChunks = new ArrayList<>();

    protected FlowPathHistoryFormatter historyFormatter = new DummyFlowPathHistoryFormatter();

    private final Map<UUID, PendingEntry> pendingRequests = new HashMap<>();
    private final List<PendingEntry> failedRequests = new ArrayList<>();
    private int failedRequestsBase = 0;

    public FlowPathFsmBase(
            @NonNull FlowPathOperationConfig config, @NonNull FlowPathRequest subject,
            @NonNull FlowGenericCarrier carrier, @NonNull CommandContext commandContext) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier);

        this.config = config;
        this.subject = subject;

        chunksToProcess = new ArrayList<>(subject.getPathChunks());
    }

    // -- public API --

    @Override
    public String getFlowId() {
        return getPathReference().getFlowId();
    }

    @Override
    protected String getCrudActionName() {
        return "path-install";
    }

    @Override
    public FlowPathReference getPathReference() {
        return subject.getReference();
    }

    @Override
    public void handleSpeakerResponse(@NonNull SpeakerFlowSegmentResponse response) {
        FlowPathContext context = FlowPathContext.builder()
                .speakerResponse(response)
                .build();
        handleEvent(Event.SPEAKER_RESPONSE, context);
    }

    @Override
    public void handleCancel() {
        FlowPathContext context = FlowPathContext.builder().build();
        handleEvent(Event.CANCEL, context);
    }

    // -- private/service methods --

    protected Optional<Event> processCurrentChunk(
            Function<FlowSegmentRequestFactory, FlowSegmentRequest> requestFactoryAdapter) {
        for (FlowSegmentRequestFactory entry : currentChunk.getPayload()) {
            PendingEntry pendingEntry = new PendingEntry(entry, requestFactoryAdapter, currentChunk.getType());
            emitRequest(pendingEntry);
        }
        historyFormatter.recordChunkProcessing(currentChunk.getType());

        if (pendingRequests.isEmpty()) {
            return Optional.of(Event.CHUNK_COMPLETE);
        }

        return Optional.empty();
    }

    protected Optional<Event> processSpeakerResponse(SpeakerFlowSegmentResponse response) {
        PendingEntry pendingEntry = pendingRequests.remove(response.getCommandId());
        if (pendingEntry == null) {
            log.error("Received a response for unexpected command: {}", response);
            return Optional.empty();
        }

        if (response instanceof FlowErrorResponse) {
            return processSpeakerResponse((FlowErrorResponse) response, pendingEntry);
        } else {
            return processSpeakerResponse(response, pendingEntry);
        }
    }

    protected Optional<Event> processSpeakerResponse(SpeakerFlowSegmentResponse response, PendingEntry pendingEntry) {
        historyFormatter.recordSegmentSuccess(currentChunk.getType(), pendingEntry.getRequest(), response);
        return chooseTransitionEventIfChunkCompleted();
    }

    protected Optional<Event> processSpeakerResponse(FlowErrorResponse response, PendingEntry previous) {
        PendingEntry current = PendingEntry.newAttempt(previous);
        if (config.getSpeakerCommandRetriesLimit() < current.getAttempt()) {
            historyFormatter.recordSegmentFailureAndGiveUp(currentChunk.getType(), previous.getRequest(), response);
            failedRequests.add(previous);
        } else {
            historyFormatter.recordSegmentFailureAndRetry(
                    currentChunk.getType(), previous.getRequest(), response, current.getAttempt());
            emitRequest(current);
        }
        return chooseTransitionEventIfChunkCompleted();
    }

    protected Optional<Event> chooseTransitionEventIfChunkCompleted() {
        if (!pendingRequests.isEmpty()) {
            return Optional.empty();
        }

        List<FlowSegmentRequest> failRequests = getFailedRequestsForCurrentChunk()
                .stream()
                .map(PendingEntry::getRequest)
                .collect(Collectors.toList());
        if (!failRequests.isEmpty()) {
            historyFormatter.recordChunkFailure(currentChunk.getType(), failRequests);
            return Optional.of(Event.ERROR);
        } else {
            return Optional.of(Event.CHUNK_COMPLETE);
        }
    }

    protected FlowPathChunk buildRevertChunk() {
        List<FlowSegmentRequestFactory> payload = new ArrayList<>();
        for (FlowPathChunk entry : completedChunks) {
            payload.addAll(entry.getPayload());
        }
        if (currentChunk != null) {
            payload.addAll(currentChunk.getPayload());
        }

        return new FlowPathChunk(PathChunkType.REVERT, payload);
    }

    protected boolean swapChunk() {
        if (chunksToProcess.isEmpty()) {
            return false;
        }

        if (currentChunk != null) {
            completedChunks.add(currentChunk);
        }
        currentChunk = chunksToProcess.remove(0);

        pendingRequests.clear();
        failedRequestsBase = failedRequests.size();

        return true;
    }

    protected void emitRequest(PendingEntry pendingEntry) {
        FlowSegmentRequest request = pendingEntry.getRequest();
        UUID requestId = request.getCommandId();

        log.info(
                "Emit speaker flow segment {} {} request to the switch={} cookie={} pathId={} attempt={}",
                pendingEntry.getType(), detectFlowSegmentRequestType(request), request.getSwitchId(),
                request.getCookie(), subject.getReference().getPathId(), pendingEntry.getAttempt());
        getCarrier().sendSpeakerRequest(request);
        pendingRequests.put(requestId, pendingEntry);
    }

    protected void handleEvent(Supplier<Optional<Event>> eventSupplier, FlowPathContext context) {
        eventSupplier.get()
                .ifPresent(event -> handleEvent(event, context));
    }

    protected abstract void handleEvent(Event event, FlowPathContext context);

    protected void handleTermination() {
        resultFuture.complete(new FlowPathResult(getPathReference(), resultCode));
    }

    protected List<PendingEntry> getFailedRequestsForCurrentChunk() {
        return failedRequests.subList(failedRequestsBase, failedRequests.size());
    }

    protected void setResultCode(FlowPathResultCode code) {
        if (resultCode == null) {
            resultCode = code;
        } else {
            log.debug("Do not replace result code with {}, current value {}", code, resultCode);
        }
    }

    private static String detectFlowSegmentRequestType(FlowSegmentRequest request) {
        String result;
        if (request.isInstallRequest()) {
            result = "INSTALL";
        } else if (request.isVerifyRequest()) {
            result = "VERIFY";
        } else if (request.isRemoveRequest()) {
            result = "REMOVE";
        } else {
            result = "(unknown)";
        }
        return result;
    }

    protected void initFsm() {
        addTerminateListener(dummy -> handleTermination());
        FsmUtil.addExecutionTimeMeter(this, () -> getResultCode() == FlowPathResultCode.SUCCESS);

        FlowPathContext context = FlowPathContext.builder().build();
        start(context);
    }

    enum State {
        INSTALL, DELETE, VERIFY,
        WAIT_PENDING, REVERT_WAIT_PENDING, REVERT,
        END
    }

    enum Event {
        NEXT, ERROR,
        SPEAKER_RESPONSE, CANCEL,

        CHUNK_COMPLETE, NO_MORE_CHUNKS
    }
}

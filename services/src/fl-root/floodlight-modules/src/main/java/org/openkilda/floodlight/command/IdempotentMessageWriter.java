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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.error.OfConflictException;
import org.openkilda.floodlight.error.SessionErrorResponseException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * OF ofMessage writer that helps to perform some check/validation before command execution. For instance if you need
 * to check whether a switch already has installed a specific rule, if don't - install it on a switch.
 */
@Slf4j
public class IdempotentMessageWriter<T extends OFStatsReply> extends MessageWriter {

    private final OFRequest<T> request;
    private final Function<T, Boolean> ofEntryChecker;
    private final ErrorTypeHelper errorTypeHelper;

    @Builder
    public IdempotentMessageWriter(@NonNull OFMessage message,
                                   @NonNull OFRequest<T> request,
                                   @NonNull Function<T, Boolean> ofEntryChecker,
                                   @NonNull ErrorTypeHelper errorTypeHelper) {
        super(message);
        this.request = request;
        this.ofEntryChecker = ofEntryChecker;
        this.errorTypeHelper = errorTypeHelper;
    }

    @Override
    public CompletableFuture<Optional<OFMessage>> writeTo(IOFSwitch sw, SessionService sessionService)
            throws SwitchWriteException {
        CompletableFuture<Optional<OFMessage>> result = new CompletableFuture<>();

        super.writeTo(sw, sessionService)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        SessionErrorResponseException exception = (SessionErrorResponseException) error.getCause();
                        OFErrorMsg errorMsg = exception.getErrorResponse();
                        if (errorTypeHelper.isConflict(errorMsg)) {
                            checkConflict(sw)
                                    .thenAccept(Void -> result.complete(response));
                        } else {
                            result.completeExceptionally(error);
                        }
                    } else {
                        result.complete(response);
                    }
                });

        return result;
    }

    private CompletableFuture<Optional<OFMessage>> checkConflict(IOFSwitch sw) {
        CompletableFuture<T> loadStatsStage = new CompletableFutureAdapter<>(sw.writeRequest(request));

        return loadStatsStage.thenApply(entries -> {
            if (ofEntryChecker.apply(entries)) {
                log.info("The entry wasn't installed: switch {} already has the same entry.", sw.getId());
                return Optional.empty();
            } else {
                log.warn("Entry conflict on switch {}", sw.getId());
                throw new CompletionException(new OfConflictException(sw.getId(), ofMessage));
            }
        });
    }

    public interface ErrorTypeHelper {
        boolean isConflict(OFErrorMsg errorMsg);
    }
}

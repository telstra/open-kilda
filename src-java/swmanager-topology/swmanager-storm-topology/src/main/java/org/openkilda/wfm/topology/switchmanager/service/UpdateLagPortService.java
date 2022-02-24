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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.grpc.CreateOrUpdateLogicalPortResponse;
import org.openkilda.messaging.swmanager.request.UpdateLagPortRequest;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.service.handler.LagPortUpdateHandler;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

@Slf4j
public class UpdateLagPortService implements SwitchManagerHubService {
    @Getter
    private final SwitchManagerCarrier carrier;

    private final LagPortOperationService operationService;

    @VisibleForTesting
    final Map<String, LagPortUpdateHandler> activeHandlers = new HashMap<>();

    private boolean active = true;

    public UpdateLagPortService(SwitchManagerCarrier carrier, LagPortOperationConfig config) {
        this(carrier, new LagPortOperationService(config));
    }

    public UpdateLagPortService(SwitchManagerCarrier carrier, LagPortOperationService operationService) {
        this.carrier = carrier;
        this.operationService = operationService;
    }

    /**
     * Handle update request.
     */
    public void update(String requestKey, UpdateLagPortRequest request) {
        LagPortUpdateHandler handler = newHandler(requestKey, request);
        LagPortUpdateHandler existing = activeHandlers.put(
                requestKey, handler);
        if (existing != null) {
            activeHandlers.put(requestKey, existing);  // restore handler
            throw new InconsistentDataException(
                    String.format(
                            "LAG logical port update requests collision, requests with key=%s already exists",
                            requestKey));
        }

        process(requestKey, handler, (h, dummy) -> h.start());
    }

    @Override
    public void timeout(@NonNull MessageCookie cookie) {
        try {
            process(cookie, (h, dummy) -> {
                log.debug("Got timeout notification for {}", h.formatLagPortReference());
                h.timeout();
            });
        } catch (MessageDispatchException e) {
            log.debug("There is no handler for timeout notification {}", cookie);
        }
    }

    @Override
    public void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws MessageDispatchException, UnexpectedInputException {
        if (payload instanceof CreateOrUpdateLogicalPortResponse) {
            process(cookie, (h, nested) -> h.dispatchGrpcResponse((CreateOrUpdateLogicalPortResponse) payload, nested));
        } else {
            throw new UnexpectedInputException(payload);
        }
    }

    @Override
    public void dispatchWorkerMessage(ErrorData payload, MessageCookie cookie) throws MessageDispatchException {
        process(cookie, (h,  nested) -> h.dispatchGrpcResponse(payload, nested));
    }

    @Override
    public void activate() {
        active = true;
    }

    @Override
    public boolean deactivate() {
        active = false;
        return isAllOperationsCompleted();
    }

    @Override
    public boolean isAllOperationsCompleted() {
        return ! active && activeHandlers.isEmpty();
    }

    private void process(MessageCookie cookie, BiConsumer<LagPortUpdateHandler, MessageCookie> action)
            throws MessageDispatchException {
        if (cookie == null) {
            throw new MessageDispatchException();
        }

        LagPortUpdateHandler handler = activeHandlers.get(cookie.getValue());
        if (handler == null) {
            throw new MessageDispatchException(cookie);
        }
        process(cookie.getValue(), handler, cookie.getNested(), action);
    }

    private void process(
            String requestKey, LagPortUpdateHandler handler, BiConsumer<LagPortUpdateHandler, MessageCookie> action) {
        process(requestKey, handler, null, action);
    }

    private void process(
            String requestKey, LagPortUpdateHandler handler, MessageCookie cookie,
            BiConsumer<LagPortUpdateHandler, MessageCookie> action) {
        boolean success = false;
        try {
            action.accept(handler, cookie);
            success = true;
        } catch (SwitchManagerException e) {
            errorResponse(requestKey, e.getError(), handler.getGoal(), e.getMessage());
        } catch (Exception e) {
            UpdateLagPortRequest goal = handler.getGoal();
            log.error(
                    "Error processing LAG update request for port #{} on {} with request key {}: {}",
                    goal.getLogicalPortNumber(), goal.getSwitchId(), requestKey, e.getMessage(),
                    e);
            errorResponse(
                    requestKey, ErrorType.INTERNAL_ERROR, goal, "Internal error, see application logs for details");
        } finally {
            if (! success || handler.isCompleted()) {
                UpdateLagPortRequest goal = handler.getGoal();
                log.debug(
                        "Remove LAG logical port #{} on {} update handler (isSuccess: {}, isComplete: {})",
                        goal.getLogicalPortNumber(), goal.getSwitchId(),
                        success, handler.isCompleted());
                activeHandlers.remove(requestKey);
                carrier.cancelTimeoutCallback(requestKey);
                processPossiblePostponedDeactivation();
            }
        }
    }

    private void errorResponse(String requestKey, ErrorType type, UpdateLagPortRequest goal, String description) {
        String message = String.format(
                "Error processing LAG logical port #%d on %s update request",
                goal.getLogicalPortNumber(), goal.getSwitchId());
        carrier.errorResponse(requestKey, type, message, description);
    }

    private void processPossiblePostponedDeactivation() {
        if (! active && activeHandlers.isEmpty()) {
            carrier.sendInactive();
        }
    }

    private LagPortUpdateHandler newHandler(String requestKey, UpdateLagPortRequest request) {
        return new LagPortUpdateHandler(
                new SwitchManagerCarrierCookieDecorator(carrier, requestKey), operationService,
                requestKey, request);
    }
}

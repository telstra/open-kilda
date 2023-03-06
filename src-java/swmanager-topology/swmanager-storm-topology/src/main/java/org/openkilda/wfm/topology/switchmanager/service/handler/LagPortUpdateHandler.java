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

package org.openkilda.wfm.topology.switchmanager.service.handler;

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.grpc.CreateOrUpdateLogicalPortRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.grpc.CreateOrUpdateLogicalPortResponse;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.messaging.swmanager.request.UpdateLagPortRequest;
import org.openkilda.messaging.swmanager.response.LagPortResponse;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;
import org.openkilda.wfm.topology.switchmanager.error.SwitchManagerException;
import org.openkilda.wfm.topology.switchmanager.model.LagRollbackData;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationService;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class LagPortUpdateHandler {
    private static final int COOKIE_GENERATION_ATTEMPTS_LIMIT = 5;
    private static final NoArgGenerator cookieValueGenerator = Generators.randomBasedGenerator();

    private final SwitchManagerCarrier carrier;

    private final LagPortOperationService operationService;

    private final String requestKey;
    @Getter
    private final UpdateLagPortRequest goal;

    @VisibleForTesting
    LagRollbackData rollbackData;

    private final Set<MessageCookie> pendingSpeakerRequests = new HashSet<>();

    public LagPortUpdateHandler(
            SwitchManagerCarrier carrier, LagPortOperationService operationService, String requestKey,
            UpdateLagPortRequest goal) {
        this.carrier = carrier;
        this.operationService = operationService;
        this.requestKey = requestKey;
        this.goal = goal;
    }

    /**
     * Handle start event.
     */
    public void start() {
        Set<Integer> targetPorts = new HashSet<>(goal.getTargetPorts());
        rollbackData = operationService.updateLagPort(
                goal.getSwitchId(), goal.getLogicalPortNumber(), targetPorts, goal.isLacpReply());

        CreateOrUpdateLogicalPortRequest request = newGrpcRequest(targetPorts);
        MessageCookie cookie = newMessageCookie();

        log.info(
                "Going to update {}, target ports set: {}, target LACP reply {}",
                formatLagPortReference(), formatTargetPorts(goal.getTargetPorts()), goal.isLacpReply());
        carrier.sendCommandToSpeaker(request, cookie);
        pendingSpeakerRequests.add(cookie);
    }

    /**
     * Handle GRPC response.
     */
    public void dispatchGrpcResponse(CreateOrUpdateLogicalPortResponse response, MessageCookie cookie) {
        if (! pendingSpeakerRequests.remove(cookie)) {
            logUnwantedResponse(response);
            return;
        }

        if (rollbackData.isLacpReply() == goal.isLacpReply()) {
            sendResponseToNorthbound();
        } else {
            sendCommandsToFloodlight();
        }
    }

    private void sendCommandsToFloodlight() {
        List<OfCommand> commands = operationService.buildLacpSpeakerCommands(
                goal.getSwitchId(), goal.getLogicalPortNumber());
        MessageCookie floodlightMessageCookie = newMessageCookie();
        OfCommandAction action = goal.isLacpReply() ? OfCommandAction.INSTALL : OfCommandAction.DELETE;

        log.info("Sending {} commands {} to switch {} to update LAG port {} from {} to {}",
                action, commands, goal.getSwitchId(), goal.getLogicalPortNumber(), rollbackData, goal);
        carrier.sendOfCommandsToSpeaker(commands, action, goal.getSwitchId(), floodlightMessageCookie);
        pendingSpeakerRequests.add(floodlightMessageCookie);
    }

    /**
     * Handle Floodlight response.
     */
    public void dispatchFloodlightResponse(SpeakerResponse response, MessageCookie cookie) {
        if (!pendingSpeakerRequests.remove(cookie)) {
            logUnwantedResponse(response);
            return;
        }

        log.info("OpenFLow rules for {} have been updated", formatLagPortReference());
        sendResponseToNorthbound();
    }

    /**
     * Handle error response.
     */
    public void dispatchErrorResponse(ErrorData response, MessageCookie cookie) {
        if (!pendingSpeakerRequests.remove(cookie)) {
            logUnwantedResponse(response);
            return;
        }

        log.error("Unable to update {}: {}", formatLagPortReference(), response);

        // TODO(surabujin): should we retry update attempt?

        fail(response.getErrorType(), response.getErrorMessage());
    }

    /**
     * Handle timeout event.
     */
    public void timeout() {
        fail(ErrorType.OPERATION_TIMED_OUT, "Timeout communication switch via GRPC");

        pendingSpeakerRequests.clear(); // force handle completion
    }

    public boolean isCompleted() {
        return pendingSpeakerRequests.isEmpty();
    }

    private void sendResponseToNorthbound() {
        log.info("{} have been updated", formatLagPortReference());
        carrier.response(requestKey, new LagPortResponse(
                goal.getLogicalPortNumber(), goal.getTargetPorts(), goal.isLacpReply()));
    }

    private void fail(ErrorType type, String description) {
        String errorMessage;
        if (rollback()) {
            errorMessage = String.format("Unable to update %s", formatLagPortReference());
        } else {
            errorMessage = String.format(
                    "Unable to update %s, also DB data rollback have failed, use switch validate/sync to restore "
                            + "system's consistent state",
                    formatLagPortReference());
        }

        carrier.errorResponse(requestKey, type, errorMessage, description);
    }

    private boolean rollback() {
        try {
            operationService.updateLagPort(goal.getSwitchId(), goal.getLogicalPortNumber(),
                    rollbackData.getPhysicalPorts(), rollbackData.isLacpReply());
        } catch (SwitchManagerException e) {
            log.error("Unable to rollback DB update for {}: {}", formatLagPortReference(), e.getMessage());
            return false;
        }
        return true;
    }

    private void logUnwantedResponse(Serializable response) {
        log.info("Got unwanted/outdated GRPC response: {}", response);
    }

    private MessageCookie newMessageCookie() {
        for (int i = 0; i < COOKIE_GENERATION_ATTEMPTS_LIMIT; i++) {
            MessageCookie attempt = new MessageCookie(cookieValueGenerator.generate().toString());
            if (pendingSpeakerRequests.contains(attempt)) {
                continue;
            }

            return attempt;
        }

        throw new IllegalStateException(String.format(
                "Unable to generate request cookie (made %d attempts)", COOKIE_GENERATION_ATTEMPTS_LIMIT));
    }

    private CreateOrUpdateLogicalPortRequest newGrpcRequest(Set<Integer> targetPorts) {
        String address = operationService.getSwitchIpAddress(goal.getSwitchId());
        return new CreateOrUpdateLogicalPortRequest(
                address, targetPorts, goal.getLogicalPortNumber(), LogicalPortType.LAG);
    }

    public String formatLagPortReference() {
        return String.format("LAG logical port #%d on %s", goal.getLogicalPortNumber(), goal.getSwitchId());
    }

    private static String formatTargetPorts(Set<Integer> origin) {
        List<Integer> ports = new ArrayList<>(origin);
        Collections.sort(ports);
        return ports.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}

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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.DumpRulesForSwitchManagerRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.flow.SingleFlowDumpResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerCommandCarrier;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class SpeakerWorkerService {
    private final SpeakerCommandCarrier carrier;

    private final Map<String, RequestContext> keyToRequest = new HashMap<>();

    /**
     * The storage for received chunked message ids. It is needed to identify whether we have already received specific
     * chunked message or not in order to do not have duplicates, because current version of kafka do not guarantee
     * exactly once delivery.
     */
    private final Map<String, Set<String>> chunkedMessageIdsPerRequest = new HashMap<>();
    /**
     * Chains of chunked messages, it is filling by messages one by one as soon as the next linked message is received.
     */
    private final Map<String, List<ChunkedInfoMessage>> messagesChains;

    public SpeakerWorkerService(SpeakerCommandCarrier carrier, int chunkedMessagesExpirationMinutes) {
        this.carrier = carrier;
        messagesChains = new PassiveExpiringMap<>(chunkedMessagesExpirationMinutes, TimeUnit.MINUTES, new HashMap<>());
    }

    /**
     * Sends command to Floodlight speaker.
     * @param key unique operation's key.
     * @param command command to be executed.
     */
    public void sendFloodlightCommand(String key, CommandData command, MessageCookie cookie) throws PipelineException {
        log.debug("Got Floodlight request from hub bolt {}", command);
        keyToRequest.put(key, new RequestContext(command, null, cookie));
        carrier.sendFloodlightCommand(key, new CommandMessage(command, System.currentTimeMillis(), key));
    }

    /**
     * Sends RuleManager request to Floodlight speaker.
     * @param key unique operation's key.
     * @param request request to be processed.
     */
    public void sendFloodlightOfRequest(String key, BaseSpeakerCommandsRequest request, MessageCookie cookie)
            throws PipelineException {
        log.debug("Got Floodlight RuleManager request from hub bolt {}", request);
        keyToRequest.put(key, new RequestContext(null, request, cookie));
        carrier.sendFloodlightOfRequest(key, request);
    }

    /**
     * Sends command to GRPC speaker.
     * @param key unique operation's key.
     * @param command command to be executed.
     */
    public void sendGrpcCommand(String key, CommandData command, MessageCookie cookie) throws PipelineException {
        log.debug("Got GRPC request from hub bolt {}", command);
        keyToRequest.put(key, new RequestContext(command, null, cookie));
        carrier.sendGrpcCommand(key, new CommandMessage(command, key, cookie));
    }

    /**
     * Processes received response and forwards it to the hub component.
     * @param key operation's key.
     * @param response response payload.
     */
    public void handleResponse(String key, Message response)
            throws PipelineException {
        log.debug("Got a response from speaker {}", response);
        RequestContext pending = keyToRequest.remove(key);
        if (pending != null) {
            if (response.getCookie() == null) {
                response.setCookie(pending.getCookie());
            }
            carrier.sendResponse(key, response);
        }
    }

    /**
     * Processes received chunked responses, combines them and forwards it to the hub component.
     */
    public void handleChunkedResponse(String key, ChunkedInfoMessage response) throws PipelineException {
        log.debug("Got chunked response from speaker {}", response);
        chunkedMessageIdsPerRequest.computeIfAbsent(key, mappingFunction -> new HashSet<>());
        Set<String> associatedMessages = chunkedMessageIdsPerRequest.get(key);
        if (!associatedMessages.add(response.getMessageId())) {
            log.debug("Skipping chunked message, it is already received: {}", response);
            return;
        }

        messagesChains.computeIfAbsent(key, mappingFunction -> new ArrayList<>());
        List<ChunkedInfoMessage> chain = messagesChains.get(key);
        if (response.getTotalMessages() != 0) {
            chain.add(response);
        }

        if (chain.size() == response.getTotalMessages()) {
            completeChunkedResponse(key, chain);
        }
    }

    private void completeChunkedResponse(String key, List<ChunkedInfoMessage> messages) throws PipelineException {
        RequestContext pending = keyToRequest.remove(key);
        messagesChains.remove(key);
        chunkedMessageIdsPerRequest.remove(key);

        if (pending != null && pending.getPayload() != null) {
            if (pending.getPayload() instanceof DumpRulesForSwitchManagerRequest) {
                FlowDumpResponse entries = new FlowDumpResponse(
                        messages.stream()
                                .map(InfoMessage::getData)
                                .map(SingleFlowDumpResponse.class::cast)
                                .map(SingleFlowDumpResponse::getFlowSpeakerData)
                                .collect(Collectors.toList()));
                InfoMessage response = new InfoMessage(entries, key, pending.getCookie());
                carrier.sendResponse(key, response);
            } else {
                log.error("Unknown request payload for chunked response. Request contest: {}, key: {}, "
                        + "chunked data: {}", pending, key, messages);
            }
        }
    }

    /**
     * Processes received OF speaker response and forwards it to the hub component.
     */
    public void handleSpeakerResponse(String key, SpeakerResponse response) throws PipelineException {
        log.debug("Got a response from speaker {}", response);
        RequestContext pending = keyToRequest.remove(key);
        if (pending != null) {
            if (response.getMessageCookie() == null) {
                response.setMessageCookie(pending.getCookie());
            }
            carrier.sendSpeakerResponse(key, response);
        }
    }

    /**
     * Handles operation timeout.
     * @param key operation identifier.
     */
    public void handleTimeout(String key) throws PipelineException {
        log.debug("Send timeout error to hub {}", key);
        RequestContext pending = keyToRequest.remove(key);

        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT,
                String.format("Timeout for waiting response on %s", pending.getPayload()),
                "Error in SpeakerWorkerService");
        ErrorMessage errorMessage = new ErrorMessage(errorData, key, pending.getCookie());
        carrier.sendResponse(key, errorMessage);
    }

    @Value
    private static class RequestContext {
        CommandData payload;
        BaseSpeakerCommandsRequest request;
        MessageCookie cookie;
    }
}

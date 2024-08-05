/* Copyright 2024 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.api.response.ChunkedSpeakerDataResponse;
import org.openkilda.floodlight.api.response.SpeakerDataResponse;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.wfm.error.PipelineException;

import lombok.NonNull;
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
public class SpeakerWorkerForDumpsService {
    private final SpeakerCommandForDumpsCarrier carrier;
    private final Map<String, CommandMessage> keyToRequest = new HashMap<>();

    /**
     * The storage for received chunked message ids. It is needed to identify whether we have already received specific
     * chunked message or not in order to do not have duplicates, because current version of kafka do not guarantee
     * exactly once delivery.
     */
    private final Map<String, Set<String>> chunkedMessageIdsPerRequest = new HashMap<>();
    /**
     * Chains of chunked messages, it is filling by messages one by one as soon as the next linked message is received.
     */
    private final Map<String, List<ChunkedSpeakerDataResponse>> messagesChains;

    public SpeakerWorkerForDumpsService(@NonNull SpeakerCommandForDumpsCarrier carrier,
                                        int chunkedMessagesExpirationMinutes) {
        this.carrier = carrier;
        messagesChains = new PassiveExpiringMap<>(chunkedMessagesExpirationMinutes, TimeUnit.MINUTES, new HashMap<>());
    }

    /**
     * Sends command to speaker.
     *
     * @param key     unique operation's key.
     * @param request command to be executed.
     */
    public void sendCommand(@NonNull String key, @NonNull CommandMessage request) throws PipelineException {
        log.debug("Got a request from hub bolt {}", request);
        keyToRequest.put(key, request);
        carrier.sendCommand(key, request);
    }

    /**
     * Handles a timeout event by sending an error response to the hub.
     *
     * @param key the unique operation key associated with the timed-out request.
     * @throws PipelineException if there is an error while sending the error response.
     */
    public void handleTimeout(String key) throws PipelineException {
        log.debug("Send timeout error to hub {}", key);
        CommandMessage request = keyToRequest.remove(key);
        chunkedMessageIdsPerRequest.remove(key);
        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT,
                String.format("Timeout for waiting response on command %s", request),
                "Error in SpeakerWorker");
        carrier.sendResponse(key, errorData);
    }

    /**
     * Processes received response and forwards it to the hub component.
     *
     * @param key      operation's key.
     * @param response response payload.
     */
    public void handleResponse(@NonNull String key, @NonNull SpeakerDataResponse response)
            throws PipelineException {
        log.debug("Got a response from speaker {}", response);
        CommandMessage pendingRequest = keyToRequest.remove(key);
        if (pendingRequest != null) {
            carrier.sendResponse(key, response.getData());
        }
    }

    /**
     * Processes received chunked responses, combines them and forwards it to the hub component.
     */
    public void handleChunkedResponse(String key, ChunkedSpeakerDataResponse response) throws PipelineException {
        log.debug("Got chunked response from speaker {}", response);
        chunkedMessageIdsPerRequest.computeIfAbsent(key, mappingFunction -> new HashSet<>());
        Set<String> associatedMessages = chunkedMessageIdsPerRequest.get(key);
        if (!associatedMessages.add(response.getMessageId())) {
            log.debug("Skipping chunked message, it is already received: {}", response);
            return;
        }

        messagesChains.computeIfAbsent(key, mappingFunction -> new ArrayList<>());
        List<ChunkedSpeakerDataResponse> chain = messagesChains.get(key);
        if (response.getTotalMessages() != 0) {
            chain.add(response);
        }

        if (chain.size() == response.getTotalMessages()) {
            completeChunkedResponse(key, chain);
        }
    }

    private void completeChunkedResponse(String key, List<ChunkedSpeakerDataResponse> messages)
            throws PipelineException {

        CommandMessage pending = keyToRequest.remove(key);
        messagesChains.remove(key);
        chunkedMessageIdsPerRequest.remove(key);

        if (pending == null || pending.getData() == null) {
            return;
        }
        Object data = pending.getData();
        if (data instanceof DumpRulesForFlowHsRequest) {
            List<FlowDumpResponse> responses = process(messages, FlowDumpResponse.class);
            carrier.sendResponse(key, FlowDumpResponse.unite(responses));
        } else if (data instanceof DumpMetersForFlowHsRequest) {
            List<MeterDumpResponse> responses = process(messages, MeterDumpResponse.class);
            carrier.sendResponse(key, MeterDumpResponse.unite(responses));
        } else if (data instanceof DumpGroupsForFlowHsRequest) {
            List<GroupDumpResponse> responses = process(messages, GroupDumpResponse.class);
            carrier.sendResponse(key, GroupDumpResponse.unite(responses));
        } else {
            log.error("Unknown request payload for chunked response. Request contest: {}, key: {}, "
                    + "chunked data: {}", pending, key, messages);
        }
    }

    private <T> List<T> process(List<ChunkedSpeakerDataResponse> messages, Class<T> responseType) {
        return messages.stream()
                .map(SpeakerDataResponse::getData)
                .map(responseType::cast)
                .collect(Collectors.toList());
    }
}

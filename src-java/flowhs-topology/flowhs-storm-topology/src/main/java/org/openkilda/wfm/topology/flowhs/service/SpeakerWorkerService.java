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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.wfm.error.PipelineException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SpeakerWorkerService {
    private final SpeakerCommandCarrier carrier;
    private final Map<String, SpeakerRequest> keyToRequest = new HashMap<>();

    public SpeakerWorkerService(@NonNull SpeakerCommandCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * Sends command to speaker.
     * @param key unique operation's key.
     * @param command command to be executed.
     */
    public void sendCommand(@NonNull String key, @NonNull SpeakerRequest command) throws PipelineException {
        log.debug("Got a request from hub bolt {}", command);
        keyToRequest.put(key, command);
        carrier.sendCommand(key, command);
    }

    /**
     * Processes received response and forwards it to the hub component.
     * @param key operation's key.
     * @param response response payload.
     */
    public void handleResponse(@NonNull String key, @NonNull SpeakerResponse response)
            throws PipelineException {
        log.debug("Got a response from speaker {}", response);
        SpeakerRequest pendingRequest = keyToRequest.remove(key);
        if (pendingRequest != null) {
            if (pendingRequest.getCommandId().equals(response.getCommandId())) {
                carrier.sendResponse(key, response);
            } else {
                log.warn("Pending request's command id and received response's command id mismatch");
            }
        }
    }

    /**
     * Handles operation timeout.
     * @param key operation identifier.
     */
    public void handleTimeout(@NonNull String key) throws PipelineException {
        SpeakerRequest failedRequest = keyToRequest.remove(key);

        if (failedRequest instanceof FlowSegmentRequest) {
            FlowSegmentRequest flowSegmentRequest = (FlowSegmentRequest) failedRequest;
            SpeakerFlowSegmentResponse response = FlowErrorResponse.errorBuilder()
                    .commandId(flowSegmentRequest.getCommandId())
                    .switchId(flowSegmentRequest.getSwitchId())
                    .metadata(flowSegmentRequest.getMetadata())
                    .errorCode(ErrorCode.OPERATION_TIMED_OUT)
                    .messageContext(flowSegmentRequest.getMessageContext())
                    .build();
            carrier.sendResponse(key, response);
        } else if (failedRequest instanceof BaseSpeakerCommandsRequest) {
            BaseSpeakerCommandsRequest speakerCommandsRequest = (BaseSpeakerCommandsRequest) failedRequest;
            SpeakerCommandResponse response = SpeakerCommandResponse.builder()
                    .commandId(speakerCommandsRequest.getCommandId())
                    .switchId(speakerCommandsRequest.getSwitchId())
                    .messageContext(speakerCommandsRequest.getMessageContext())
                    .success(false)
                    .failedCommandIds(speakerCommandsRequest.getCommands().stream().map(command -> {
                        if (command instanceof FlowCommand) {
                            return ((FlowCommand) command).getData();
                        }
                        if (command instanceof MeterCommand) {
                            return ((MeterCommand) command).getData();
                        }
                        return ((GroupCommand) command).getData();
                    }).collect(Collectors.toMap(SpeakerData::getUuid, error -> "Operation is timed out")))
                    .build();
            carrier.sendResponse(key, response);
        }
    }
}

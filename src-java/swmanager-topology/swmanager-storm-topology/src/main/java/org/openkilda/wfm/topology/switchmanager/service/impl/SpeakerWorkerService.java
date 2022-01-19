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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerCommandCarrier;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SpeakerWorkerService {
    private final SpeakerCommandCarrier carrier;

    private final Map<String, RequestContext> keyToRequest = new HashMap<>();

    public SpeakerWorkerService(SpeakerCommandCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * Sends command to Floodlight speaker.
     * @param key unique operation's key.
     * @param command command to be executed.
     */
    public void sendFloodlightCommand(String key, CommandData command, MessageCookie cookie) throws PipelineException {
        log.debug("Got Floodlight request from hub bolt {}", command);
        keyToRequest.put(key, new RequestContext(command, cookie));
        carrier.sendFloodlightCommand(key, new CommandMessage(command, System.currentTimeMillis(), key));
    }

    /**
     * Sends command to GRPC speaker.
     * @param key unique operation's key.
     * @param command command to be executed.
     */
    public void sendGrpcCommand(String key, CommandData command, MessageCookie cookie) throws PipelineException {
        log.debug("Got GRPC request from hub bolt {}", command);
        keyToRequest.put(key, new RequestContext(command, cookie));
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
        MessageCookie cookie;
    }
}

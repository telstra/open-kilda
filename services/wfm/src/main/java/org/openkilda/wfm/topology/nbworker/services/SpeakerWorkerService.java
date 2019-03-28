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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.topology.nbworker.bolts.SpeakerWorkerCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SpeakerWorkerService {
    private final Map<String, CommandData> keyToRequest = new HashMap<>();

    /**
     * Send command to speaker.
     */
    public void sendCommand(String key, CommandData commandData, SpeakerWorkerCarrier carrier) {
        log.debug("Send a request with data: {}", commandData);
        keyToRequest.put(key, commandData);

        CommandMessage commandMessage = new CommandMessage(commandData, System.currentTimeMillis(), key);
        carrier.sendCommand(key, commandMessage);
    }

    /**
     * Process received response and forward it to the hub component.
     */
    public void handleResponse(String key, Message message, SpeakerWorkerCarrier carrier) {
        log.debug("Got a response from speaker {}", message);
        CommandData commandData = keyToRequest.remove(key);
        if (commandData != null) {
            carrier.sendResponse(key, message);
        }
    }

    /**
     * Handle operation timeout.
     */
    public void handleTimeout(String key, SpeakerWorkerCarrier carrier) {
        log.debug("Send timeout error to hub {}", key);
        keyToRequest.remove(key);

        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT, "Timeout for waiting response",
                "Error in SpeakerWorkerService");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        carrier.sendResponse(key, errorMessage);
    }
}

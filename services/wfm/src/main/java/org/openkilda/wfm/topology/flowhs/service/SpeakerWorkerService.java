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

package org.openkilda.wfm.topology.flowhs.service;

import static java.lang.String.format;

import org.openkilda.messaging.floodlight.FlowMessage;
import org.openkilda.messaging.floodlight.response.FlowResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.flowhs.model.FlowCommands;
import org.openkilda.wfm.topology.flowhs.model.FlowResponses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeakerWorkerService {
    private final Map<String, Map<SwitchId, FlowMessage>> commands = new HashMap<>();
    private final Map<String, List<FlowResponse>> responses = new HashMap<>();

    /**
     * Send batch of commands.
     * @param key unique identifier.
     * @param flowCommands list of commands to be sent.
     * @param commandSender helper for sending messages.
     */
    public void sendCommands(String key, FlowCommands flowCommands, SpeakerCommandCarrier commandSender)
            throws PipelineException {
        Map<SwitchId, FlowMessage> commandsPerSwitch = new HashMap<>(flowCommands.getCommands().size());
        for (FlowMessage command : flowCommands.getCommands()) {
            commandsPerSwitch.put(command.getSwitchId(), command);
            commandSender.sendCommand(command);
        }

        commands.put(key, commandsPerSwitch);
    }

    /**
     * Process received response. If it is the last in the batch - then send response to the hub.
     * @param key operation's key.
     * @param response response payload.
     * @param commandSender helper for sending messages.
     */
    public void handleResponse(String key, FlowResponse response, SpeakerCommandCarrier commandSender)
            throws PipelineException {
        if (!commands.containsKey(key)) {
            throw new IllegalStateException(format("Received response for non pending request. Payload: %s", response));
        }

        Map<SwitchId, FlowMessage> pendingRequests = commands.get(key);
        FlowMessage currentRequest = pendingRequests.remove(response.getSwitchId());
        if (currentRequest == null) {
            throw new IllegalStateException(format("Received response with wrong switch dpid. Payload: %s", response));
        }

        responses.computeIfAbsent(key, function -> new ArrayList<>())
                .add(response);

        if (pendingRequests.isEmpty()) {
            commandSender.sendResponse(new FlowResponses(responses.remove(key)));
        }
    }

    public void handleTimeout(String key) {
        commands.remove(key);
        responses.remove(key);
    }
}

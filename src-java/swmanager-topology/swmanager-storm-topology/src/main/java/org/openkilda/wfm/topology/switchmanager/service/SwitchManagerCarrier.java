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

package org.openkilda.wfm.topology.switchmanager.service;

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.Chunkable;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub.OfCommandAction;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;

import lombok.NonNull;

import java.util.List;

public interface SwitchManagerCarrier {
    MessageCookie newDispatchRoute(String requestKey);

    void sendCommandToSpeaker(String key, CommandData command);

    void sendCommandToSpeaker(CommandData command, @NonNull MessageCookie cookie);

    void runHeavyOperation(String key, SwitchId switchId);

    void runHeavyOperation(SwitchId switchId, @NonNull MessageCookie messageCookie);

    void sendOfCommandsToSpeaker(String key, List<OfCommand> commands, OfCommandAction action, SwitchId switchId);

    void sendOfCommandsToSpeaker(List<OfCommand> commands, OfCommandAction action, SwitchId switchId,
                                 @NonNull MessageCookie cookie);

    void response(String key, Message message);

    void response(String key, InfoData payload);

    void responseChunks(String key, Chunkable<? extends InfoData> payload);

    void errorResponse(String key, ErrorType error, String message, String description);

    void cancelTimeoutCallback(String key);

    void runSwitchSync(String key, SwitchValidateRequest request, ValidationResult validationResult);

    void sendInactive();
}

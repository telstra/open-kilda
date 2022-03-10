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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;

import lombok.NonNull;

public class SwitchManagerCarrierCookieDecorator implements SwitchManagerCarrier {
    private final SwitchManagerCarrier target;

    private final String dispatchRoute;

    public SwitchManagerCarrierCookieDecorator(SwitchManagerCarrier target, String dispatchRoute) {
        this.target = target;
        this.dispatchRoute = dispatchRoute;
    }

    @Override
    public MessageCookie newDispatchRoute(String requestKey) {
        return new MessageCookie(dispatchRoute, target.newDispatchRoute(requestKey));
    }

    @Override
    public void sendCommandToSpeaker(String key, CommandData command) {
        sendCommandToSpeaker(command, new MessageCookie(key));
    }

    @Override
    public void sendCommandToSpeaker(CommandData command, @NonNull MessageCookie cookie) {
        target.sendCommandToSpeaker(command, new MessageCookie(dispatchRoute, cookie));
    }

    @Override
    public void response(String key, Message message) {
        target.response(key, message);
    }

    @Override
    public void response(String key, InfoData payload) {
        target.response(key, payload);
    }

    @Override
    public void errorResponse(String key, ErrorType error, String message, String description) {
        target.errorResponse(key, error, message, description);
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        target.cancelTimeoutCallback(key);
    }

    @Override
    public void runSwitchSync(String key, SwitchValidateRequest request, ValidationResult validationResult) {
        target.runSwitchSync(key, request, validationResult);
    }

    @Override
    public void sendInactive() {
        target.sendInactive();
    }
}

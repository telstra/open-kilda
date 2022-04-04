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

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.error.MessageDispatchException;
import org.openkilda.wfm.error.UnexpectedInputException;

import lombok.NonNull;

public interface SwitchManagerHubService {
    void activate();

    boolean deactivate();

    boolean isAllOperationsCompleted();

    void timeout(@NonNull MessageCookie cookie) throws MessageDispatchException;

    void dispatchWorkerMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException;

    default void dispatchWorkerMessage(SpeakerResponse payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {}

    void dispatchErrorMessage(ErrorData payload, MessageCookie cookie) throws MessageDispatchException;

    default void dispatchHeavyOperationMessage(InfoData payload, MessageCookie cookie)
            throws UnexpectedInputException, MessageDispatchException {}

    SwitchManagerCarrier getCarrier();
}

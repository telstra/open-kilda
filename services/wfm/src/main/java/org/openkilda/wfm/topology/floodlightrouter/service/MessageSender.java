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

package org.openkilda.wfm.topology.floodlightrouter.service;

import org.openkilda.messaging.Message;
import org.openkilda.model.SwitchId;

public interface MessageSender {
    void emitSpeakerAliveRequest(String region);

    void emitSwitchUnmanagedNotification(SwitchId sw);

    void emitNetworkDumpRequest(String region);

    void emitSpeakerMessage(Message message, String region);

    void emitSpeakerMessage(String key, Message message, String region);

    void emitControllerMessage(Message message);

    void emitControllerMessage(String key, Message message);

    void emitRegionNotification(SwitchMapping mapping);
}

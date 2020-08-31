/* Copyright 2020 Telstra Open Source
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

public class ProxyMessageWrapper implements ProxyPayloadWrapper {
    private final Message message;

    public ProxyMessageWrapper(Message message) {
        this.message = message;
    }

    @Override
    public void sendToSpeaker(ControllerToSpeakerProxyCarrier carrier, String region) {
        carrier.sendToSpeaker(message, region);
    }

    @Override
    public void regionNotFound(ControllerToSpeakerProxyCarrier carrier, SwitchId switchId) {
        carrier.regionNotFoundError(message, switchId);
    }
}

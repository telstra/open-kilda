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

package org.openkilda.wfm.topology.network.service;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;

import java.time.Instant;

public interface IPortCarrier {
    void setupUniIslHandler(Endpoint endpoint, Isl history);

    void enableDiscoveryPoll(Endpoint endpoint);

    void disableDiscoveryPoll(Endpoint endpoint);

    void notifyPortDiscovered(Endpoint endpoint, IslInfoData speakerDiscoveryEvent);

    void notifyPortDiscoveryFailed(Endpoint endpoint);

    void notifyPortPhysicalDown(Endpoint endpoint);

    void sendPortStateChangedHistory(Endpoint endpoint, PortHistoryEvent event, Instant time);

    void removeUniIslHandler(Endpoint endpoint);
}

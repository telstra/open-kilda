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

package org.openkilda.wfm.topology.flowhs.service.common;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.ChunkedInfoMessage;

import java.util.List;

/**
 * Defines a base carrier for processing Northbound requests.
 */
public interface NorthboundResponseCarrier {
    /**
     * Sends response to northbound component.
     */
    void sendNorthboundResponse(Message message);

    /**
     * Sends responses to northbound component.
     */
    default void sendNorthboundResponses(List<? extends ChunkedInfoMessage> messages) {
        for (ChunkedInfoMessage message : messages) {
            sendNorthboundResponse(message);
        }
    }
}

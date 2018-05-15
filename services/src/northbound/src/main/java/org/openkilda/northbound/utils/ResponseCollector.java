/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.utils;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.response.ChunkedInfoMessage;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResponseCollector<T extends InfoData> {

    @Autowired
    private MessageConsumer<ChunkedInfoMessage> messageConsumer;

    public ResponseCollector() {
    }

    public List<T> getResult(String requestId) {
        List<T> result = null;
        ChunkedInfoMessage message;
        String nextRequest = requestId;
        do {
            message = messageConsumer.poll(nextRequest);
            nextRequest = message.getNextRequestId();
            if (result == null) {
                result = new ArrayList<>(message.getTotalItems());
            }

            T response = (T) message.getData();
            if (response != null) {
                result.add(response);
            }
        } while (result.size() < message.getTotalItems());

        return result;
    }

}

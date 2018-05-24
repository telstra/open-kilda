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

import org.apache.commons.lang3.StringUtils;
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

    /**
     * Receives chunked responses. This method expects to get messages one by one,
     * associated one with the following by nextRequestId.
     * If nextRequestId is null it means this message is the last one in the list.
     * @param requestId correlationId of the requst.
     * @return List of messages.
     */
    public List<T> getResult(String requestId) {
        List<T> result = new ArrayList<>();
        ChunkedInfoMessage message;
        String nextRequest = requestId;
        do {
            message = messageConsumer.poll(nextRequest);
            nextRequest = message.getNextRequestId();

            T response = (T) message.getData();
            if (response != null) {
                result.add(response);
            }
        } while (StringUtils.isNoneBlank(message.getNextRequestId()));

        return result;
    }

}

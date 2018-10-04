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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.AbstractTopology;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ResponseSplitterBolt extends AbstractBolt {
    @Override
    protected void handleInput(Tuple input) {
        List<InfoData> responses = (List<InfoData>) input.getValueByField("response");
        String correlationId = input.getStringByField("correlationId");
        log.debug("Received response correlationId {}", correlationId);

        sendChunkedResponse(responses, input, correlationId);
    }

    private void sendChunkedResponse(List<InfoData> responses, Tuple input, String requestId) {
        List<Message> messages = new ArrayList<>();
        if (CollectionUtils.isEmpty(responses)) {
            log.debug("No records found in the database");
            Message message = new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId, null);
            messages.add(message);
        } else {
            String currentRequestId = requestId;
            String nextRequestId;
            Iterator<InfoData> iterator = responses.iterator();
            while (iterator.hasNext()) {
                InfoData infoData = iterator.next();
                // generate new request id for the next request if the list contains more elements.
                if (iterator.hasNext()) {
                    nextRequestId = UUID.randomUUID().toString();
                } else {
                    nextRequestId = null;
                }

                Message message = new ChunkedInfoMessage(infoData, System.currentTimeMillis(), currentRequestId,
                        nextRequestId);
                messages.add(message);
                currentRequestId = nextRequestId;
            }

            log.debug("Response is divided into {} messages", messages.size());
        }

        // emit all found messages
        for (Message message : messages) {
            try {
                getOutput().emit(input, new Values(Utils.MAPPER.writeValueAsString(message)));
            } catch (JsonProcessingException e) {
                log.error("Error during writing response as json", e);
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}

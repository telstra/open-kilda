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

import static org.openkilda.wfm.topology.AbstractTopology.KEY_FIELD;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.AbstractBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ResponseSplitterBolt extends AbstractBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseSplitterBolt.class);

    @Override
    protected void handleInput(Tuple input) {
        List<InfoData> responses = (List<InfoData>) input.getValueByField("response");
        String correlationId = input.getStringByField("correlationId");
        LOGGER.debug("Received response correlationId {}", correlationId);

        sendChunkedResponse(responses, input, correlationId);
    }

    private void sendChunkedResponse(List<InfoData> responses, Tuple input, String requestId) {
        List<Message> messages = new ArrayList<>();
        if (CollectionUtils.isEmpty(responses)) {
            LOGGER.debug("No records found in the database");
            Message message = new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId, requestId,
                    responses.size());
            messages.add(message);
        } else {
            for (int i = 0; i < responses.size(); i++) {
                Message message = new ChunkedInfoMessage(responses.get(i), System.currentTimeMillis(), requestId,
                        i, responses.size());
                messages.add(message);
            }

            LOGGER.debug("Response is divided into {} messages", messages.size());
        }

        // emit all found messages
        for (Message message : messages) {
            try {
                getOutput().emit(input, new Values(requestId, Utils.MAPPER.writeValueAsString(message)));
            } catch (JsonProcessingException e) {
                LOGGER.error("Error during writing response as json", e);
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(KEY_FIELD, MESSAGE_FIELD));
    }
}

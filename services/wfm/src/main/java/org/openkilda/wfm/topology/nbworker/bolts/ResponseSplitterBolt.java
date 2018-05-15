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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.nbtopology.response.ChunkedInfoMessage;
import org.openkilda.wfm.topology.AbstractTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResponseSplitterBolt extends BaseRichBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseSplitterBolt.class);

    private OutputCollector collector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        try {
            List<InfoData> responses = (List<InfoData>) input.getValue(0);
            String correlationId = input.getString(1);
            LOGGER.debug("Received response correlationId {}", correlationId);

            sendChunkedResponse(responses, input, correlationId);
        } catch (Exception e) {
            LOGGER.error("Unexpected error during response processing", e);
        } finally {
            collector.ack(input);
        }
    }

    private void sendChunkedResponse(List<InfoData> responses, Tuple input, String requestId) {
        List<Message> messages = new ArrayList<>();
        if (responses.isEmpty()) {
            LOGGER.debug("No records found. CorrelationId: {}", requestId);
            Message message = new ChunkedInfoMessage(null, System.currentTimeMillis(), requestId,
                    responses.size(), null);
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
                        responses.size(), nextRequestId);
                messages.add(message);

                currentRequestId = nextRequestId;
            }

            LOGGER.debug("Response chunked into {} items. CorrelationId", messages.size(), requestId);
        }

        // emit all found messages
        for (Message message : messages) {
            try {
                collector.emit(input, new Values(Utils.MAPPER.writeValueAsString(message)));
            } catch (JsonProcessingException e) {
                LOGGER.error("Error during writing response as json", e);
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}

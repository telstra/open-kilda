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

import static org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper.BOLT_KEY;
import static org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE;

import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;

public class ResponseSplitterBolt extends AbstractBolt {
    public static final String FIELD_ID_RESPONSE = "response";

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        @SuppressWarnings("unchecked")
        List<InfoData> responses = pullValue(input, FIELD_ID_RESPONSE, List.class);
        log.debug("Received response correlationId {}", getCommandContext().getCorrelationId());

        sendChunkedResponse(responses, input, getCommandContext().getCorrelationId());
    }

    private void sendChunkedResponse(List<InfoData> responses, Tuple input, String requestId) {
        List<ChunkedInfoMessage> messages = ChunkedInfoMessage.createChunkedList(responses, requestId);
        log.debug("Response is divided into {} messages", messages.size());

        // emit all found messages
        messages.forEach(message ->
                getOutput().emit(input, new Values(requestId, message)));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(BOLT_KEY, BOLT_MESSAGE));
    }
}

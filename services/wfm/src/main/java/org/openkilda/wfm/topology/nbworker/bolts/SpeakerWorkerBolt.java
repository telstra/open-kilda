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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerBolt extends WorkerBolt {
    public static final String ID = "speaker.worker.bolt";
    public static final String INCOME_STREAM = "speaker.worker.stream";

    public SpeakerWorkerBolt(Config config) {
        super(config);
    }

    @Override
    protected void onHubRequest(Tuple input) throws Exception {
        String key = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        CommandData commandData = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, CommandData.class);
        log.debug("Send a request {} to Speaker", commandData);
        CommandMessage commandMessage = new CommandMessage(commandData, System.currentTimeMillis(), key);
        emitWithContext(StreamType.TO_SPEAKER.name(), input, new Values(key, commandMessage));
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws Exception {
        String key = response.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        Message message = pullValue(response, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Message.class);
        log.debug("Got a response from speaker {}", message);
        emitResponseToHub(response, new Values(key, message, pullContext(response)));
    }

    @Override
    public void onRequestTimeout(Tuple request) throws PipelineException {
        String key = pullKey(request);
        CommandData commandData = pullValue(request, MessageKafkaTranslator.FIELD_ID_PAYLOAD, CommandData.class);

        log.debug("Send timeout error to hub {}", key);

        ErrorData errorData = new ErrorData(ErrorType.OPERATION_TIMED_OUT,
                String.format("Timeout for waiting response on command %s", commandData),
                "Error in SpeakerWorker");
        ErrorMessage errorMessage = new ErrorMessage(errorData, System.currentTimeMillis(), key);
        emitResponseToHub(request, new Values(key, errorMessage, pullContext(request)));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(StreamType.TO_SPEAKER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }
}

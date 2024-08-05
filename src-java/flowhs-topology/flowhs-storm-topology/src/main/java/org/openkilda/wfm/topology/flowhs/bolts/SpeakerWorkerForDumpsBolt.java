/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_REQUEST_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.floodlight.api.response.ChunkedSpeakerDataResponse;
import org.openkilda.floodlight.api.response.SpeakerDataResponse;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandForDumpsCarrier;
import org.openkilda.wfm.topology.flowhs.service.SpeakerWorkerForDumpsService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import com.google.common.base.Preconditions;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerForDumpsBolt extends WorkerBolt implements SpeakerCommandForDumpsCarrier {

    private transient SpeakerWorkerForDumpsService service;
    private final int chunkedMessagesExpirationMinutes;

    public SpeakerWorkerForDumpsBolt(Config config, int chunkedMessagesExpirationMinutes) {
        super(config);
        this.chunkedMessagesExpirationMinutes = chunkedMessagesExpirationMinutes;
    }

    @Override
    protected void init() {
        super.init();
        service = new SpeakerWorkerForDumpsService(this, chunkedMessagesExpirationMinutes);
    }

    @Override
    protected void onHubRequest(Tuple requestTuple) throws Exception {
        String key = pullKey();
        CommandMessage request = pullValue(requestTuple, FIELD_ID_PAYLOAD, CommandMessage.class);

        // Due to specific request handling in FL, we have to provide the key and correlationId which are equal.
        Preconditions.checkArgument(key.equals(request.getCorrelationId()),
                "Tuple %s has the key which doesn't correspond to correlationId", requestTuple);
        service.sendCommand(key, request);
    }

    @Override
    protected void onAsyncResponse(Tuple requestTuple, Tuple responseTuple) throws Exception {
        String key = pullKey();
        Object payload = responseTuple.getValueByField(FIELD_ID_PAYLOAD);
        if (payload instanceof ChunkedSpeakerDataResponse) {
            ChunkedSpeakerDataResponse chunkedInfoMessage = (ChunkedSpeakerDataResponse) payload;
            service.handleChunkedResponse(key, chunkedInfoMessage);
        } else if (payload instanceof SpeakerDataResponse) {
            SpeakerDataResponse dataResponse = (SpeakerDataResponse) payload;
            service.handleResponse(key, dataResponse);
        } else {
            log.debug("Unknown response received: {}", payload);
        }
    }

    @Override
    public void onRequestTimeout(Tuple requestTuple) throws PipelineException {
        String key = pullKey();
        service.handleTimeout(key);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(SPEAKER_WORKER_REQUEST_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
    }

    @Override
    public void sendCommand(String key, CommandMessage command) {
        emitWithContext(SPEAKER_WORKER_REQUEST_SENDER.name(), getCurrentTuple(), new Values(key, command));
    }

    @Override
    public void sendResponse(String key, MessageData response) throws PipelineException {
        emitResponseToHub(getCurrentTuple(), new Values(key, response, getCommandContext()));
    }
}

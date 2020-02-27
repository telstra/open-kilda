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

package org.openkilda.wfm.topology.switchmanager.bolt;

import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerCommandCarrier;
import org.openkilda.wfm.topology.switchmanager.service.impl.SpeakerWorkerService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerBolt extends WorkerBolt implements SpeakerCommandCarrier {

    public static final String ID = "speaker.worker.bolt";
    public static final String INCOME_STREAM = "speaker.worker.stream";

    public static String STREAM_SPEAKER_REQUEST_ID = "speaker-request";
    public static Fields STREAM_SPEAKER_REQUEST_FIELDS = MessageKafkaTranslator.STREAM_FIELDS;

    private transient SpeakerWorkerService service;

    public SpeakerWorkerBolt(Config config) {
        super(config);
    }

    @Override
    protected void init() {
        super.init();
        service = new SpeakerWorkerService(this);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        Object payload = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Object.class);

        if (payload instanceof CommandData) {
            service.sendCommand(key, (CommandData) payload);
        } else if (payload instanceof SpeakerRequest) {
            service.sendCommand(key, (SpeakerRequest) payload);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws Exception {
        String key = pullKey();
        Object payload = pullValue(response, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Object.class);
        if (payload instanceof Message) {
            service.handleResponse(key, (Message) payload);
        } else if (payload instanceof SpeakerResponse) {
            service.handleResponse(key, (SpeakerResponse) payload);
        } else {
            // all "foreign" messages/events are filtered by key mismatch inside WorkerBolt
            unhandledInput(response);
        }
    }

    @Override
    protected void onRequestTimeout(Tuple request) throws PipelineException {
        service.handleTimeout(pullKey(request));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(StreamType.TO_FLOODLIGHT.toString(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(STREAM_SPEAKER_REQUEST_ID, STREAM_SPEAKER_REQUEST_FIELDS);
    }

    @Override
    public void sendCommand(String key, CommandMessage command) {
        emit(StreamType.TO_FLOODLIGHT.toString(), getCurrentTuple(), makeKafkaTuple(key, command));
    }

    @Override
    public void sendCommand(String key, SpeakerRequest request) {
        emit(STREAM_SPEAKER_REQUEST_ID, getCurrentTuple(), makeKafkaTuple(key, request));
    }

    @Override
    public void sendResponse(String key, Message response) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, response));
    }

    @Override
    public void sendResponse(String key, SpeakerResponse response) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, response));
    }

    private Values makeHubTuple(String key, Object payload) {
        return new Values(key, payload, getCommandContext());
    }

    private Values makeKafkaTuple(String key, Object payload) {
        return new Values(key, payload, getCommandContext());
    }
}

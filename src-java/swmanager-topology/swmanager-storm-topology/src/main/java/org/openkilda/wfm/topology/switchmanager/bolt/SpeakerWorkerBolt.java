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
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.grpc.GrpcBaseRequest;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.switchmanager.StreamType;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerCommandCarrier;
import org.openkilda.wfm.topology.switchmanager.service.impl.SpeakerWorkerService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerBolt extends WorkerBolt implements SpeakerCommandCarrier {

    public static final String ID = "speaker.worker.bolt";
    public static final String INCOME_STREAM = "speaker.worker.stream";
    public static final String OF_COMMANDS_INCOME_STREAM = "speaker.worker.of.commands.stream";

    public static final String FIELD_ID_COOKIE = "cookie";

    private transient SpeakerWorkerService service;
    private final int chunkedMessagesExpirationMinutes;

    public SpeakerWorkerBolt(Config config, int chunkedMessagesExpirationMinutes) {
        super(config);
        this.chunkedMessagesExpirationMinutes = chunkedMessagesExpirationMinutes;
    }

    @Override
    protected void init() {
        super.init();
        service = new SpeakerWorkerService(this, chunkedMessagesExpirationMinutes);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        String key = input.getStringByField(MessageKafkaTranslator.FIELD_ID_KEY);
        MessageCookie cookie = pullValue(input, FIELD_ID_COOKIE, MessageCookie.class);
        if (INCOME_STREAM.equals(input.getSourceStreamId())) {
            CommandData command = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD, CommandData.class);

            if (command instanceof GrpcBaseRequest) {
                service.sendGrpcCommand(key, command, cookie);
            } else {
                service.sendFloodlightCommand(key, command, cookie);
            }
        } else if (OF_COMMANDS_INCOME_STREAM.equals(input.getSourceStreamId())) {
            BaseSpeakerCommandsRequest request = pullValue(input, MessageKafkaTranslator.FIELD_ID_PAYLOAD,
                    BaseSpeakerCommandsRequest.class);
            service.sendFloodlightOfRequest(key, request, cookie);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws Exception {
        String key = pullKey();
        Object payload = pullValue(response, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Object.class);
        if (payload instanceof ChunkedInfoMessage) {
            ChunkedInfoMessage chunkedInfoMessage = (ChunkedInfoMessage) payload;
            service.handleChunkedResponse(key, chunkedInfoMessage);
        } else if (payload instanceof Message) {
            Message message = (Message) payload;
            service.handleResponse(key, message);
        } else if (payload instanceof SpeakerResponse) {
            SpeakerResponse speakerResponse = (SpeakerResponse) payload;
            service.handleSpeakerResponse(key, speakerResponse);
        } else {
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
        declarer.declareStream(StreamType.REQUEST_TO_FLOODLIGHT.toString(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(StreamType.TO_GRPC.toString(), MessageKafkaTranslator.STREAM_FIELDS);
    }

    @Override
    public void sendFloodlightCommand(String key, CommandMessage command) {
        emitWithContext(StreamType.TO_FLOODLIGHT.toString(), getCurrentTuple(), new Values(key, command));
    }

    @Override
    public void sendFloodlightOfRequest(String key, SpeakerRequest request) {
        emitWithContext(StreamType.REQUEST_TO_FLOODLIGHT.toString(), getCurrentTuple(), new Values(key, request));
    }

    @Override
    public void sendGrpcCommand(String key, CommandMessage command) {
        emitWithContext(StreamType.TO_GRPC.toString(), getCurrentTuple(), new Values(key, command));
    }

    @Override
    public void sendResponse(String key, Message response) {
        Values values = new Values(key, response, getCommandContext());
        emitResponseToHub(getCurrentTuple(), values);
    }

    @Override
    public void sendSpeakerResponse(String key, SpeakerResponse response) {
        Values values = new Values(key, response, getCommandContext());
        emitResponseToHub(getCurrentTuple(), values);
    }
}

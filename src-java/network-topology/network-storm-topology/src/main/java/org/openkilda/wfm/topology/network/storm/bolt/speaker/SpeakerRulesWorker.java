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

package org.openkilda.wfm.topology.network.storm.bolt.speaker;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.network.storm.ICommand;
import org.openkilda.wfm.topology.network.storm.bolt.SpeakerRulesEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.isl.IslHandler;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslRulesFailedCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslRulesResponseCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerRulesWorkerCommand;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SpeakerRulesWorker extends WorkerBolt {
    public static final String BOLT_ID = WorkerBolt.ID + ".speaker.rules";

    public static final String FIELD_ID_PAYLOAD = SpeakerRulesEncoder.FIELD_ID_PAYLOAD;
    public static final String FIELD_ID_KEY = SpeakerRulesEncoder.FIELD_ID_KEY;

    public static final String STREAM_HUB_ID = "hub";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public SpeakerRulesWorker(Config config) {
        super(config);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        handleCommand(input, IslHandler.FIELD_ID_COMMAND);
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws PipelineException {
        handleCommand(response, SpeakerRouter.FIELD_ID_INPUT);
    }

    @Override
    public void onRequestTimeout(Tuple request) {
        try {
            handleTimeout(request, IslHandler.FIELD_ID_COMMAND);
        } catch (PipelineException e) {
            log.error("Unable to unpack original tuple in timeout processing - {}", e.getMessage());
        }
    }

    // -- commands processing --

    /**
     * Process request to speaker.
     */
    public void processIslRulesRequest(String key, BaseSpeakerCommandsRequest request) {
        emitSpeakerRequest(key, request);
    }

    public void processIslRulesResponse(String key, SpeakerCommandResponse response) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, new IslRulesResponseCommand(response)));
    }

    public void timeoutIslRuleRequest(String key, BaseSpeakerCommandsRequest request) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, new IslRulesFailedCommand(request.getCommandId())));
    }

    // -- setup --

    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);  // it will define HUB stream
        streamManager.declare(STREAM_FIELDS);
    }

    // -- private/service methods --

    private void handleCommand(Tuple input, String field) throws PipelineException {
        SpeakerRulesWorkerCommand command = pullValue(input, field, SpeakerRulesWorkerCommand.class);
        command.apply(this);
    }

    private void handleTimeout(Tuple input, String field) throws PipelineException {
        SpeakerRulesWorkerCommand command = pullValue(input, field, SpeakerRulesWorkerCommand.class);
        command.timeout(this);
    }

    public void emitSpeakerRequest(String key, BaseSpeakerCommandsRequest payload) {
        emit(getCurrentTuple(), makeSpeakerTuple(key, payload));
    }

    private Values makeSpeakerTuple(String key, BaseSpeakerCommandsRequest payload) {
        return new Values(key, payload, getCommandContext());
    }

    private Values makeHubTuple(String key, ICommand<IslHandler> islCommand) {
        return new Values(key, islCommand, getCommandContext());
    }
}

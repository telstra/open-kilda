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

package org.openkilda.wfm.topology.discovery.storm.bolt.speaker;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.floodlight.request.RemoveBfdSession;
import org.openkilda.messaging.floodlight.request.SetupBfdSession;
import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.BfdPortHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.speaker.command.SpeakerWorkerCommand;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SpeakerWorker extends WorkerBolt {
    public static final String BOLT_ID = WorkerBolt.ID + ".speaker";

    public static final String STREAM_HUB_ID = "hub";

    public static final Fields STREAM_FIELDS = new Fields();

    public SpeakerWorker(Config config) {
        super(config);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        handleCommand(input, BfdPortHandler.FIELD_ID_COMMAND);
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws PipelineException {
        handleCommand(input, SpeakerRouter.FIELD_ID_INPUT);
    }

    @Override
    public void onTimeout(String key) {
        // TODO
    }

    // -- HUB --> speaker --
    public void setupBfdSession(String key, NoviBfdSession bfdSession) {
        SetupBfdSession payload = new SetupBfdSession(bfdSession);
        speakerRequest(key, payload);
    }

    public void removeBfdSession(String key, NoviBfdSession bfdSession) {
        RemoveBfdSession payload = new RemoveBfdSession(bfdSession);
        speakerRequest(key, payload);
    }

    // -- speaker --> HUB --

    public void bfdSessionResponse(String key, BfdSessionResponse response) {
        // TODO
    }

    // -- private/service methods --

    private void handleCommand(Tuple input, String field) throws PipelineException {
        SpeakerWorkerCommand command = pullValue(input, field, SpeakerWorkerCommand.class);
        command.apply(this);
    }

    // -- setup --

    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);  // it will define HUB stream
        streamManager.declare(STREAM_FIELDS);
    }

    public void speakerRequest(String key, CommandData payload) {
        emit(getCurrentTuple(), makeSpeakerTuple(key, payload));
    }

    private Values makeSpeakerTuple(String key, CommandData payload) {
        return new Values(key, payload, getCommandContext());
    }
}

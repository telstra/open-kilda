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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.floodlight.request.RemoveBfdSession;
import org.openkilda.messaging.floodlight.request.SetupBfdSession;
import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.network.storm.bolt.SpeakerEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.BfdPortHandler;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortSpeakerBfdSessionResponseCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.command.BfdPortSpeakerTimeoutCommand;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.SpeakerWorkerCommand;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SpeakerWorker extends WorkerBolt {
    public static final String BOLT_ID = WorkerBolt.ID + ".speaker";

    public static final String FIELD_ID_PAYLOAD = SpeakerEncoder.FIELD_ID_PAYLOAD;
    public static final String FIELD_ID_KEY = SpeakerEncoder.FIELD_ID_KEY;

    public static final String STREAM_HUB_ID = "hub";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public SpeakerWorker(Config config) {
        super(config);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        // At this moment only one bolt(BfdPortHandler) can write into this worker so can rely on routing performed
        // in our superclass. Once this situation changed we will need to make our own request routing or extend
        // routing in superclass.
        handleCommand(input, BfdPortHandler.FIELD_ID_COMMAND);
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws PipelineException {
        handleCommand(input, SpeakerRouter.FIELD_ID_INPUT);
    }

    @Override
    public void onTimeout(String key) {
        Tuple request = pendingTasks.get(key);

        try {
            handleTimeout(request, BfdPortHandler.FIELD_ID_COMMAND);
        } catch (PipelineException e) {
            log.error("Unable to unpack original tuple in timeout processing - {}", e.getMessage());
        }
    }

    // -- commands processing --

    public void processBfdSetupRequest(String key, NoviBfdSession bfdSession) {
        SetupBfdSession payload = new SetupBfdSession(bfdSession);
        emitSpeakerRequest(key, payload);
    }

    public void processBfdRemoveRequest(String key, NoviBfdSession bfdSession) {
        RemoveBfdSession payload = new RemoveBfdSession(bfdSession);
        emitSpeakerRequest(key, payload);
    }

    public void processBfdSessionResponse(String key, BfdSessionResponse response) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(
                key, new BfdPortSpeakerBfdSessionResponseCommand(key, response)));
    }

    public void timeoutBfdRequest(String key, NoviBfdSession bfdSession) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, new BfdPortSpeakerTimeoutCommand(key, bfdSession)));
    }

    // -- setup --

    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);  // it will define HUB stream
        streamManager.declare(STREAM_FIELDS);
    }

    // -- private/service methods --

    private void handleCommand(Tuple input, String field) throws PipelineException {
        SpeakerWorkerCommand command = pullValue(input, field, SpeakerWorkerCommand.class);
        command.apply(this);
    }

    private void handleTimeout(Tuple input, String field) throws PipelineException {
        SpeakerWorkerCommand command = pullValue(input, field, SpeakerWorkerCommand.class);
        command.timeout(this);
    }

    public void emitSpeakerRequest(String key, CommandData payload) {
        emit(getCurrentTuple(), makeSpeakerTuple(key, payload));
    }

    private Values makeSpeakerTuple(String key, CommandData payload) {
        return new Values(key, payload, getCommandContext());
    }

    private Values makeHubTuple(String key, BfdPortCommand command) {
        return new Values(key, command, getCommandContext());
    }
}

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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_REQUEST_SENDER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_KEY;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandCarrier;
import org.openkilda.wfm.topology.flowhs.service.SpeakerWorkerService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerBolt extends WorkerBolt implements SpeakerCommandCarrier {

    public static final String ID = "speaker.worker.bolt";
    private transient SpeakerWorkerService service;

    private Tuple currentTuple;

    public SpeakerWorkerBolt(Config config) {
        super(config);
    }

    @Override
    protected void init() {
        service = new SpeakerWorkerService(this);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        this.currentTuple = input;

        String key = input.getStringByField(FIELD_ID_KEY);
        SpeakerFlowRequest command = (SpeakerFlowRequest) input.getValueByField(FIELD_ID_PAYLOAD);

        service.sendCommand(key, command);
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws PipelineException {
        this.currentTuple = input;

        String key = input.getStringByField(FIELD_ID_KEY);
        FlowResponse message = (FlowResponse) input.getValueByField(FIELD_ID_PAYLOAD);

        service.handleResponse(key, message);
    }

    @Override
    public void onTimeout(String key, Tuple tuple) throws PipelineException {
        this.currentTuple = tuple;

        service.handleTimeout(key);
    }

    @Override
    protected void unhandledInput(Tuple input) {
        if (log.isDebugEnabled()) {
            log.trace("Received a response from {} for non-pending task {}: {}", input.getSourceComponent(),
                    input.getStringByField(FIELD_ID_KEY), input.getValueByField(FIELD_ID_PAYLOAD));
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(SPEAKER_WORKER_REQUEST_SENDER.name(), MessageTranslator.STREAM_FIELDS);
    }

    @Override
    public void sendCommand(String key, SpeakerFlowRequest command) {
        emitWithContext(SPEAKER_WORKER_REQUEST_SENDER.name(), currentTuple, new Values(key, command));
    }

    @Override
    public void sendResponse(String key, FlowResponse response) {
        Values values = new Values(key, response, getCommandContext());
        emitResponseToHub(currentTuple, values);
    }
}

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
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;
import static org.openkilda.wfm.topology.utils.MessageTranslator.KEY_FIELD;

import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.flowhs.model.FlowCommands;
import org.openkilda.wfm.topology.flowhs.model.FlowResponses;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandCarrier;
import org.openkilda.wfm.topology.flowhs.service.SpeakerWorkerService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SpeakerWorkerBolt extends WorkerBolt {

    public static final String ID = "speaker.worker.bolt";
    private transient SpeakerWorkerService service;

    public SpeakerWorkerBolt(Config config) {
        super(config);
    }

    @Override
    protected void init() {
        service = new SpeakerWorkerService();
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        String key = input.getStringByField(KEY_FIELD);
        FlowCommands commands = (FlowCommands) input.getValueByField(FIELD_ID_PAYLOAD);

        service.sendCommands(key, commands, new WorkerCommandCarrier(input));

        //todo: should be removed once FL request processing is fixed
        for (FlowRequest request : commands.getCommands()) {
            service.handleResponse(key, new FlowResponse(request, true), new WorkerCommandCarrier(input));
        }
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws PipelineException {
        String key = input.getStringByField(KEY_FIELD);
        FlowResponse message = (FlowResponse) input.getValueByField(FIELD_ID_PAYLOAD);

        service.handleResponse(key, message, new WorkerCommandCarrier(input));
    }

    @Override
    public void onTimeout(String key) {
        service.handleTimeout(key);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(SPEAKER_WORKER_REQUEST_SENDER.name(), MessageTranslator.STREAM_FIELDS);
    }

    private class WorkerCommandCarrier implements SpeakerCommandCarrier {
        private final Tuple tuple;

        WorkerCommandCarrier(Tuple tuple) {
            this.tuple = tuple;
        }

        @Override
        public void sendCommand(FlowRequest command) throws PipelineException {
            emit(SPEAKER_WORKER_REQUEST_SENDER.name(), tuple, new Values(tuple.getStringByField(KEY_FIELD), command));
        }

        @Override
        public void sendResponse(FlowResponses responses) throws PipelineException {
            Values values = new Values(tuple.getStringByField(KEY_FIELD), responses, pullContext(tuple));
            emitResponseToHub(tuple, values);
        }
    }
}

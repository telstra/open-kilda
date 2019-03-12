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
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.openkilda.wfm.topology.nbworker.services.SpeakerWorkerService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

public class SpeakerWorkerBolt extends WorkerBolt {
    public static final String ID = "speaker.worker.bolt";
    public static final String INCOME_STREAM = "speaker.worker.stream";
    private transient SpeakerWorkerService service;

    public SpeakerWorkerBolt(Config config) {
        super(config);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        service = new SpeakerWorkerService();
    }

    @Override
    protected void onHubRequest(Tuple input) throws Exception {
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        SwitchId switchId = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, SwitchId.class);
        service.sendCommand(key, switchId, new SpeakerWorkerCarrierImpl(input));
    }

    @Override
    protected void onAsyncResponse(Tuple input) throws Exception {
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        Message message = pullValue(input, MessageTranslator.FIELD_ID_PAYLOAD, Message.class);
        service.handleResponse(key, message, new SpeakerWorkerCarrierImpl(input));
    }

    @Override
    public void onTimeout(String key) {
        Tuple request = pendingTasks.get(key);
        service.handleTimeout(key, new SpeakerWorkerCarrierImpl(request));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(StreamType.TO_SPEAKER.name(), MessageTranslator.STREAM_FIELDS);
    }

    private class SpeakerWorkerCarrierImpl implements SpeakerWorkerCarrier {
        private final Tuple tuple;

        SpeakerWorkerCarrierImpl(Tuple tuple) {
            this.tuple = tuple;
        }

        @Override
        public void sendCommand(String key, CommandMessage command) {
            emitWithContext(StreamType.TO_SPEAKER.name(), tuple, new Values(key, command));
        }

        @Override
        public void sendResponse(String key, Message message) {
            try {
                emitResponseToHub(tuple, new Values(key, message, pullContext(tuple)));
            } catch (PipelineException e) {
                log.error("Key: {}, Unable to send message to hub - {}", key, e.getMessage());
            }
        }
    }
}

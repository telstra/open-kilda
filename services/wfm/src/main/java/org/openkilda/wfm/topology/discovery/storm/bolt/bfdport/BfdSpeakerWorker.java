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

package org.openkilda.wfm.topology.discovery.storm.bolt.bfdport;

import org.openkilda.messaging.HeartBeat;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.AbstractOutputAdapter;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.discovery.service.DiscoveryBfdPortSpeakerWorkerService;
import org.openkilda.wfm.topology.discovery.service.IBfdSpeakerWorkerCarrier;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.command.BfdSpeakerWorkerCommand;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class BfdSpeakerWorker extends WorkerBolt {
    public static final String BOLD_ID = WorkerBolt.ID + ".bfd.speaker";

    public static final String STREAM_HUB_ID = "hub";

    public static final Fields STREAM_FIELDS = new Fields();

    private transient DiscoveryBfdPortSpeakerWorkerService service;

    public BfdSpeakerWorker(Config config) {
        super(config);
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        BfdSpeakerWorkerCommand command = pullValue(
                input, BfdPortHandler.FIELD_ID_COMMAND, BfdSpeakerWorkerCommand.class);
        command.apply(service, new OutputAdapter(this, input));
    }

    @Override
    protected void onAsyncResponse(Tuple input) {
        // TODO
    }

    @Override
    public void onTimeout(String key) {
        // TODO
    }

    @Override
    protected void unhandledInput(Tuple input) {
        boolean suppressError = false;
        if (ComponentId.INPUT_SPEAKER.toString().equals(input.getSourceComponent())) {
            String key = input.getStringByField(MessageTranslator.KEY_FIELD);
            if (key == null) {
                // Speaker produce a lot of message by himself, that is not response for any command. We can
                // safely ignore all suck messages.
                suppressError = true;
            } else {
                @SuppressWarnings("uncheked")
                Message message = (Message) input.getValueByField(MessageTranslator.FIELD_ID_PAYLOAD);
                suppressError = shouldSuppressUnhandledInput(message);
            }
        }

        if (!suppressError) {
            super.unhandledInput(input);
        }
    }

    private static boolean shouldSuppressUnhandledInput(Message message) {
        return message instanceof HeartBeat;
    }

    @Override
    protected void init() {
        super.init();
        service = new DiscoveryBfdPortSpeakerWorkerService();
    }

    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);  // it will define HUB stream
        streamManager.declare(STREAM_FIELDS);
    }

    private class OutputAdapter extends AbstractOutputAdapter implements IBfdSpeakerWorkerCarrier {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }

        @Override
        public void speakerRequest(String key, CommandData payload) {
            emit(makeSpeakerTuple(key, payload));
        }

        private Values makeSpeakerTuple(String key, CommandData payload) {
            return new Values(key, payload, getContext());
        }
    }
}

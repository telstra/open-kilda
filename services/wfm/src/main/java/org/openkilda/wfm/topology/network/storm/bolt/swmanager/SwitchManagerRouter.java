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

package org.openkilda.wfm.topology.network.storm.bolt.swmanager;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.command.SwitchManagerSynchronizeErrorCommand;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.command.SwitchManagerSynchronizeResponseCommand;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.command.SwitchManagerWorkerCommand;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SwitchManagerRouter extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SWMANAGER_ROUTER.toString();

    public static final String FIELD_ID_KEY = MessageKafkaTranslator.FIELD_ID_KEY;
    public static final String FIELD_ID_PAYLOAD = MessageKafkaTranslator.FIELD_ID_PAYLOAD;

    public static final String FIELD_ID_COMMAND = "command";

    public static final String STREAM_WORKER_ID = "worker";
    public static final Fields STREAM_WORKER_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (ComponentId.INPUT_SWMANAGER.toString().equals(source)) {
            Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);
            switchManagerMessage(input, message);
        } else {
            unhandledInput(input);
        }
    }

    private void switchManagerMessage(Tuple input, Message message) {
        if (message instanceof InfoMessage) {
            processSwitchManagerResponse(input, ((InfoMessage) message).getData());
        } else if (message instanceof ErrorMessage) {
            processSwitchManagerErrorResponse(input, ((ErrorMessage) message).getData());
        } else {
            log.error("Unexpected message type \"{}\"", message.getClass());
        }
    }

    private void processSwitchManagerResponse(Tuple input, InfoData payload) {
        if (payload instanceof SwitchSyncResponse) {
            emit(STREAM_WORKER_ID, input, makeWorkerTuple(new SwitchManagerSynchronizeResponseCommand(
                    input.getStringByField(FIELD_ID_KEY), (SwitchSyncResponse) payload)));
        } else if (payload != null) {
            log.debug("Received message payload \"{}\"", payload.getClass());
        }
    }

    private void processSwitchManagerErrorResponse(Tuple input, ErrorData payload) {
        if (payload instanceof SwitchSyncErrorData) {
            emit(STREAM_WORKER_ID, input, makeWorkerTuple(new SwitchManagerSynchronizeErrorCommand(
                    input.getStringByField(FIELD_ID_KEY), (SwitchSyncErrorData) payload)));
        } else if (payload != null) {
            log.debug("Received message payload \"{}\"", payload.getClass());
        }
    }

    private Values makeWorkerTuple(SwitchManagerWorkerCommand command) {
        return new Values(command.getKey(), command, getCommandContext());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_WORKER_ID, STREAM_WORKER_FIELDS);
    }
}

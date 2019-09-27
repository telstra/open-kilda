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

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_KEY;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.storm.bolt.SwitchManagerEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.sw.SwitchHandler;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchSynchronizeErrorCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchSynchronizeResponseCommand;
import org.openkilda.wfm.topology.network.storm.bolt.sw.command.SwitchSynchronizeTimeoutCommand;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.command.SwitchManagerWorkerCommand;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class SwitchManagerWorker extends WorkerBolt {
    public static final String BOLT_ID = WorkerBolt.ID + ".swmanager";

    public static final String FIELD_ID_PAYLOAD = SwitchManagerEncoder.FIELD_ID_PAYLOAD;
    public static final String FIELD_ID_KEY = SwitchManagerEncoder.FIELD_ID_KEY;

    public static final String STREAM_HUB_ID = "hub";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    private final NetworkOptions options;

    public SwitchManagerWorker(Config config, NetworkOptions options) {
        super(config);
        this.options = options;
    }

    @Override
    protected void onHubRequest(Tuple input) throws PipelineException {
        handleCommand(input, SwitchHandler.FIELD_ID_COMMAND);
    }

    @Override
    protected void onAsyncResponse(Tuple request, Tuple response) throws Exception {
        handleCommand(response, SwitchManagerRouter.FIELD_ID_COMMAND);
    }

    @Override
    protected void onRequestTimeout(Tuple request) throws PipelineException {
        try {
            handleTimeout(request, SwitchHandler.FIELD_ID_COMMAND);
        } catch (PipelineException e) {
            log.error("Unable to unpack original tuple in timeout processing - {}", e.getMessage());
        }
    }

    @Override
    protected void unhandledInput(Tuple input) {
        if (log.isDebugEnabled()) {
            log.trace("Received a response from {} for non-pending task {}: {}", input.getSourceComponent(),
                    input.getStringByField(FIELD_ID_KEY), input.getValueByField(FIELD_ID_PAYLOAD));
        }
    }

    // -- commands processing --

    public void processSynchronizeSwitchRequest(String key, SwitchId switchId) {
        emitSwitchManagerRequest(key, makeSwitchSynchronizationRequest(switchId));
    }

    public void processSynchronizeSwitchRsponse(String key, SwitchSyncResponse payload) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, new SwitchSynchronizeResponseCommand(payload)));
    }

    public void processSynchronizeSwitchErrorRsponse(String key, SwitchSyncErrorData payload) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, new SwitchSynchronizeErrorCommand(payload)));
    }

    public void timeoutSwitchManagerRequest(String key, SwitchId switchId) {
        emitResponseToHub(getCurrentTuple(), makeHubTuple(key, new SwitchSynchronizeTimeoutCommand(switchId)));
    }

    // -- setup --

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);  // it will define HUB stream
        streamManager.declare(STREAM_FIELDS);
    }

    // -- private/service methods --

    private void handleCommand(Tuple input, String field) throws PipelineException {
        SwitchManagerWorkerCommand command = pullValue(input, field, SwitchManagerWorkerCommand.class);
        command.apply(this);
    }

    private void handleTimeout(Tuple input, String field) throws PipelineException {
        SwitchManagerWorkerCommand command = pullValue(input, field, SwitchManagerWorkerCommand.class);
        command.timeout(this);
    }

    public void emitSwitchManagerRequest(String key, CommandData payload) {
        emit(getCurrentTuple(), makeSwitchManagerTuple(key, payload));
    }

    private SwitchValidateRequest makeSwitchSynchronizationRequest(SwitchId switchId) {
        return SwitchValidateRequest.builder()
                .switchId(switchId)
                .performSync(true)
                .processMeters(true)
                .removeExcess(options.isRemoveExcessWhenSwitchSync())
                .build();
    }

    private Values makeSwitchManagerTuple(String key, CommandData payload) {
        return new Values(key, payload, getCommandContext());
    }

    private Values makeHubTuple(String key, SwitchCommand command) {
        return new Values(key, command, getCommandContext());
    }
}
